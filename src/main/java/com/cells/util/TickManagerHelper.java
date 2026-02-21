package com.cells.util;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.PriorityQueue;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.me.cache.TickManagerCache;
import appeng.me.cache.helpers.TickTracker;

import com.cells.Cells;


/**
 * Workaround for an AE2 bug in {@link TickManagerCache}.
 * <p>
 * {@link TickManagerCache#removeNode} removes a node's {@link TickTracker} from
 * the {@code awake}/{@code sleeping}/{@code alertable} HashMaps, but does NOT
 * remove it from the {@code upcomingTicks} PriorityQueue. When {@code addNode}
 * is called immediately after (to re-register with new tick bounds), a new
 * TickTracker is created and added to the queue alongside the stale one.
 * <p>
 * The staleness guard in {@code onUpdateTick} checks
 * {@code awake.containsKey(tt.getNode())}, which passes for the stale tracker
 * because the NEW tracker is registered under the same node key. As a result,
 * the stale tracker keeps firing and is re-added to the queue every cycle.
 * Each {@code removeNode/addNode} cycle accumulates another phantom tracker,
 * causing the node to be ticked N times per cycle after N rate changes.
 * The issue resolves on world reload since the tick manager's state is rebuilt.
 * <p>
 * This helper uses reflection to access the {@code upcomingTicks} PriorityQueue
 * and purge any stale TickTracker entries for a given node before re-registering.
 */
public final class TickManagerHelper {

    // Cached reflection field for TickManagerCache.upcomingTicks
    private static Field upcomingTicksField;
    private static boolean reflectionFailed = false;

    private TickManagerHelper() {}

    /**
     * Safely re-register a tickable node with the tick manager.
     * Cleans up stale TickTracker entries from AE2's internal PriorityQueue
     * before calling removeNode/addNode, preventing phantom tick accumulation.
     *
     * @param node     the grid node to re-register
     * @param tickable the tickable machine (must also implement IGridHost, typically the tile entity itself)
     * @return true if re-registration succeeded, false if it failed
     */
    public static <T extends IGridTickable & IGridHost> boolean reRegisterTickable(IGridNode node, T tickable) {
        if (node == null || node.getGrid() == null) return false;

        ITickManager tickManager = node.getGrid().getCache(ITickManager.class);
        if (tickManager == null) return false;

        // Purge stale entries from the PriorityQueue before AE2's removeNode leaves them behind
        purgeStaleTrackers(tickManager, node);

        tickManager.removeNode(node, tickable);
        tickManager.addNode(node, tickable);

        return true;
    }

    /**
     * Remove all TickTracker entries for the given node from the tick manager's
     * internal PriorityQueue. This prevents stale trackers from accumulating
     * when a node is removed and re-added.
     */
    @SuppressWarnings("unchecked")
    private static void purgeStaleTrackers(ITickManager tickManager, IGridNode node) {
        if (reflectionFailed) return;
        if (!(tickManager instanceof TickManagerCache)) return;

        try {
            if (upcomingTicksField == null) {
                upcomingTicksField = TickManagerCache.class.getDeclaredField("upcomingTicks");
                upcomingTicksField.setAccessible(true);
            }

            PriorityQueue<TickTracker> upcomingTicks =
                (PriorityQueue<TickTracker>) upcomingTicksField.get(tickManager);

            Iterator<TickTracker> it = upcomingTicks.iterator();
            while (it.hasNext()) {
                TickTracker tracker = it.next();
                if (tracker.getNode() == node) it.remove();
            }
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            // If reflection fails (e.g. AE2 internals changed), log a warning once
            // and fall back to the buggy removeNode/addNode behavior. The issue will
            // self-resolve on world reload, so this is an acceptable degradation.
            reflectionFailed = true;
            Cells.LOGGER.warn("Failed to purge stale TickTrackers from AE2's tick manager. "
                + "Polling rate changes may cause erratic ticking until world reload.", e);
        }
    }
}
