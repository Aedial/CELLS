package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;


/**
 * Per-grid coordinator for subnet-proxy peer aggregation and event dedup.
 * <p>
 * One instance exists per AE2 {@link IGrid} that hosts at least one
 * {@link PartSubnetProxyFront}. Lookup is via a {@link WeakHashMap} keyed
 * by grid identity, so grids that get garbage collected take their
 * coordinators with them; explicit removal happens when the last front
 * unregisters.
 *
 * <h3>Responsibilities</h3>
 * <ol>
 *   <li><b>Per-origin election</b>: when multiple inbound proxies on the same
 *       destination grid expose the same origin-grid (diamond topology, e.g.
 *       {@code A→B→D + A→C→D} both expose {@code A}), exactly one is elected
 *       to publish that origin's items in BOTH listing and propagation paths.
 *       Election is deterministic across grids (smallest identity hash wins).</li>
 *   <li><b>Event UUID dedup</b>: a bounded LRU of recently-seen event UUIDs.
 *       Used as belt-and-suspenders against race conditions in election
 *       (e.g. mid-stream peer registration changes briefly allowing two
 *       fronts to forward the same origin's events).</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * All public methods are {@code synchronized} on this instance, since
 * AE2 monitors can post change events from network ticks while world
 * threads update peers via {@code updatePassthroughSources}.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Created on first {@link #registerFront}.</li>
 *   <li>Removed from the global map when {@link #unregisterFront} drains the
 *       last registered front (ref-count == 0).</li>
 *   <li>Re-created on demand if registration happens again later.</li>
 * </ul>
 */
public final class SubnetProxyGridCoordinator {

    /** Bound on event-UUID LRU. ~256 covers bursty cell-update events. */
    private static final int MAX_SEEN_EVENTS = 256;

    /** Per-grid singleton map. WeakHashMap so dead grids don't keep coords alive. */
    private static final Map<IGrid, SubnetProxyGridCoordinator> INSTANCES = new WeakHashMap<>();

    /**
     * Lazily fetch (or create) the coordinator for the given grid.
     * Synchronized on the class to protect the global map.
     */
    public static synchronized SubnetProxyGridCoordinator getOrCreate(IGrid grid) {
        if (grid == null) return null;
        SubnetProxyGridCoordinator c = INSTANCES.get(grid);
        if (c == null) {
            c = new SubnetProxyGridCoordinator();
            INSTANCES.put(grid, c);
        }
        return c;
    }

    /** Lookup without creating. Returns null if no coord exists for this grid. */
    public static synchronized SubnetProxyGridCoordinator getOrNull(IGrid grid) {
        if (grid == null) return null;
        return INSTANCES.get(grid);
    }

    // ========================= Instance state =========================

    /** Fronts whose front-grid is THIS coordinator's grid. */
    private final Set<PartSubnetProxyFront> registeredFronts = new HashSet<>();

    /**
     * origin-grid → elected front. Only the elected front for a given origin
     * is allowed to publish that origin's items (listing) or forward that
     * origin's deltas (propagation) into THIS coordinator's grid.
     * <p>
     * Recomputed eagerly on every register/unregister and on
     * {@link #onPeersChanged} (called by fronts when their peer set changes).
     */
    private final Map<IGrid, PartSubnetProxyFront> originElection = new HashMap<>();

    /**
     * LRU of recently-seen event UUIDs. {@link LinkedHashSet} preserves
     * insertion order, allowing eviction of the oldest entry when the cap
     * is reached.
     */
    private final LinkedHashSet<UUID> seenEvents = new LinkedHashSet<>();

    private SubnetProxyGridCoordinator() {}

    // ========================= Front registration =========================

    public synchronized void registerFront(PartSubnetProxyFront front) {
        if (registeredFronts.add(front)) recomputeElection();
    }

    /**
     * Unregister a front. If this drops the registered count to zero, the
     * coordinator removes itself from the global map (lazy cleanup).
     * <p>
     * Static-synchronized cleanup to coordinate with the global map mutex.
     */
    public void unregisterFront(PartSubnetProxyFront front) {
        boolean drained;
        synchronized (this) {
            registeredFronts.remove(front);
            recomputeElection();
            drained = registeredFronts.isEmpty();
        }
        if (drained) cleanupIfEmpty(this);
    }

    private static synchronized void cleanupIfEmpty(SubnetProxyGridCoordinator coord) {
        // Remove only if still empty (check inside global lock to avoid race with new register)
        synchronized (coord) {
            if (!coord.registeredFronts.isEmpty()) return;
        }
        Iterator<Map.Entry<IGrid, SubnetProxyGridCoordinator>> it = INSTANCES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IGrid, SubnetProxyGridCoordinator> e = it.next();
            if (e.getValue() == coord) {
                it.remove();
                break;
            }
        }
    }

    /**
     * Notify the coordinator that one of its fronts changed its
     * exposed-origins set (e.g. peers on its back-grid were added/removed).
     * Triggers election recomputation.
     */
    public synchronized void onPeersChanged() {
        recomputeElection();
    }

    /**
     * @return true if {@code front} is the elected publisher for
     *         {@code originGrid} on this coordinator's grid.
     */
    public synchronized boolean isElected(PartSubnetProxyFront front, IGrid originGrid) {
        return originElection.get(originGrid) == front;
    }

    // ========================= Election =========================

    /**
     * Deterministic election: sort fronts by identity hash, then for each,
     * claim every exposed origin not yet claimed. First-claim wins per origin.
     * <p>
     * O(F × O) where F = fronts on this grid, O = avg exposed origins per
     * front. Both small in practice.
     */
    private void recomputeElection() {
        originElection.clear();

        List<PartSubnetProxyFront> sorted = new ArrayList<>(registeredFronts);
        sorted.sort(Comparator.comparingInt(System::identityHashCode));

        for (PartSubnetProxyFront f : sorted) {
            Set<IGrid> exposed = f.getExposedOrigins();
            if (exposed == null || exposed.isEmpty()) continue;

            for (IGrid origin : exposed) {
                if (origin == null) continue;
                originElection.putIfAbsent(origin, f);
            }
        }

        // Election flips change which structural inventory surface each front
        // publishes. AE2 handles that via front-grid force updates; clearing the
        // proxy snapshots here prevents a later back-grid full-reset diff from
        // replaying those already-applied structural changes as item deltas.
        for (PartSubnetProxyFront front : registeredFronts) {
            front.onCoordinatorElectionChanged();
        }
    }

    // ========================= Event dedup =========================

    /**
     * Try to mark an event UUID as seen. Returns true if it was new (caller
     * should proceed with forwarding/posting), false if it's a duplicate.
     * Null UUIDs always return true (no dedup possible).
     */
    public synchronized boolean tryAccept(UUID eventId) {
        if (eventId == null) return true;
        if (seenEvents.contains(eventId)) return false;

        seenEvents.add(eventId);
        // Evict oldest until under cap
        while (seenEvents.size() > MAX_SEEN_EVENTS) {
            Iterator<UUID> it = seenEvents.iterator();
            it.next();
            it.remove();
        }
        return true;
    }
}
