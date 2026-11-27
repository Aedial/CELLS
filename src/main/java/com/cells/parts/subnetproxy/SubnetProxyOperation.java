package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;


/**
 * Extraction/Insertion/Enumeration handler for subnet proxy operations.
 * Used by the Adapters to interact with AE2, respecting AE2 permissions and security.
 */
public final class SubnetProxyOperation {

    private static final int PREFERRED_PASS = 1;
    private static final int FALLBACK_PASS = 2;

    /** Visited providers tracking for simulation requests (for deduplication in the same request) */
    private static final Map<Object, Simulation> REQUESTS = new IdentityHashMap<>();

    /** Scope for the current operation, containing context about the caller independently of source */
    private static Scope<?> activeScope;

    /** Insertion depth for nested insertion calls (ensures we only insert at the top level) */
    private static int insertionDepth;

    private SubnetProxyOperation() {
    }

    public static void expire() {
        REQUESTS.clear();
    }

    public static void clear() {
        REQUESTS.clear();
        activeScope = null;
        insertionDepth = 0;
    }

    public static <T extends IAEStack<T>> long cachedAvailable(SubnetProxySnapshot<T> snapshot, T request) {
        if (!snapshot.available() || !snapshot.filter.test(request)) return 0;

        Set<SubnetProxyNode> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(snapshot.back);
        long available = localAmount(snapshot.back, snapshot.channel, request);

        for (SubnetProxySnapshot<T> peer : SubnetProxyManager.topology()
                .getPeerSnapshots(snapshot.back, snapshot.channel.getChannel())) {
            if (peer.back == snapshot.front || !peer.filter.test(request) || !visited.add(peer.back)) {
                continue;
            }

            available += localAmount(peer.back, snapshot.channel, request);
        }

        return available;
    }

    private static <T extends IAEStack<T>> long localAmount(SubnetProxyNode node, SubnetProxyChannel<T> channel, T request) {
        SubnetProxyView<T> view = node.view(channel.getChannel());
        if (view == null || !view.isStable()) return 0;

        T stack = view.localSnapshot().findPrecise(request);
        return stack == null ? 0 : Math.max(0, stack.getStackSize());
    }

    public static <T extends IAEStack<T>> T extract(SubnetProxySnapshot<T> snapshot,
            T request, Actionable mode, IActionSource source) {

        if (request == null || request.getStackSize() <= 0 || snapshot == null) return null;

        Simulation simulation = mode == Actionable.SIMULATE
            ? REQUESTS.computeIfAbsent(request, key -> new Simulation()) : null;

        List<Object> copies = new ArrayList<>();
        try {
            return extractGrid(snapshot, request, mode, source, simulation, snapshot, true, copies);
        } catch (RuntimeException | Error failure) {
            for (Object copy : copies) REQUESTS.remove(copy, simulation);

            throw failure;
        }
    }

    private static <T extends IAEStack<T>> T extractGrid(SubnetProxySnapshot<T> snapshot,
            T request, Actionable mode, IActionSource source, Simulation simulation,
            SubnetProxySnapshot<T> entrySnapshot, boolean peers, List<Object> copies) {

        if (snapshot.back == entrySnapshot.front || !snapshot.available() || !entrySnapshot.available()
            || !snapshot.filter.test(request)) return null;
        if (!permitted(snapshot.back.getGrid(), source, SecurityPermissions.EXTRACT)) return null;

        List<Attempt<T>> attempts = new ArrayList<>();
        for (SubnetProxyProvider.Handler<T> handler : snapshot.back.resolve(snapshot.channel)) {
            attempts.add(new Attempt<>(handler, null));
        }

        if (peers) {
            for (SubnetProxySnapshot<T> peer : SubnetProxyManager.topology()
                    .getPeerSnapshots(snapshot.back, snapshot.channel.getChannel())) {
                if (peer.back != entrySnapshot.front && peer.filter.test(request)) {
                    attempts.add(new Attempt<>(null, peer));
                }
            }
        }

        attempts.sort(Comparator.<Attempt<T>, Boolean>comparing(Attempt::sticky)
            .thenComparingInt(Attempt::priority));
        T output = SubnetProxyView.quantity(request, 0);

        for (Attempt<T> attempt : attempts) {
            if (!snapshot.available() || !entrySnapshot.available()) break;

            long remaining = request.getStackSize() - output.getStackSize();
            if (remaining <= 0) break;

            T sub = SubnetProxyView.quantity(request, remaining);
            if (simulation != null) {
                REQUESTS.put(sub, simulation);
                copies.add(sub);
            }

            T extracted;
            if (attempt.peer != null) {
                extracted = extractGrid(attempt.peer, sub, mode, source, simulation, entrySnapshot, false, copies);
            } else {
                extracted = extractHandler(snapshot, entrySnapshot, attempt.handler, sub,
                    mode, source, simulation, copies);
            }

            if (extracted != null && extracted.getStackSize() > 0) output.incStackSize(extracted.getStackSize());
        }

        return output.getStackSize() > 0 ? output : null;
    }

    private static <T extends IAEStack<T>> T extractHandler(SubnetProxySnapshot<T> snapshot,
            SubnetProxySnapshot<T> entrySnapshot, SubnetProxyProvider.Handler<T> handler, T request,
            Actionable mode, IActionSource source, Simulation simulation, List<Object> copies) {

        SubnetProxyChannel<T> channel = snapshot.channel;
        handler = liveHandler(snapshot.back, channel, handler);
        if (handler == null || !snapshot.available() || !entrySnapshot.available()) return null;

        long counted = simulation == null ? 0 : simulation.counted(handler, channel, request);
        long amount = request.getStackSize();
        amount = counted > Long.MAX_VALUE - amount ? Long.MAX_VALUE : amount + counted;
        T liveRequest = SubnetProxyView.quantity(request, amount);

        if (simulation != null) {
            REQUESTS.put(liveRequest, simulation);
            copies.add(liveRequest);
        }

        Scope<?> enclosing = activeScope;
        activeScope = new Scope<>(snapshot.back, channel, handler, source);
        try {
            T extracted = handler.inventory.extractItems(liveRequest, mode, source);
            if (extracted == null) return null;

            long result = Math.min(amount, Math.max(0, extracted.getStackSize() - counted));
            if (result == 0) return null;

            if (simulation != null) simulation.add(handler, channel, request, result);

            return SubnetProxyView.quantity(extracted, result);
        } finally {
            activeScope = enclosing;
        }
    }

    public static <T extends IAEStack<T>> T insert(SubnetProxySnapshot<T> snapshot, T input,
            Actionable mode, IActionSource source) {

        if (input == null || input.getStackSize() <= 0 || snapshot == null || !snapshot.insertion
            || !snapshot.available() || !snapshot.filter.test(input) || insertionDepth != 0) return input;

        if (!permitted(snapshot.back.getGrid(), source, SecurityPermissions.INJECT)) return input;

        insertionDepth++;
        try {
            // TODO: Direct local-handler selection bypasses crafting-input interception
            //       A standard subnet connection can feed back a waiting CPU,
            //       but we bypass it since it is not a local source.
            //       Consider adding support for that.

            List<InsertionAttempt<T>> attempts = new ArrayList<>();
            for (SubnetProxyProvider.Handler<T> handler : snapshot.back.resolve(snapshot.channel)) {
                attempts.add(new InsertionAttempt<>(handler, PREFERRED_PASS));
                if (!handler.inventory.isSticky()) attempts.add(new InsertionAttempt<>(handler, FALLBACK_PASS));
            }
            attempts.sort(Comparator.<InsertionAttempt<T>, Boolean>comparing(
                attempt -> attempt.handler.inventory.isSticky()).reversed()
                .thenComparing(Comparator.comparingInt(
                    (InsertionAttempt<T> attempt) -> attempt.handler.inventory.getPriority()).reversed())
                .thenComparingInt(attempt -> attempt.pass));

            T remaining = input.copy();
            boolean stickyFound = false;

            for (InsertionAttempt<T> attempt : attempts) {
                if (remaining == null) break;
                if (!snapshot.available()) return remaining;

                SubnetProxyProvider.Handler<T> handler = attempt.handler;
                IMEInventoryHandler<T> inventory = handler.inventory;

                // multiple sticky inventories are allowed to accept the same stock without returning early
                boolean sticky = inventory.isSticky();
                if (stickyFound && !sticky) return remaining;

                if (attempt.pass == PREFERRED_PASS) {
                    if (!acceptsFirst(snapshot, handler, remaining, source)) continue;
                } else {
                    if (sticky) continue;

                    handler = liveHandler(snapshot.back, snapshot.channel, handler);
                    if (handler == null) continue;

                    inventory = handler.inventory;

                    if (!inventory.validForPass(FALLBACK_PASS) || !inventory.canAccept(remaining)
                        || inventory.isPrioritized(remaining)) continue;
                }

                remaining = injectHandler(snapshot, handler, remaining, mode, source);
                if (sticky) stickyFound = true;
            }

            return remaining;
        } finally {
            insertionDepth--;
        }
    }

    private static <T extends IAEStack<T>> boolean acceptsFirst(SubnetProxySnapshot<T> snapshot,
            SubnetProxyProvider.Handler<T> handler, T input, IActionSource source) {

        handler = liveHandler(snapshot.back, snapshot.channel, handler);
        if (handler == null || !snapshot.available()) return false;

        Scope<?> enclosing = activeScope;
        activeScope = new Scope<>(snapshot.back, snapshot.channel, handler, source);
        try {
            IMEInventoryHandler<T> inventory = handler.inventory;
            return inventory.validForPass(PREFERRED_PASS) && inventory.canAccept(input)
                && (inventory.isPrioritized(input) || inventory.extractItems(input, Actionable.SIMULATE, source) != null);
        } finally {
            activeScope = enclosing;
        }
    }

    private static <T extends IAEStack<T>> T injectHandler(SubnetProxySnapshot<T> snapshot,
            SubnetProxyProvider.Handler<T> handler, T input, Actionable mode, IActionSource source) {

        handler = liveHandler(snapshot.back, snapshot.channel, handler);
        if (handler == null || !snapshot.available()) return input;

        Scope<?> enclosing = activeScope;
        activeScope = new Scope<>(snapshot.back, snapshot.channel, handler, source);
        try {
            return handler.inventory.injectItems(input, mode, source);
        } finally {
            activeScope = enclosing;
        }
    }

    /**
     * Resolve the handler's current inventory for the given node and channel.
     */
    private static <T extends IAEStack<T>> SubnetProxyProvider.Handler<T> liveHandler(SubnetProxyNode node,
            SubnetProxyChannel<T> channel, SubnetProxyProvider.Handler<T> handler) {

        return handler.provider.resolveHandler(node, channel, handler);
    }

    public static SubnetProxyNode origin(SubnetProxyView<?> receiving, IActionSource source) {
        Scope<?> scope = activeScope;
        if (scope != null && scope.matches(receiving, source)) return scope.node;
        if (source == null || !source.machine().isPresent()) return receiving.node();

        IActionHost machine = source.machine().get();
        IGridNode node = machine.getActionableNode();
        if (node == null) return null;

        IGridHost host = node.getMachine();
        if (host instanceof ICellProvider && receiving.node().isLocal(host, receiving.channel())) {
            return receiving.node();
        }

        if (machine instanceof ICellProvider) return null;

        return receiving.node().acceptsWorld(node.getWorld())
            && node.getGrid() == receiving.node().getGrid() ? receiving.node() : null;
    }

    private static boolean permitted(IGrid grid, IActionSource source, SecurityPermissions permission) {
        ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (source.player().isPresent()) return security.hasPermission(source.player().get(), permission);
        if (!source.machine().isPresent() || !security.isAvailable()) return true;

        IGridNode node = source.machine().get().getActionableNode();
        if (node == null) return false;
        if (node.getGrid() == grid) return true;

        ISecurityGrid requesting = node.getGrid().getCache(ISecurityGrid.class);
        return security.hasPermission(requesting.getOwner(), permission);
    }

    private static final class Attempt<T extends IAEStack<T>> {

        private final SubnetProxyProvider.Handler<T> handler;
        private final SubnetProxySnapshot<T> peer;

        private Attempt(SubnetProxyProvider.Handler<T> handler, SubnetProxySnapshot<T> peer) {
            this.handler = handler;
            this.peer = peer;
        }

        private boolean sticky() {
            return this.handler != null && this.handler.inventory.isSticky();
        }

        private int priority() {
            return this.peer != null ? this.peer.priority : this.handler.inventory.getPriority();
        }
    }

    private static final class InsertionAttempt<T extends IAEStack<T>> {

        private final SubnetProxyProvider.Handler<T> handler;
        private final int pass;

        private InsertionAttempt(SubnetProxyProvider.Handler<T> handler, int pass) {
            this.handler = handler;
            this.pass = pass;
        }
    }

    private static final class Simulation {

        private final Map<SubnetProxyProvider.Handler<?>, IItemList<?>> counts = new HashMap<>();

        @SuppressWarnings("unchecked")
        private <T extends IAEStack<T>> IItemList<T> amounts(
                SubnetProxyProvider.Handler<T> handler, SubnetProxyChannel<T> channel) {
            return (IItemList<T>) this.counts.computeIfAbsent(handler, key -> channel.getChannel().createList());
        }

        private <T extends IAEStack<T>> long counted(SubnetProxyProvider.Handler<T> handler,
                SubnetProxyChannel<T> channel, T resource) {
            T stack = this.amounts(handler, channel).findPrecise(resource);
            return stack == null ? 0 : stack.getStackSize();
        }

        private <T extends IAEStack<T>> void add(SubnetProxyProvider.Handler<T> handler,
                SubnetProxyChannel<T> channel, T resource, long amount) {
            this.amounts(handler, channel).addStorage(SubnetProxyView.quantity(resource, amount));
        }
    }

    private static final class Scope<T extends IAEStack<T>> {

        private final SubnetProxyNode node;
        private final SubnetProxyChannel<T> channel;
        private final SubnetProxyProvider.Handler<T> handler;
        private final IActionSource source;
        private final long generation;

        private Scope(SubnetProxyNode node, SubnetProxyChannel<T> channel,
                SubnetProxyProvider.Handler<T> handler, IActionSource source) {
            this.node = node;
            this.channel = channel;
            this.handler = handler;
            this.source = source;
            this.generation = node.getGeneration();
        }

        private boolean matches(SubnetProxyView<?> view, IActionSource source) {
            if (!this.node.isRegistered() || view.node() != this.node
                || view.channel().getChannel() != this.channel.getChannel()
                || view.monitor() != this.node.view(this.channel.getChannel()).monitor()
                || this.generation != this.node.getGeneration() || this.source != source) return false;

            return liveHandler(this.node, this.channel, this.handler) != null;
        }
    }
}
