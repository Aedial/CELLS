package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;

import com.cells.config.CellsConfig;


/**
 * Quantity accounting for a given resource channel on the grid. It keeps isolated local stock separate from
 * the local AE2 cache (local + imported from other proxies) and handles the lifecycle of the AE2 cache
 * (importing of remote contributions and exporting of local contributions).
 */
public final class SubnetProxyView<T extends IAEStack<T>> implements IMEMonitorHandlerReceiver<T> {

    /** Grid owner for this view */
    private final SubnetProxyNode node;

    /** Captured channel implementation used by this listener and its adapters */
    private final SubnetProxyChannel<T> channel;

    /** AE2 memory monitor for this resource channel */
    private final IMEMonitor<T> monitor;

    /** Generation of the grid owner for listener invalidation when the view changes */
    private final long generation;

    /** Enumeration handler for this view, responsible for exposing remote stock to the local grid */
    private final SubnetProxyEnumeration<T> enumeration;

    /** Remote contributions from other subnet proxy nodes */
    private final Map<SubnetProxyNode, Contribution<T>> contributions = new IdentityHashMap<>();

    /** Local baseline + stable local deltas for this type of resource */
    private IItemList<T> local;

    /** Deduplicated imported stocks from reachable origins via connected proxies */
    private IItemList<T> imported;

    /** Whether ordinary local and imported deltas can update the accepted listing */
    private boolean stable;

    /** Whether this channel has applicable incoming or outgoing read paths */
    private boolean tracking;

    /** Whether this channel has any outgoing path to forward deltas to */
    private boolean forwarding;

    /** Whether the view should expose imported stocks (disabled during baseline to avoid double counting) */
    private boolean importedStocksEnabled;

    /** Guard to invalidate a live candidate when callbacks arrive during enumeration */
    private boolean acquiring;

    /** Revision of quantity accounting (debug purposes) */
    private long localRevision;

    /** Reset revision, for stale imported stock invalidation */
    private long resetRevision;

    /** Revision used to reject baseline aquisition if the state somehow changes during the process */
    private long invalidationRevision;

    /** Earliest tick for rebuilding the view after reset */
    private long deadline;

    /** Current state or recovery reason */
    private String reason = "initialization";

    public SubnetProxyView(SubnetProxyNode node, SubnetProxyChannel<T> channel, IMEMonitor<T> monitor) {
        this.node = node;
        this.channel = channel;
        this.monitor = monitor;
        this.generation = node.getGeneration();
        this.enumeration = new SubnetProxyEnumeration<>(this);
        this.local = channel.getChannel().createList();
        this.imported = channel.getChannel().createList();
    }

    public SubnetProxyNode node() {
        return this.node;
    }

    public SubnetProxyChannel<T> channel() {
        return this.channel;
    }

    public IMEMonitor<T> monitor() {
        return this.monitor;
    }

    public SubnetProxyEnumeration<T> enumeration() {
        return this.enumeration;
    }

    public void bind() {
        this.monitor.addListener(this, this);
    }

    public void detach() {
        SubnetProxyManager.retireListener(this);
        SubnetProxyManager.unschedule(this);
        this.stable = false;
        this.tracking = false;
        this.forwarding = false;
        this.importedStocksEnabled = false;
        this.contributions.clear();
        this.local = this.channel.getChannel().createList();
        this.imported = this.channel.getChannel().createList();
    }

    @Override
    public boolean isValid(Object self) {
        return this.node.isRegistered() && this.node.getGeneration() == this.generation;
    }

    public void unsubscribe() {
        this.monitor.removeListener(this);
    }

    public boolean isStable() {
        return this.stable && this.node.isRegistered() && this.isValid(this);
    }

    public boolean isTracking() {
        return this.tracking;
    }

    public long revision() {
        return this.invalidationRevision;
    }

    public long deadline() {
        return this.deadline;
    }

    public String reason() {
        return this.reason;
    }

    public long localRevision() {
        return this.localRevision;
    }

    /**
     * Current reset revision of the view, incremented on each reset.
     * Tells which imported stocks have been imported under this reset revision.
     */
    public long resetRevision() {
        return this.resetRevision;
    }

    public void retryAfterFailure() {
        this.invalidate("baseline failure");
        this.deadline = SubnetProxyManager.tick()
            + Math.max(1, CellsConfig.general.subnetProxyMaxTickRate);
    }

    public void setWork(boolean tracking, boolean forwarding) {
        boolean starting = tracking && !this.tracking;
        this.tracking = tracking;
        this.forwarding = forwarding;
        if (starting) this.invalidate("channel enabled");

        if (!tracking) {
            this.stable = false;
            SubnetProxyManager.unschedule(this);
        }
    }

    public void invalidate(String reason) {
        this.invalidateKeepingDeadline(reason);
        this.deadline = SubnetProxyManager.tick() + SubnetProxyManager.settlingTicks();
    }

    public void invalidateKeepingDeadline(String reason) {
        this.stable = false;
        this.reason = reason;
        this.invalidationRevision++;
        if (this.tracking) SubnetProxyManager.schedule(this);
    }

    @Override
    public void onListUpdate() {
        if (!this.node.isRegistered() || !this.isValid(this)) return;

        this.resetRevision++;
        if (!this.importedStocksEnabled) {
            this.contributions.clear();
            this.imported = this.channel.getChannel().createList();
        }

        this.node.completedReset(this);
    }

    @Override
    public void postChange(IBaseMonitor<T> monitor, Iterable<T> changes, IActionSource source) {
        if (!this.node.isRegistered() || !this.isValid(this)) return;

        SubnetProxyManager.Delivery<?> delivery = SubnetProxyManager.delivery(source);
        if (delivery != null && delivery.accept(this, monitor, changes)) return;

        if (this.acquiring) this.invalidationRevision++;

        if (!this.forwarding && !(this.tracking && this.stable)) return;

        SubnetProxyNode origin = SubnetProxyOperation.origin(this, source);
        if (origin != this.node) return;

        if (!this.forwarding) {
            boolean changed = false;
            for (T change : changes) {
                if (change == null || change.getStackSize() == 0) continue;

                this.local.addStorage(quantity(change, change.getStackSize()));
                changed = true;
            }

            if (changed) this.localRevision++;

            return;
        }

        IItemList<T> retained = copy(this.channel, changes);
        if (retained.isEmpty()) return;

        if (this.stable) {
            for (T change : retained) this.local.addStorage(change);

            this.localRevision++;
        }

        SubnetProxyManager.dispatch(this, retained, source);
    }

    public IItemList<T> localSnapshot() {
        return copy(this.channel, this.local);
    }

    public IItemList<T> importSnapshot() {
        return copy(this.channel, this.imported);
    }

    public IItemList<T> contributionSnapshot(SubnetProxyNode origin) {
        Contribution<T> contribution = this.contributions.get(origin);
        return contribution == null ? this.channel.getChannel().createList() : copy(this.channel, contribution.amounts);
    }

    public List<SubnetProxyNode> accountedOrigins() {
        return new ArrayList<>(this.contributions.keySet());
    }

    /**
     * Remove a specific origin and its contributions from this view.
     * Happens when the path to the origin is no longer valid or the origin is removed.
     */
    public void forget(SubnetProxyNode origin) {
        Contribution<T> removed = this.contributions.remove(origin);
        if (removed != null && !removed.amounts.isEmpty()) this.updateEnumeration();
    }

    /**
     * Whether the view has received contributions from the specified origin
     * under the current reset revision and generation.
     */
    public boolean received(SubnetProxyNode origin) {
        Contribution<T> contribution = this.contributions.get(origin);
        return contribution != null && contribution.generation == origin.getGeneration()
            && contribution.receivedResetRevision == this.resetRevision;
    }

    /**
     * Establish the baseline of local stocks and take ownership of the supplied list.
     */
    public void install(IItemList<T> baseline) {
        this.node.diagnostics().baseline();
        this.local = baseline;
        this.localRevision++;
        this.stable = true;
    }

    public void beginAcquisition() {
        this.acquiring = true;
    }

    public void endAcquisition() {
        this.acquiring = false;
    }

    public void rejectCandidate() {
        this.stable = false;
        this.invalidationRevision++;
    }

    public void appendImports(IItemList<T> out) {
        if (!this.importedStocksEnabled) return;

        for (T stack : this.imported) {
            if (stack.getStackSize() > 0) out.addStorage(stack.copy());
        }
    }

    /**
     * Enable the enumeration of imported stocks for this view.
     * Called after the baseline of local stocks has been established.
     */
    public void enableEnumeration() {
        this.importedStocksEnabled = true;
        this.updateEnumeration();
    }

    /**
     * Update the enumeration of imported stocks based on the current contributions.
     */
    private void updateEnumeration() {
        IItemList<T> next = this.channel.getChannel().createList();

        for (Contribution<T> contribution : this.contributions.values()) {
            for (T stack : contribution.amounts) next.addStorage(stack.copy());
        }

        this.imported = next;
    }

    /**
     * Withdraw and disable all remote contributions.
     */
    public void withdraw() {
        this.importedStocksEnabled = false;
        for (SubnetProxyNode origin : new ArrayList<>(this.contributions.keySet())) {
            this.replace(origin, this.channel.getChannel().createList());
        }

        this.imported = this.channel.getChannel().createList();
    }

    public void suspend() {
        this.withdraw();
        this.contributions.clear();
        this.local = this.channel.getChannel().createList();
    }

    /**
     * Replace the contribution from the specified origin with the desired item list,
     * publishing the difference between the previous and new contributions.
     */
    public void replace(SubnetProxyNode origin, IItemList<T> desired) {
        this.replace(origin, desired, null);
    }

    /**
     * Compute the filtered contribution difference from the source's current local quantities.
     * Complete the owned difference before publication can trigger callbacks.
     */
    public void replace(SubnetProxyView<T> source, Predicate<T> filter) {
        this.replace(source.node, source.local, stack -> stack.getStackSize() > 0 && filter.test(stack));
    }

    private void replace(SubnetProxyNode origin, IItemList<T> desired, Predicate<T> filter) {
        Contribution<T> contribution = this.contributions.computeIfAbsent(origin,
            key -> new Contribution<>(this.channel, origin.getGeneration()));
        IItemList<T> difference = this.channel.getChannel().createList();

        for (T stack : desired) {
            if (stack == null || stack.getStackSize() == 0) continue;

            T previous = contribution.amounts.findPrecise(stack);
            if (previous != null && previous.getStackSize() != 0) continue;
            if (filter != null && !filter.test(stack)) continue;

            difference.addStorage(quantity(stack, stack.getStackSize()));
        }

        for (T old : contribution.amounts) {
            if (old.getStackSize() == 0) continue;

            T next = desired.findPrecise(old);
            long amount = 0;
            if (next != null && next.getStackSize() != 0 && (filter == null || filter.test(next))) {
                amount = next.getStackSize();
            }

            amount -= old.getStackSize();
            if (amount != 0) difference.addStorage(quantity(old, amount));
        }

        SubnetProxyManager.publish(() -> {
            for (T delta : difference) this.publish(origin, delta, null);

            contribution.generation = origin.getGeneration();
            contribution.receivedResetRevision = this.resetRevision;
        });
    }

    /**
     * Publish an owned remote delta from the specified origin.
     */
    public void add(SubnetProxyNode origin, T delta, IActionSource source) {
        this.publish(origin, delta, source);
    }

    /**
     * Publish a remote delta from the specified origin on the current grid.
     */
    private void publish(SubnetProxyNode origin, T delta, IActionSource source) {
        if (delta.getStackSize() == 0) return;

        SubnetProxyManager.Delivery<T> delivery = SubnetProxyManager.beginDelivery(this, origin, delta, source);
        try {
            this.node.storage().postAlterationOfStoredItems(this.channel.getChannel(),
                Collections.singletonList(delta.copy()), delivery.source);

            if (!delivery.completed()) throw new IllegalStateException("Delta changes not received/accepted");
        } catch (RuntimeException error) {
            this.invalidate("publication failure");
            this.node.requestGridReset();

            throw error;
        } finally {
            delivery.close();
        }
    }

    /**
     * Account for a remote delta from the specified origin after publication.
     */
    public void account(SubnetProxyNode origin, T delta) {
        Contribution<T> contribution = this.contributions.computeIfAbsent(origin,
            key -> new Contribution<>(this.channel, origin.getGeneration()));
        T change = quantity(delta, delta.getStackSize());
        contribution.amounts.addStorage(change);

        if (this.importedStocksEnabled) this.imported.addStorage(change);
    }

    /**
     * Create a standard stack with the specified quantity.
     */
    public static <T extends IAEStack<T>> T quantity(T stack, long amount) {
        T result = stack.copy();
        result.reset();
        result.setStackSize(amount);
        return result;
    }

    /**
     * Filter out invalid changes from the input list into a new list.
     */
    public static <T extends IAEStack<T>> IItemList<T> copy(SubnetProxyChannel<T> channel, Iterable<T> input) {
        IItemList<T> result = channel.getChannel().createList();

        for (T stack : input) {
            if (stack == null || stack.getStackSize() == 0) continue;

            result.addStorage(quantity(stack, stack.getStackSize()));
        }

        return result;
    }

    /**
     * Track the remote contributions from a specific origin.
     */
    private static final class Contribution<T extends IAEStack<T>> {

        /** Imported remote quantities for a specific grid */
        private final IItemList<T> amounts;

        /** Node generation at the time of reception */
        private long generation;

        /** Grid reset revision at the time of reception */
        private long receivedResetRevision = -1;

        private Contribution(SubnetProxyChannel<T> channel, long generation) {
            this.amounts = channel.getChannel().createList();
            this.generation = generation;
        }
    }
}
