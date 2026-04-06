package com.cells.blocks.interfacebase.managers;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;


/**
 * Manages the capability cache for auto-pull/push operations with adjacent blocks.
 * Handles scanning, caching, invalidation, and the actual pull/push transfers.
 * <p>
 * The adjacent handler is parameterized on <R, K> to support resource-type-specific
 * operations (counting, extracting, inserting resources from/to adjacent handlers).
 * The actual capability type (IItemHandler, IFluidHandler, etc.) is opaque to this
 * class, it stores handlers as Object and delegates type-specific work to callbacks.
 *
 * @param <R> The native resource stack type
 * @param <K> The hashable key type for the resource
 */
public class InterfaceAdjacentHandler<R, K> {

    /**
     * Resource-type-specific operations for adjacent handler interaction.
     */
    public interface ResourceOps<R, K> {

        /**
         * Get the Forge Capabilities that this interface type uses for adjacent interaction,
         * in priority order. The first non-null capability that an adjacent tile supports
         * will be cached. For example, items return [IItemRepository, IItemHandler] to
         * prefer slotless bulk operations over slot-by-slot iteration.
         *
         * @return Ordered list of capabilities to try, or empty if this type does not use Forge capabilities
         */
        List<Capability<?>> getAdjacentCapabilities();

        /**
         * Count how much of a resource matching the given key exists in the adjacent handler.
         */
        long countResourceInHandler(Object handler, K key, EnumFacing facing);

        /**
         * Extract resources matching the given key from the adjacent handler.
         * @return The amount actually extracted
         */
        long extractResourceFromHandler(Object handler, K key, int maxAmount, EnumFacing facing);

        /**
         * Insert resources into the adjacent handler.
         * @return The amount actually accepted
         */
        long insertResourceIntoHandler(Object handler, R identity, int maxAmount, EnumFacing facing);

        /**
         * Create an identity-only copy of a resource (amount=1).
         */
        R copyAsIdentity(R resource);

        /**
         * Build a map of all resources and their total amounts in the adjacent handler.
         * Used to pre-compute counts for keepQuantity calculations, avoiding repeated
         * iteration over the handler's slots for each filter during pull/push operations.
         * <p>
         * Returns null if the implementation does not support bulk enumeration
         * (e.g. essentia's per-aspect lookup is already O(1)), in which case
         * {@link #countResourceInHandler} is used per-key as a fallback.
         *
         * @param handler The adjacent handler (typed appropriately by each subclass)
         * @param facing The direction from us to the adjacent block
         * @return Map of resource key → total amount, or null for per-key fallback
         */
        @Nullable
        default Map<K, Long> buildResourceCountMap(Object handler, EnumFacing facing) { return null; }

        /**
         * Called at the end of a pull/push cycle to flush any per-cycle caches
         * built during extract/insert operations. Default no-op; subclasses that
         * cache handler state across multiple operations should clear it here.
         */
        default void flushOperationCaches() { }
    }

    /**
     * Callback interface for host/logic access.
     */
    public interface Callbacks {

        /** Get the world this host is in (may be null during loading). */
        @Nullable
        World getHostWorld();

        /** Get the position of this host in the world. */
        BlockPos getHostPos();

        /** Whether this is an export interface. */
        boolean isExport();

        /** Mark the host as dirty and save. */
        void markDirtyAndSave();

        /** Mark the host for a network (client) update. */
        void markForNetworkUpdate();
    }

    private final ResourceOps<R, K> ops;
    private final Callbacks callbacks;

    /**
     * Reference to the inventory manager for filter maps, slot operations, and transfer.
     */
    private final InterfaceInventoryManager<R, ?, K> inventoryManager;

    // ============================== Cache state ==============================

    /**
     * Cached weak references to adjacent tile entities, keyed by facing direction.
     * WeakReference prevents memory leaks if the TE is removed without neighborChanged firing.
     * Must check ref.get() != null && !te.isInvalid() before every use.
     */
    private final Map<EnumFacing, WeakReference<TileEntity>> cachedAdjacentTiles = new EnumMap<>(EnumFacing.class);

    /**
     * Cached capability handlers from adjacent tiles, keyed by facing direction.
     * The Object type is intentional, the concrete type (IItemHandler, IFluidHandler, etc.)
     * is known only by the subclass.
     */
    private final Map<EnumFacing, Object> cachedCapabilities = new EnumMap<>(EnumFacing.class);

    /**
     * Whether the capability cache has been scanned at least once.
     * Prevents redundant scans when no card is installed.
     */
    private boolean capabilityCachePopulated = false;

    public InterfaceAdjacentHandler(ResourceOps<R, K> ops, Callbacks callbacks,
                                    InterfaceInventoryManager<R, ?, K> inventoryManager) {
        this.ops = ops;
        this.callbacks = callbacks;
        this.inventoryManager = inventoryManager;
    }

    // ============================== Cache accessors ==============================

    /**
     * @return Whether the capability cache has been scanned at least once.
     */
    public boolean isCachePopulated() {
        return this.capabilityCachePopulated;
    }

    /**
     * Store a handler in the cache for a given facing direction.
     * For use by {@link #scanAndCacheFacing} implementations in subclasses.
     */
    protected void putCapabilityCache(EnumFacing facing, TileEntity te, Object handler) {
        this.cachedAdjacentTiles.put(facing, new WeakReference<>(te));
        this.cachedCapabilities.put(facing, handler);
    }

    // ============================== Cache management ==============================

    /**
     * Scan all 6 adjacent positions and cache any valid capabilities.
     * Called when a card is installed or when the interface first loads with a card.
     */
    public void refreshCapabilityCache() {
        this.cachedAdjacentTiles.clear();
        this.cachedCapabilities.clear();
        this.capabilityCachePopulated = true;

        World world = this.callbacks.getHostWorld();
        BlockPos pos = this.callbacks.getHostPos();
        if (world == null || pos == null) return;

        for (EnumFacing facing : EnumFacing.VALUES) {
            scanAndCacheFacing(world, pos, facing);
        }
    }

    /**
     * Scan a single adjacent position and cache the result.
     * Called by both {@link #refreshCapabilityCache()} and {@link #invalidateCapabilityCacheForFacing}.
     * <p>
     * Subclasses can override this to change how adjacent tiles are scanned
     * (e.g. checking for a specific interface instead of using Forge capabilities).
     *
     * @param world The world
     * @param pos Our position (not the adjacent position)
     * @param facing The direction to scan
     */
    protected void scanAndCacheFacing(World world, BlockPos pos, EnumFacing facing) {
        BlockPos adjacentPos = pos.offset(facing);

        // Don't load chunks to check neighbors
        if (!world.isBlockLoaded(adjacentPos)) {
            this.cachedAdjacentTiles.remove(facing);
            this.cachedCapabilities.remove(facing);
            return;
        }

        TileEntity te = world.getTileEntity(adjacentPos);
        if (te == null || te.isInvalid()) {
            this.cachedAdjacentTiles.remove(facing);
            this.cachedCapabilities.remove(facing);
            return;
        }

        List<Capability<?>> caps = this.ops.getAdjacentCapabilities();
        if (caps == null || caps.isEmpty()) {
            this.cachedAdjacentTiles.remove(facing);
            this.cachedCapabilities.remove(facing);
            return;
        }

        // Query capabilities in priority order; cache the first one the adjacent tile supports.
        // E.g., for items: try IItemRepository (slotless bulk) before IItemHandler (slot-by-slot).
        EnumFacing adjacentFace = facing.getOpposite();
        for (Capability<?> cap : caps) {
            if (cap == null) continue;

            Object handler = te.getCapability(cap, adjacentFace);
            if (handler != null) {
                this.cachedAdjacentTiles.put(facing, new WeakReference<>(te));
                this.cachedCapabilities.put(facing, handler);
                return;
            }
        }

        // No matching capability found
        this.cachedAdjacentTiles.remove(facing);
        this.cachedCapabilities.remove(facing);
    }

    /**
     * Clear all cached capabilities. Called when the card is removed.
     */
    public void clearCapabilityCache() {
        this.cachedAdjacentTiles.clear();
        this.cachedCapabilities.clear();
        this.capabilityCachePopulated = false;
    }

    /**
     * Invalidate the capability cache for a specific facing direction.
     * Called from onNeighborChanged when an adjacent block changes.
     * Immediately re-scans the specific facing if a card is installed.
     *
     * @param facing The direction that changed
     * @param hasAutoPullPushUpgrade Whether a card is currently installed
     */
    public void invalidateCapabilityCacheForFacing(EnumFacing facing, boolean hasAutoPullPushUpgrade) {
        this.cachedAdjacentTiles.remove(facing);
        this.cachedCapabilities.remove(facing);

        // If we have a card, immediately try to re-cache
        if (!hasAutoPullPushUpgrade) return;

        World world = this.callbacks.getHostWorld();
        BlockPos pos = this.callbacks.getHostPos();
        if (world == null || pos == null) return;

        scanAndCacheFacing(world, pos, facing);
    }

    /**
     * Called when a neighbor block changes. Determines which facing was affected
     * and invalidates the corresponding cache entry.
     *
     * @param neighborPos The position of the block that changed
     * @param hasAutoPullPushUpgrade Whether a card is currently installed
     */
    public void onNeighborChanged(BlockPos neighborPos, boolean hasAutoPullPushUpgrade) {
        BlockPos pos = this.callbacks.getHostPos();
        if (pos == null || neighborPos == null) return;

        for (EnumFacing facing : EnumFacing.VALUES) {
            if (pos.offset(facing).equals(neighborPos)) {
                invalidateCapabilityCacheForFacing(facing, hasAutoPullPushUpgrade);
                return;
            }
        }
    }

    /**
     * Validate a cached capability is still usable.
     * Checks that the WeakReference is still alive and the tile entity is not invalidated.
     * If stale, removes the cache entry and returns null.
     */
    @Nullable
    public Object getValidCachedCapability(EnumFacing facing) {
        WeakReference<TileEntity> ref = this.cachedAdjacentTiles.get(facing);
        if (ref == null) return null;

        TileEntity te = ref.get();
        if (te == null || te.isInvalid()) {
            // Stale reference, evict cache entry
            this.cachedAdjacentTiles.remove(facing);
            this.cachedCapabilities.remove(facing);
            return null;
        }

        return this.cachedCapabilities.get(facing);
    }

    /**
     * Check if any cached capability is present and valid.
     */
    public boolean hasAnyCachedCapability() {
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (getValidCachedCapability(facing) != null) return true;
        }
        return false;
    }

    // ============================== Pull/Push operations ==============================

    /**
     * Pull resources from an adjacent capability handler into our internal storage buffer.
     * Iterates our filter slots, and for each filtered resource present in the adjacent handler,
     * extracts up to the card's configured quantity while respecting keepQuantity and available space.
     *
     * @param adjacentHandler The adjacent capability handler (already validated by cache)
     * @param facing The direction from us to the adjacent block
     * @param quantity Maximum amount to transfer per resource type (from card config)
     * @param keepQuantity Amount to keep in the adjacent inventory (0 = take everything)
     * @return true if any resources were transferred
     */
    public boolean pullFromAdjacent(Object adjacentHandler, EnumFacing facing, int quantity, int keepQuantity) {
        boolean didWork = false;
        long maxSlotSize = this.inventoryManager.getMaxSlotSize();

        // Pre-compute resource counts to avoid re-scanning the adjacent handler per filter slot.
        // Only needed when keepQuantity > 0 (otherwise we don't care about adjacent amounts).
        Map<K, Long> resourceCountMap = keepQuantity > 0
                ? this.ops.buildResourceCountMap(adjacentHandler, facing) : null;

        for (int filterIdx : this.inventoryManager.getFilterSlotList()) {
            K filterKey = this.inventoryManager.getFilterKey(filterIdx);
            if (filterKey == null) continue;

            long spaceRemaining = maxSlotSize - this.inventoryManager.getSlotAmount(filterIdx);
            if (spaceRemaining <= 0) continue;

            int maxToExtract = (int) Math.min(quantity, spaceRemaining);

            if (keepQuantity > 0) {
                long adjacentAvailable = resourceCountMap != null
                        ? resourceCountMap.getOrDefault(filterKey, 0L)
                        : this.ops.countResourceInHandler(adjacentHandler, filterKey, facing);
                long available = adjacentAvailable - keepQuantity;
                if (available <= 0) continue;

                maxToExtract = (int) Math.min(maxToExtract, available);
            }

            if (maxToExtract <= 0) continue;

            long extracted = this.ops.extractResourceFromHandler(adjacentHandler, filterKey, maxToExtract, facing);
            if (extracted <= 0) continue;

            // Insert into our buffer, identity may already be preserved from filter match
            R identity = this.inventoryManager.getStorageIdentity(filterIdx);
            if (identity == null) {
                R filter = this.inventoryManager.getRawFilter(filterIdx);
                this.inventoryManager.setResourceInSlotWithAmount(filterIdx,
                        this.ops.copyAsIdentity(filter), extracted);
            } else {
                this.inventoryManager.adjustSlotAmount(filterIdx, extracted);
            }

            didWork = true;
        }

        if (didWork) {
            this.callbacks.markDirtyAndSave();
            this.callbacks.markForNetworkUpdate();
        }

        return didWork;
    }

    /**
     * Push resources from our internal storage buffer to an adjacent capability handler.
     * Iterates our filter slots, and for each slot with stored resources, pushes up to the
     * card's configured quantity while respecting keepQuantity in the adjacent inventory.
     * Orphaned resources are skipped (handled by the normal network I/O tick).
     *
     * @param adjacentHandler The adjacent capability handler (already validated by cache)
     * @param facing The direction from us to the adjacent block
     * @param quantity Maximum amount to transfer per resource type (from card config)
     * @param keepQuantity Target amount in adjacent inventory (0 = push freely)
     * @return true if any resources were transferred
     */
    public boolean pushToAdjacent(Object adjacentHandler, EnumFacing facing, int quantity, int keepQuantity) {
        boolean didWork = false;

        // Pre-compute resource counts to avoid re-scanning the adjacent handler per filter slot.
        Map<K, Long> resourceCountMap = keepQuantity > 0
                ? this.ops.buildResourceCountMap(adjacentHandler, facing) : null;

        for (int filterIdx : this.inventoryManager.getFilterSlotList()) {
            R identity = this.inventoryManager.getStorageIdentity(filterIdx);
            if (identity == null) continue;

            long ourStored = this.inventoryManager.getSlotAmount(filterIdx);
            if (ourStored <= 0) continue;

            // Skip orphaned resources, the normal network I/O tick handles these
            if (this.inventoryManager.isOrphanedSlot(filterIdx)) continue;

            K filterKey = this.inventoryManager.getFilterKey(filterIdx);
            if (filterKey == null) continue;

            int maxToPush = (int) Math.min(quantity, ourStored);

            if (keepQuantity > 0) {
                long adjacentAmount = resourceCountMap != null
                        ? resourceCountMap.getOrDefault(filterKey, 0L)
                        : this.ops.countResourceInHandler(adjacentHandler, filterKey, facing);
                long deficit = keepQuantity - adjacentAmount;
                if (deficit <= 0) continue;

                maxToPush = (int) Math.min(maxToPush, deficit);
            }

            if (maxToPush <= 0) continue;

            long accepted = this.ops.insertResourceIntoHandler(adjacentHandler, identity, maxToPush, facing);
            if (accepted <= 0) continue;

            this.inventoryManager.adjustSlotAmount(filterIdx, -accepted);
            if (this.inventoryManager.getSlotAmount(filterIdx) <= 0) this.inventoryManager.clearSlot(filterIdx);

            didWork = true;
        }

        if (didWork) {
            this.callbacks.markDirtyAndSave();
            this.callbacks.markForNetworkUpdate();
        }

        return didWork;
    }

    /**
     * Perform one cycle of auto-pull or auto-push operations across all cached capabilities.
     * Called from the tick scheduler when the card interval has elapsed.
     *
     * @param quantity Transfer quantity per type
     * @param keepQuantity Keep quantity threshold
     * @return true if any resource was transferred
     */
    public boolean performAutoPullPush(int quantity, int keepQuantity) {
        if (this.inventoryManager.getFilterSlotList().isEmpty()) return false;

        // Ensure cache is populated (handles first tick after load)
        if (!this.capabilityCachePopulated) refreshCapabilityCache();

        boolean didWork = false;
        boolean isExport = this.callbacks.isExport();

        for (EnumFacing facing : EnumFacing.VALUES) {
            Object handler = getValidCachedCapability(facing);
            if (handler == null) continue;

            if (isExport) {
                didWork |= pushToAdjacent(handler, facing, quantity, keepQuantity);
            } else {
                didWork |= pullFromAdjacent(handler, facing, quantity, keepQuantity);
            }
        }

        // Flush any per-cycle operation caches built during extract/insert
        this.ops.flushOperationCaches();

        return didWork;
    }
}
