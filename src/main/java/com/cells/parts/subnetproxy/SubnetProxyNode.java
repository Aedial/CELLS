package com.cells.parts.subnetproxy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nonnull;

import net.minecraft.world.World;

import appeng.api.networking.events.MENetworkBootingStatusChange;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.util.AEPartLocation;

import com.cells.network.sync.ResourceType;


/**
 * AE2 cache owner for Subnet Proxy participation. It collects weak local provider locations,
 * binds channel monitors, and registers the grid's enumeration adapters.
 * LAE2 lifecycle events invalidate the cached local stocks and schedule a new baseline acquisition.
 * A grid reset is required after initialization to ensure the enumeration handler is properly registered.
 */
public final class SubnetProxyNode implements ISubnetProxyNode, ICellProvider {

    /** Weak reference to the grid this node belongs to */
    private final WeakReference<IGrid> grid;

    /** Loaded front and back nodes on this grid, including temporarily inactive parts */
    private final Set<IGridNode> parts = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Captured snapshots for source-side and destination-side nodes, revoked on removal or rebinding */
    private final Map<IGridNode, List<SubnetProxySnapshot<?>>> snapshots = new IdentityHashMap<>();

    /** Current view (local baseline + imported contributions) for each resource type */
    private final Map<ResourceType, SubnetProxyView<?>> views = new EnumMap<>(ResourceType.class);

    /** Cached weak locations of local providers */
    private List<SubnetProxyProvider> providers = Collections.emptyList();

    /** Weak provider-node lookup, replaced when rebuilding the provider locations */
    private Map<IGridNode, SubnetProxyProvider> providersByNode = Collections.emptyMap();

    /** Whether the node has any active proxy left */
    private boolean registered;

    /** Whether the local providers cache needs to be rebuilt */
    private boolean providersDirty = true;

    /** Guard to prevent reentrant initialization */
    private boolean initializing;

    /** Whether a grid reset is required (to include our enumeration handler) */
    private boolean shouldResetGrid;

    /** Participation generation used to reject callbacks from detached bindings */
    private long generation;

    /** Revision changed by source and AE2 lifecycle events */
    private long membershipRevision;

    /** Revision changed by pending lifecycle and Front work on this grid */
    private long inputRevision;

    /** Bounded observations retained beside this grid's work */
    private final SubnetProxyDiagnostics diagnostics = new SubnetProxyDiagnostics();

    /** Defensive guard for stale processes that may try to access unloaded worlds */
    private final Set<World> retiredWorlds = Collections.newSetFromMap(new WeakHashMap<>());

    /** Weak world reference for the current node */
    private WeakReference<World> participationWorld = new WeakReference<>(null);

    /** Views awaiting reset after a grid reset request */
    private final Set<SubnetProxyView<?>> requestedResets = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Reset event reference used to distinguish the reset source (and whether it was requested) */
    private Object requestedEvent;

    public SubnetProxyNode(IGrid grid) {
        this.grid = new WeakReference<>(grid);
    }

    public IGrid getGrid() {
        return this.grid.get();
    }

    public SubnetProxyDiagnostics diagnostics() {
        return this.diagnostics;
    }

    public long getGeneration() {
        return this.generation;
    }

    public long getMembershipRevision() {
        return this.membershipRevision;
    }

    public long getInputRevision() {
        return this.inputRevision;
    }

    public void inputChanged() {
        this.inputRevision++;
    }

    public boolean isRegistered() {
        return this.registered;
    }

    public List<SubnetProxyView<?>> views() {
        return new ArrayList<>(this.views.values());
    }

    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> SubnetProxyView<T> view(IStorageChannel<T> channel) {
        for (SubnetProxyView<?> view : this.views.values()) {
            if (view.channel().getChannel() == channel) return (SubnetProxyView<T>) view;
        }

        return null;
    }

    public void register() {
        if (this.initializing) return;

        this.initializing = true;
        try {
            if (!this.registered && !this.parts.isEmpty()) {
                this.generation++;

                try {
                    for (SubnetProxyChannel<?> channel : SubnetProxyManager.channels()) {
                        this.bind(channel);
                    }
                } catch (RuntimeException | Error failure) {
                    for (SubnetProxyView<?> view : this.views.values()) view.detach();
                    this.views.clear();

                    throw failure;
                }

                this.registered = true;
                this.shouldResetGrid = true;
                this.storage().registerCellProvider(this);
            }

            if (this.registered && this.providersDirty) this.collectProviders();
        } finally {
            this.initializing = false;
        }
    }

    /**
     * Bind a channel to this node, creating a corresponding view.
     */
    private <T extends IAEStack<T>> void bind(SubnetProxyChannel<T> channel) {
        IMEMonitor<T> monitor = this.storage().getInventory(channel.getChannel());
        SubnetProxyView<T> view = new SubnetProxyView<>(this, channel, monitor);
        this.views.put(channel.getType(), view);
        view.bind();
    }

    /**
     * Collect the local providers (drives, storage buses) for this grid.
     */
    private void collectProviders() {
        this.diagnostics.sourcePass();

        long revision = this.membershipRevision;
        long generation = this.generation;
        List<SubnetProxyProvider> candidate = new ArrayList<>();
        Map<IGridNode, SubnetProxyProvider> byNode = new WeakHashMap<>();
        Set<IGridHost> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        IGrid grid = this.getGrid();

        List<Class<? extends IGridHost>> types = new ArrayList<>();
        for (Class<? extends IGridHost> type : grid.getMachinesClasses()) types.add(type);

        for (Class<? extends IGridHost> type : types) {
            if (!ICellProvider.class.isAssignableFrom(type)) continue;

            List<IGridNode> nodes = new ArrayList<>();
            for (IGridNode node : grid.getMachines(type)) nodes.add(node);

            for (IGridNode node : nodes) {
                IGridHost host = node.getMachine();
                if (host instanceof PartSubnetProxyFront || host instanceof PartSubnetProxyBack) continue;

                if (!visited.add(host)) continue;

                SubnetProxyProvider source = new SubnetProxyProvider(node);
                source.captureChannels(SubnetProxyManager.channels());
                candidate.add(source);
                byNode.put(node, source);
            }
        }

        if (this.registered && generation == this.generation && revision == this.membershipRevision) {
            this.providers = candidate;
            this.providersByNode = byNode;
            this.providersDirty = false;
        }
    }

    public <T extends IAEStack<T>> List<SubnetProxyProvider.Handler<T>> resolve(SubnetProxyChannel<T> channel) {
        List<SubnetProxyProvider.Handler<T>> handlers = new ArrayList<>();
        Set<SubnetProxyProvider.Handler<T>> visited = new HashSet<>();

        for (SubnetProxyProvider provider : this.providers) {
            for (SubnetProxyProvider.Handler<T> handler : provider.handlers(this, channel)) {
                if (handler.inventory instanceof SubnetProxyAdapter
                    || handler.inventory instanceof SubnetProxyEnumeration) continue;

                if (visited.add(handler)) handlers.add(handler);
            }
        }

        return handlers;
    }

    public boolean isLocal(IGridHost host, SubnetProxyChannel<?> channel) {
        SubnetProxyProvider provider = this.providersByNode.get(host.getGridNode(AEPartLocation.INTERNAL));
        return provider != null && provider.resolve(this) == host && !channel.isPassthrough(host);
    }

    public void requestGridReset() {
        this.shouldResetGrid = true;
        SubnetProxyManager.makeDirty(this);
    }

    public void resetGridIfNeeded() {
        if (!this.registered || !this.shouldResetGrid) return;

        this.shouldResetGrid = false;
        this.requestedResets.addAll(this.views.values());

        MENetworkCellArrayUpdate event = new MENetworkCellArrayUpdate();
        this.requestedEvent = event;
        try {
            this.getGrid().postEvent(event);
        } finally {
            this.requestedEvent = null;
        }
    }

    public boolean awaitingReset(SubnetProxyView<?> view) {
        return this.requestedResets.contains(view);
    }

    public void completedReset(SubnetProxyView<?> view) {
        if (this.requestedResets.remove(view)) {
            view.invalidateKeepingDeadline("requested grid reset");
        } else {
            view.invalidate("grid reset");
        }
    }

    public boolean hasPassthrough(SubnetProxyChannel<?> channel) {
        for (SubnetProxyProvider source : this.providers) {
            IGridHost host = source.resolve(this);
            if (host != null && source.capturedPassthrough(channel)) return true;
        }

        return false;
    }

    public IStorageGrid storage() {
        IGrid grid = this.getGrid();
        return grid == null ? null : grid.getCache(IStorageGrid.class);
    }

    /**
     * Invalidates this cached providers and views, so they will be refreshed when possible.
     * Called when the node's state has changed and we cannot reliably correct the cached information.
     */
    public void invalidate(String reason) {
        this.diagnostics.signal(reason);
        this.membershipRevision++;
        this.providersDirty = true;

        for (SubnetProxyView<?> view : this.views.values()) view.invalidate(reason);

        SubnetProxyManager.makeDirty(this);
    }

    public void captureParts() {
        for (IGridNode node : new ArrayList<>(this.parts)) {
            IGridHost host = node.getMachine();
            if (host instanceof PartSubnetProxyFront) SubnetProxyManager.makeDirty((PartSubnetProxyFront) host);

            if (host instanceof PartSubnetProxyBack) {
                PartSubnetProxyFront front = ((PartSubnetProxyBack) host).findFrontPart();
                if (front != null) SubnetProxyManager.makeDirty(front);
            }
        }
    }

    public void replaceSnapshots(IGridNode endpoint, List<SubnetProxySnapshot<?>> captured) {
        List<SubnetProxySnapshot<?>> previous = this.snapshots.put(endpoint, captured);
        if (previous != null) SubnetProxyManager.topology().invalidate(previous);
    }

    private void invalidateSnapshots(IGridNode endpoint) {
        List<SubnetProxySnapshot<?>> snapshots = this.snapshots.remove(endpoint);
        if (snapshots != null) SubnetProxyManager.topology().invalidate(snapshots);
    }

    private void invalidateSnapshots() {
        for (List<SubnetProxySnapshot<?>> snapshots : this.snapshots.values()) {
            SubnetProxyManager.topology().invalidate(snapshots);
        }

        this.snapshots.clear();
    }

    public boolean hasParts() {
        return !this.parts.isEmpty();
    }

    public boolean belongsTo(World world) {
        for (IGridNode part : this.parts) {
            if (part.getWorld() == world) return true;
        }

        return this.participationWorld.get() == world;
    }

    public boolean acceptsWorld(World world) {
        return !this.retiredWorlds.contains(world);
    }

    public boolean retireWorld(World world) {
        this.retiredWorlds.add(world);
        for (IGridNode part : this.parts) {
            if (part.getWorld() == world) this.invalidateSnapshots(part);
        }

        this.parts.removeIf(part -> part.getWorld() == world);

        if (this.parts.isEmpty()) {
            this.dispose();

            return true;
        }

        this.invalidate("world unloaded");

        return false;
    }

    public void dispose() {
        IStorageGrid storage = this.storage();
        this.invalidateSnapshots();
        this.registered = false;
        this.shouldResetGrid = false;
        this.generation++;

        for (SubnetProxyView<?> view : this.views.values()) view.detach();
        this.views.clear();
        this.providers = Collections.emptyList();
        this.providersByNode = Collections.emptyMap();
        this.parts.clear();
        this.participationWorld.clear();
        this.requestedResets.clear();

        if (storage != null) storage.unregisterCellProvider(this);
    }

    public void retire() {
        if (!this.registered) return;

        try {
            for (SubnetProxyView<?> view : this.views.values()) view.withdraw();
        } finally {
            this.dispose();
        }
    }

    @Override
    public void addNode(@Nonnull IGridNode node, @Nonnull IGridHost host) {
        if (host instanceof PartSubnetProxyFront || host instanceof PartSubnetProxyBack) {
            this.parts.add(node);
            this.participationWorld = new WeakReference<>(node.getWorld());
        }

        // TODO: Don't need a full invalidation when adding a node
        if (this.registered || !this.parts.isEmpty()) this.invalidate("node added");
    }

    @Override
    public void removeNode(@Nonnull IGridNode node, @Nonnull IGridHost host) {
        this.invalidateSnapshots(node);
        this.parts.remove(node);

        if (host instanceof PartSubnetProxyFront) SubnetProxyManager.makeDirty((PartSubnetProxyFront) host);

        // TODO: Same here
        if (this.registered) this.invalidate("node removed");
    }

    @MENetworkEventSubscribe
    public void cellsChanged(MENetworkCellArrayUpdate event) {
        if (!this.registered) return;

        // Nondescript change in the grid, invalidate caches to catch splits/merges
        if (event != this.requestedEvent) {
            this.invalidate("cell array");

            return;
        }

        // Process our own reset request
        this.membershipRevision++;
        this.providersDirty = true;
        for (SubnetProxyView<?> view : this.views.values()) {
            view.invalidateKeepingDeadline("providers changed");
        }

        SubnetProxyManager.makeDirty(this);
    }

    // TODO: Skip invalidation if the change doesn't affect this node in a meaningful way

    @MENetworkEventSubscribe
    public void bootingChanged(MENetworkBootingStatusChange event) {
        this.invalidateSnapshots();
        if (this.registered) this.invalidate("pathing");
    }

    @MENetworkEventSubscribe
    public void channelsChanged(MENetworkChannelsChanged event) {
        this.invalidateSnapshots();
        if (this.registered) this.invalidate("channels");
    }

    @MENetworkEventSubscribe
    public void powerChanged(MENetworkPowerStatusChange event) {
        this.invalidateSnapshots();
        if (this.registered) this.invalidate("power");
    }

    @SuppressWarnings("rawtypes")
    @Override
    public List<IMEInventoryHandler> getCellArray(IStorageChannel<?> channel) {
        for (SubnetProxyView<?> view : this.views.values()) {
            if (view.channel().getChannel() == channel) return Collections.singletonList(view.enumeration());
        }

        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void onUpdateTick() {
    }

    @Override
    public void onSplit(@Nonnull IGridStorage storage) {
        this.invalidateSnapshots();
        if (this.registered) this.invalidate("split");
    }

    @Override
    public void onJoin(@Nonnull IGridStorage storage) {
        this.invalidateSnapshots();
        if (this.registered) this.invalidate("join");
    }

    @Override
    public void populateGridStorage(@Nonnull IGridStorage storage) {
    }
}
