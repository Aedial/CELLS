package com.cells.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.hooks.TickHandler;


/**
 * Utility for deferring cell operations to the end of tick.
 * <p>
 * This class batches {@code saveChanges()} calls and cross-tier notifications
 * to reduce overhead when many operations occur within a single tick.
 * </p>
 * 
 * <h2>Usage Pattern</h2>
 * <pre>
 * // In inject/extract:
 * if (mode == Actionable.MODULATE) {
 *     storedBaseUnits += amount;
 *     DeferredCellOperations.markDirty(this, container);
 *     DeferredCellOperations.queueCrossTierNotification(this, container, channel, changes, src);
 * }
 * </pre>
 * 
 * <h2>Thread Safety</h2>
 * This class assumes single-threaded access from the server thread only.
 * All operations are batched per-cell and flushed at end of server tick.
 */
public final class DeferredCellOperations {

    /**
     * Tracks cells that need saveChanges() called at end of tick.
     * Key is identity hash of the cell inventory instance.
     */
    private static final Map<Integer, PendingSave> pendingSaves = new HashMap<>();

    /**
     * Tracks cells with pending cross-tier notifications.
     * Key is identity hash of the cell inventory instance.
     */
    private static final Map<Integer, PendingNotification<?>> pendingNotifications = new HashMap<>();

    /** Whether the tick callback has been registered for this tick. */
    private static boolean callbackRegistered = false;

    private DeferredCellOperations() {}

    /**
     * Mark a cell as needing saveChanges() at end of tick.
     * Multiple calls within the same tick are coalesced into one save.
     *
     * @param cell      The cell inventory that changed
     * @param container The save provider (Drive/Chest tile entity)
     */
    public static void markDirty(ICellInventory<?> cell, @Nullable ISaveProvider container) {
        if (container == null) return;

        int key = System.identityHashCode(cell);
        if (!pendingSaves.containsKey(key)) pendingSaves.put(key, new PendingSave(cell, container));

        ensureCallbackRegistered();
    }

    /**
     * Queue a cross-tier notification to be batched and sent at end of tick.
     * Multiple notifications for the same cell are merged.
     *
     * @param cell      The cell inventory
     * @param container The save provider
     * @param channel   The storage channel
     * @param changes   The item changes to report
     * @param src       The action source
     * @param <T>       The stack type
     */
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> void queueCrossTierNotification(
            ICellInventory<T> cell,
            @Nullable ISaveProvider container,
            IStorageChannel<T> channel,
            List<T> changes,
            @Nullable IActionSource src) {

        if (changes == null || changes.isEmpty()) return;

        int key = System.identityHashCode(cell);
        PendingNotification<T> pending = (PendingNotification<T>) pendingNotifications.get(key);

        if (pending == null) {
            pending = new PendingNotification<>(container, channel, src);
            pendingNotifications.put(key, pending);
        }

        // Merge changes into pending notification
        pending.addChanges(changes);
        ensureCallbackRegistered();
    }

    /**
     * Ensure the end-of-tick callback is registered.
     */
    private static void ensureCallbackRegistered() {
        if (callbackRegistered) return;

        callbackRegistered = true;
        TickHandler.INSTANCE.addCallable(null, world -> {
            flushAll();
            return null;
        });
    }

    /**
     * Flush all pending operations. Called at end of tick.
     */
    private static void flushAll() {
        callbackRegistered = false;

        // Process saves first so NBT is up to date before notifications
        for (PendingSave save : pendingSaves.values()) save.execute();
        pendingSaves.clear();

        // Then process notifications
        for (PendingNotification<?> notification : pendingNotifications.values()) {
            notification.execute();
        }
        pendingNotifications.clear();
    }

    /**
     * Force immediate flush - used when cell is being removed from drive.
     * Call this before the cell's container reference becomes invalid.
     *
     * @param cell The cell being removed
     */
    public static void flushCell(ICellInventory<?> cell) {
        int key = System.identityHashCode(cell);

        PendingSave save = pendingSaves.remove(key);
        if (save != null) save.execute();

        PendingNotification<?> notification = pendingNotifications.remove(key);
        if (notification != null) notification.execute();
    }

    // ===============================
    // Internal helper classes
    // ===============================

    private static class PendingSave {
        private final ICellInventory<?> cell;
        private final ISaveProvider container;

        PendingSave(ICellInventory<?> cell, ISaveProvider container) {
            this.cell = cell;
            this.container = container;
        }

        void execute() {
            cell.persist();
            container.saveChanges(cell);
        }
    }

    private static class PendingNotification<T extends IAEStack<T>> {
        private final ISaveProvider container;
        private final IStorageChannel<T> channel;
        private final IActionSource src;
        private final List<T> mergedChanges = new ArrayList<>();

        PendingNotification(ISaveProvider container, IStorageChannel<T> channel, IActionSource src) {
            this.container = container;
            this.channel = channel;
            this.src = src;
        }

        void addChanges(List<T> changes) {
            // Merge changes - same item types should have their deltas summed
            for (T change : changes) {
                boolean merged = false;
                for (T existing : mergedChanges) {
                    if (existing.equals(change)) {
                        existing.setStackSize(existing.getStackSize() + change.getStackSize());
                        merged = true;
                        break;
                    }
                }

                if (!merged) mergedChanges.add(change.copy());
            }
        }

        void execute() {
            // Remove zero-delta changes (due to merges canceling each other out)
            mergedChanges.removeIf(stack -> stack.getStackSize() == 0);
            if (mergedChanges.isEmpty()) return;

            // Post cross-tier changes to grid. This is necessary because:
            // - DriveWatcher/ChestNetNotifier only report the directly operated item
            // - Cross-tier changes (e.g., block count when ingots are inserted) must be reported separately
            // - We skip the operated slot, so we're NOT double-reporting that item
            // IMPORTANT: Always prefer the container's grid over the source's grid!
            // The container (TileDrive/TileChest) is always on the correct grid where the cell
            // physically resides. The src might be from a different subnet (e.g., items injected
            // through a PartStorageBus on a subnet), which would cause cross-tier notifications
            // to go to the wrong grid, and the main grid would never learn about the changes.
            IGrid grid = CellMathHelper.getGridFromContainer(container);
            if (grid == null) grid = CellMathHelper.getGridFromSource(src);
            if (grid == null) return;

            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return;

            // Use CrossTierActionSource to avoid nesting detection issues
            IActionSource crossTierSource = (container instanceof IActionHost)
                ? new CrossTierActionSource((IActionHost) container)
                : src;

            storageGrid.postAlterationOfStoredItems(channel, mergedChanges, crossTierSource);
        }
    }
}
