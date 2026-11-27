package com.cells.parts.subnetproxy;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import com.cells.Cells;
import com.cells.config.CellsConfig;
import com.cells.Tags;


/**
 * Event handler and scheduler for subnet proxy management, handling dirty nodes, parts, and views.
 * It manages the lifecycle of the proxies, and coordinates their initialization, updates, and retirement.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class SubnetProxyManager {

    /** Maximum number of topology passes (retries) allowed within a tick */
    private static final int MAX_TOPOLOGY_PASSES = 8;

    /** Cached read paths for this session */
    private static final SubnetProxyTopology TOPOLOGY = new SubnetProxyTopology();

    /** Grid nodes with valid proxies, that shutdown must release */
    private static final Set<SubnetProxyNode> NODES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Pending grid nodes, waiting for a refresh of their proxies */
    private static Set<SubnetProxyNode> DIRTY_NODES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Physical Fronts that require a (re-)capture of their snapshots */
    private static Set<PartSubnetProxyFront> DIRTY_PARTS = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Grid owners awaiting capture of their edited physical Fronts */
    private static Set<SubnetProxyNode> PENDING_PART_NODES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Captured physical Fronts, so parallel edits can rebuild shared directed edges */
    private static final Map<PartSubnetProxyFront, CapturedSnapshots> SNAPSHOTS = new IdentityHashMap<>();

    /** List of pending refreshes for subnet proxy views */
    private static final Map<SubnetProxyView<?>, SubnetProxyRefresh> REFRESHES = new IdentityHashMap<>();

    /** List of Views to release the listener for, after they are no longer needed */
    private static final Set<SubnetProxyView<?>> RETIRING_VIEWS = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Managed publication provenance retained until the following server START */
    private static final Map<IActionSource, Delivery<?>> DELIVERIES = new IdentityHashMap<>();

    /** Processing queue for nested deltas triggered during dispatch or contribution replacement */
    private static final Deque<Runnable> FRAMES = new ArrayDeque<>();

    /** Shared channel implementations initialized after channel registration */
    private static List<SubnetProxyChannel<?>> channels;

    /** Guard that keeps nested notifications processed linearly */
    private static boolean dispatching;

    /** Number of ticks since the server started, for tracking and scheduling purposes */
    private static long tick;

    /** Revision changed by new lifecycle events, to reject interrupted topology captures */
    private static long inputRevision;

    /** Topology revision last processed for stable imported quantities */
    private static long appliedTopologyRevision;

    private SubnetProxyManager() {
    }

    public static List<SubnetProxyChannel<?>> channels() {
        if (channels == null) channels = Collections.unmodifiableList(SubnetProxyChannel.available());

        return channels;
    }

    public static long tick() {
        return tick;
    }

    /**
     * Minimum number of stable ticks required for a Proxy to re-init after grid reset.
     */
    public static int settlingTicks() {
        return CellsConfig.general.subnetProxyMinTickRate;
    }

    public static SubnetProxyTopology topology() {
        return TOPOLOGY;
    }

    public static void makeDirty(SubnetProxyNode node) {
        inputRevision++;
        node.inputChanged();
        DIRTY_NODES.add(node);
    }

    public static void makeDirty(PartSubnetProxyFront part) {
        inputRevision++;
        DIRTY_PARTS.add(part);

        Set<SubnetProxyNode> nodes = endpoints(part);
        IGridNode front = part.getProxy().getNode();
        if (front != null) nodes.add(front.getGrid().getCache(ISubnetProxyNode.class));

        IGrid back = part.getBackGrid();
        if (back != null) nodes.add(back.getCache(ISubnetProxyNode.class));

        for (SubnetProxyNode node : nodes) {
            node.inputChanged();
            PENDING_PART_NODES.add(node);
        }
    }

    public static boolean hasPendingInput(SubnetProxyNode node) {
        return DIRTY_NODES.contains(node) || PENDING_PART_NODES.contains(node);
    }

    /**
     * Collects the subnet proxy nodes that are directly connected to the given Front.
     */
    public static Set<SubnetProxyNode> endpoints(PartSubnetProxyFront part) {
        Set<SubnetProxyNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        CapturedSnapshots captured = SNAPSHOTS.get(part);
        if (captured == null) return result;

        for (SubnetProxySnapshot<?> snapshot : captured.snapshots) {
            result.add(snapshot.back);
            result.add(snapshot.front);
        }

        return result;
    }

    public static void schedule(SubnetProxyView<?> view) {
        REFRESHES.computeIfAbsent(view, SubnetProxyRefresh::new);
    }

    public static void unschedule(SubnetProxyView<?> view) {
        REFRESHES.remove(view);
    }

    public static void retireListener(SubnetProxyView<?> view) {
        RETIRING_VIEWS.add(view);
    }

    private static void releaseListeners() {
        List<SubnetProxyView<?>> retiring = new ArrayList<>(RETIRING_VIEWS);
        RETIRING_VIEWS.clear();

        for (SubnetProxyView<?> view : retiring) {
            try {
                view.unsubscribe();
            } catch (RuntimeException failure) {
                Cells.LOGGER.error("Subnet Proxy monitor detach failed", failure);
            }
        }
    }

    /**
     * Dispatch owned physical changes from the given origin view to the network.
     */
    public static <T extends IAEStack<T>> void dispatch(SubnetProxyView<T> origin,
            IItemList<T> changes, IActionSource source) {

        FRAMES.add(TOPOLOGY.frame(origin, changes, source));

        if (!dispatching) drainFrames();
    }

    /**
     * Complete a contribution replacement before forwarding changes triggered by its callbacks.
     */
    public static void publish(Runnable publication) {
        boolean nested = dispatching;
        dispatching = true;
        try {
            publication.run();
        } finally {
            if (!nested) drainFrames();
        }
    }

    private static void drainFrames() {
        dispatching = true;
        try {
            while (!FRAMES.isEmpty()) {
                Runnable frame = FRAMES.remove();
                try {
                    frame.run();
                } catch (RuntimeException error) {
                    Cells.LOGGER.error("Subnet Proxy delta publication failed", error);
                }
            }
        } finally {
            dispatching = false;
            FRAMES.clear();
        }
    }

    /**
     * Send the local stock of the origin view to relevant networks as import contributions.
     * This is for networks that have initialized before us, ensuring they receive our current state.
     */
    public static <T extends IAEStack<T>> void supplyInitialized(SubnetProxyView<T> origin) {
        TOPOLOGY.supplyInitialized(origin);
    }

    private static void topologyTransaction() {
        for (int pass = 0; pass < MAX_TOPOLOGY_PASSES; pass++) {
            if (DIRTY_NODES.isEmpty() && DIRTY_PARTS.isEmpty()) return;

            Set<SubnetProxyNode> nodes = DIRTY_NODES;
            DIRTY_NODES = Collections.newSetFromMap(new IdentityHashMap<>());

            Set<PartSubnetProxyFront> parts = DIRTY_PARTS;
            DIRTY_PARTS = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<SubnetProxyNode> capturedPartNodes = null;

            try {
                // Register and capture parts for all dirty nodes
                for (SubnetProxyNode node : nodes) {
                    node.register();
                    if (node.isRegistered()) NODES.add(node);

                    node.captureParts();
                }

                // Add any newly dirty parts from captureParts() calls
                parts.addAll(DIRTY_PARTS);
                DIRTY_PARTS.clear();

                Set<SubnetProxyNode> roots = Collections.newSetFromMap(new IdentityHashMap<>());
                roots.addAll(nodes);

                // Capture all parts connected to the dirty nodes and refresh the topology accordingly
                for (PartSubnetProxyFront part : parts) roots.addAll(endpoints(part));
                Set<SubnetProxyNode> affected = TOPOLOGY.neighborhood(roots);
                long currentRevision = inputRevision;
                Map<PartSubnetProxyFront, List<SubnetProxySnapshot<?>>> captured = new IdentityHashMap<>();

                for (PartSubnetProxyFront part : parts) {
                    List<SubnetProxySnapshot<?>> snapshots = part.captureConnection();
                    captured.put(part, snapshots);

                    for (SubnetProxySnapshot<?> snapshot : snapshots) {
                        roots.add(snapshot.back);
                        roots.add(snapshot.front);
                        NODES.add(snapshot.back);
                        NODES.add(snapshot.front);
                    }
                }

                // If the parts became dirty during capture, invalidate and retry
                if (currentRevision != inputRevision) {
                    for (List<SubnetProxySnapshot<?>> snapshots : captured.values()) TOPOLOGY.invalidate(snapshots);
                    for (PartSubnetProxyFront part : parts) part.invalidateSnapshots();
                    DIRTY_PARTS.addAll(parts);

                    continue;
                }

                for (Map.Entry<PartSubnetProxyFront, List<SubnetProxySnapshot<?>>> entry : captured.entrySet()) {
                    if (entry.getValue().isEmpty()) {
                        SNAPSHOTS.remove(entry.getKey());
                    } else {
                        SNAPSHOTS.put(entry.getKey(), new CapturedSnapshots(
                            entry.getKey().getHostWorld(), entry.getValue()));
                    }

                    TOPOLOGY.capture(entry.getValue());
                }

                TOPOLOGY.commit();

                capturedPartNodes = PENDING_PART_NODES;
                PENDING_PART_NODES = Collections.newSetFromMap(new IdentityHashMap<>());

                long currentTopologyRevision = TOPOLOGY.revision();
                boolean topologyChanged = currentTopologyRevision != appliedTopologyRevision;
                affected.addAll(TOPOLOGY.neighborhood(roots));
                for (SubnetProxyNode node : affected) {
                    for (SubnetProxyView<?> view : node.views()) {
                        boolean tracking = TOPOLOGY.participates(node, view.channel().getChannel());
                        view.setWork(tracking, TOPOLOGY.forwards(node, view.channel().getChannel()));
                    }
                }

                // Process grid resets for affected nodes and update their views accordingly
                for (SubnetProxyNode node : affected) node.resetGridIfNeeded();
                for (SubnetProxyNode node : affected) {
                    for (SubnetProxyView<?> view : node.views()) {
                        if (!view.isTracking()) {
                            view.suspend();
                        } else if (topologyChanged && view.isStable()) {
                            TOPOLOGY.gather(view);
                        }
                    }
                }

                // Retire nodes that no longer have any parts attached
                for (SubnetProxyNode node : nodes) {
                    if (node.hasParts()) continue;

                    node.retire();
                    NODES.remove(node);
                }

                appliedTopologyRevision = currentTopologyRevision;
            } catch (RuntimeException failure) {
                for (PartSubnetProxyFront part : parts) part.invalidateSnapshots();

                if (capturedPartNodes != null) {
                    if (PENDING_PART_NODES.isEmpty()) PENDING_PART_NODES = capturedPartNodes;
                    else PENDING_PART_NODES.addAll(capturedPartNodes);
                }

                if (DIRTY_NODES.isEmpty()) DIRTY_NODES = nodes;
                else DIRTY_NODES.addAll(nodes);

                if (DIRTY_PARTS.isEmpty()) DIRTY_PARTS = parts;
                else DIRTY_PARTS.addAll(parts);

                throw failure;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tick++;

            // Release metadata from the previous tick
            SubnetProxyOperation.expire();
            DELIVERIES.clear();

            // Release any listeners for views that are no longer needed
            releaseListeners();

            // Remove the proxies that have completed their refresh cycle (baseline aquisition) after submit
            for (Map.Entry<SubnetProxyView<?>, SubnetProxyRefresh> entry : new ArrayList<>(REFRESHES.entrySet())) {
                try {
                    if (entry.getValue().start()) REFRESHES.remove(entry.getKey(), entry.getValue());
                } catch (RuntimeException error) {
                    entry.getKey().retryAfterFailure();
                    Cells.LOGGER.error("Subnet Proxy listing refresh failed", error);
                }
            }

            return;
        }

        // --- event.phase == TickEvent.Phase.END ---

        // Process any pending topology changes in the subnet proxy network
        try {
            topologyTransaction();
        } catch (RuntimeException error) {
            Cells.LOGGER.error("Subnet Proxy topology transaction failed", error);
        }

        // Re-trigger any pending refreshes for processing in the next tick (START)
        for (SubnetProxyRefresh refresh : new ArrayList<>(REFRESHES.values())) {
            refresh.submitReset();
        }
    }

    public static Delivery<?> delivery(IActionSource source) {
        return DELIVERIES.get(source);
    }

    public static <T extends IAEStack<T>> Delivery<T> beginDelivery(
            SubnetProxyView<T> target, SubnetProxyNode origin, T change, IActionSource source) {
        Delivery<T> delivery = new Delivery<>(target, origin, change, source);
        DELIVERIES.put(delivery.source, delivery);

        return delivery;
    }

    public static void shutdown() {
        Set<SubnetProxyNode> retiring = Collections.newSetFromMap(new IdentityHashMap<>());
        retiring.addAll(NODES);
        retiring.addAll(DIRTY_NODES);
        retiring.addAll(PENDING_PART_NODES);

        for (SubnetProxyNode node : retiring) {
            try {
                node.dispose();
            } catch (RuntimeException error) {
                Cells.LOGGER.error("Subnet Proxy retirement failed", error);
            }
        }

        releaseListeners();
        NODES.clear();
        DIRTY_NODES.clear();
        DIRTY_PARTS.clear();
        PENDING_PART_NODES.clear();
        SNAPSHOTS.clear();
        REFRESHES.clear();
        DELIVERIES.clear();
        FRAMES.clear();
        TOPOLOGY.clear();
        appliedTopologyRevision = TOPOLOGY.revision();
        SubnetProxyOperation.clear();
        dispatching = false;
        tick = 0;
        inputRevision = 0;
    }

    @SubscribeEvent
    public static void worldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;

        inputRevision++;
        Set<SubnetProxyNode> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        DIRTY_PARTS.removeIf(part -> part.getHostWorld() == event.getWorld());
        Iterator<Map.Entry<PartSubnetProxyFront, CapturedSnapshots>> iterator = SNAPSHOTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PartSubnetProxyFront, CapturedSnapshots> entry = iterator.next();
            if (entry.getValue().world.get() != event.getWorld()) continue;

            for (SubnetProxySnapshot<?> snapshot : entry.getValue().snapshots) {
                roots.add(snapshot.back);
                roots.add(snapshot.front);
            }

            entry.getKey().invalidateSnapshots();
            TOPOLOGY.invalidate(entry.getValue().snapshots);
            iterator.remove();
        }

        Set<SubnetProxyNode> retiring = Collections.newSetFromMap(new IdentityHashMap<>());
        retiring.addAll(NODES);
        retiring.addAll(DIRTY_NODES);
        retiring.addAll(PENDING_PART_NODES);

        for (SubnetProxyNode node : retiring) {
            if (!node.belongsTo(event.getWorld())) continue;

            roots.add(node);
            DELIVERIES.values().removeIf(delivery -> !delivery.active
                && (delivery.target.node() == node || delivery.origin == node));

            if (node.retireWorld(event.getWorld())) {
                DIRTY_NODES.remove(node);
                PENDING_PART_NODES.remove(node);
                NODES.remove(node);
            }
        }

        Set<SubnetProxyNode> affected = TOPOLOGY.neighborhood(roots);
        TOPOLOGY.commit();
        affected.addAll(TOPOLOGY.neighborhood(roots));

        for (SubnetProxyNode node : affected) {
            if (NODES.contains(node)) makeDirty(node);
        }
    }

    private static final class CapturedSnapshots {

        /** Weak reference to the world of the proxy connection */
        private final WeakReference<World> world;

        /** Cached Front settings, kept until replacement or retirement */
        private final List<SubnetProxySnapshot<?>> snapshots;

        private CapturedSnapshots(World world, List<SubnetProxySnapshot<?>> snapshots) {
            this.world = new WeakReference<>(world);
            this.snapshots = new ArrayList<>(snapshots);
        }
    }

    /**
     * Cross-grid tracking of delta changes
     */
    public static final class Delivery<T extends IAEStack<T>> {

        // TODO: Should we use the original action source instead of creating a new one?
        //       May not fare well with AE2's nested action guards
        //       Using the original action source could help with passthrough deduplication?
        //       Batching would require careful handling of uneven parallel Fronts
        //       => We would need to collect every target and the unique changes for this grid,
        //          instead of propagating filtered changes directly.
        //          This means projecting edges from the origin to all reachable targets
        //          and build a filter list for each, maybe backed by a LRU cache to avoid exploding RAM.

        /** Unique source for this delivery, to identify it through postAlterationOfStoredItems */
        public final IActionSource source;

        /** Grid the delivery targets */
        private final SubnetProxyView<T> target;

        /** Grid the delivery originates from */
        private final SubnetProxyNode origin;

        /** Owned change retained through publication and deferred callbacks */
        private final T change;

        /** Node generation of the target at the time of publication */
        private final long generation;
        /** Grid reset revision of the target at the time of publication */
        private final long resetRevision;

        /** Whether the delivery has been processed on the target grid (for deduplication) */
        private boolean applied;

        /** Last callback result for publication failure diagnostics */
        private String callback = "not received";

        /** Whether the delivery is still valid */
        private boolean active = true;

        private Delivery(SubnetProxyView<T> target, SubnetProxyNode origin, T change, IActionSource source) {
            this.source = new SubnetProxyActionSource(source);
            this.target = target;
            this.origin = origin;
            this.change = change;
            this.generation = target.node().getGeneration();
            this.resetRevision = target.resetRevision();
        }

        public boolean completed() {
            return this.applied && this.target.resetRevision() == this.resetRevision;
        }

        public void warning() {
            Cells.LOGGER.warn("Subnet Proxy delivery failed: channel={} origin={} target={} resource={} x {}"
                + "lastCallback={} applied={} registered={} valid={} generation={}/{} resetRevision={}/{}",
                this.target.channel().getType(), this.origin, this.target.node(), this.change.getStackSize(),
                this.change, this.callback, this.applied, this.target.node().isRegistered(),
                this.target.isValid(this.target), this.generation, this.target.node().getGeneration(),
                this.resetRevision, this.target.resetRevision());
        }

        public void close() {
            this.active = false;
        }

        public boolean accept(SubnetProxyView<?> receiver, IBaseMonitor<?> monitor, Iterable<?> changes) {
            if (receiver.node() != this.target.node()) {
                this.callback = "receiver grid mismatch: received=" + receiver.node();
                return false;
            }

            if (monitor != this.target.monitor()) {
                this.callback = "monitor mismatch: expected=" + this.target.monitor() + " received=" + monitor;
                return false;
            }

            Iterator<?> iterator = changes.iterator();
            if (!iterator.hasNext()) {
                this.callback = "empty changes";
                return false;
            }

            Object candidate = iterator.next();
            if (iterator.hasNext() || !(candidate instanceof IAEStack)) {
                this.callback = "multiple changes or invalid stack: received=" + candidate;
                return false;
            }

            IAEStack<?> stack = (IAEStack<?>) candidate;
            if (!this.change.equals(stack) || this.change.getStackSize() != stack.getStackSize()) {
                this.callback = "resource or quantity mismatch: received=" + stack + " delta=" + stack.getStackSize();
                return false;
            }

            if (receiver != this.target) {
                this.callback = "receiver view replaced";
                return true;
            }

            if (receiver.node().getGeneration() != this.generation || receiver.resetRevision() != this.resetRevision) {
                this.callback = "generation or reset revision changed";
                return true;
            }

            if (this.applied) {
                this.callback = "already applied";
                return true;
            }

            this.callback = "accounting";
            this.target.account(this.origin, this.change);
            this.applied = true;
            this.callback = "applied";

            return true;
        }
    }
}
