package com.cells.blocks.interfacebase.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;

import com.cells.config.CellsConfig;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;


/**
 * Manages all filter and storage operations for a resource interface:
 * filter-to-slot mapping, storage slot CRUD, external handler APIs (receive/drain),
 * ME network interactions (import/export), capacity reduction, serialization, and drops.
 * <p>
 * This is the result of merging InterfaceFilterManager and InterfaceSlotManager,
 * plus moving network and serialization methods from the logic class.
 * <p>
 * Operates on arrays owned by the parent logic (filters, storage, amounts).
 * The parent logic keeps those as protected fields for subclass direct access.
 *
 * @param <R>  The native resource stack type (FluidStack, GasStack, etc.)
 * @param <AE> The AE2 wrapped stack type (IAEFluidStack, IAEGasStack, etc.)
 * @param <K>  The hashable key type for the resource (FluidStackKey, GasStackKey, etc.)
 */
public class InterfaceInventoryManager<R, AE extends IAEStack<AE>, K> {

    private static final int FILTER_SLOTS = AbstractResourceInterfaceLogic.FILTER_SLOTS;
    private static final int STORAGE_SLOTS = AbstractResourceInterfaceLogic.STORAGE_SLOTS;
    private static final int TOTAL_SLOTS = AbstractResourceInterfaceLogic.TOTAL_SLOTS;
    private static final int MIN_MAX_SLOT_SIZE = 1;

    // ============================== Strategy / Callback interfaces ==============================

    /**
     * Strategy interface for resource-type-specific operations.
     * The parent logic bridges its abstract methods to this interface.
     */
    public interface ResourceOps<R, AE extends IAEStack<AE>, K> {

        /** Create a hashable key for the given resource. */
        @Nullable
        K createKey(R resource);

        /** Get the amount from an external resource stack. */
        int getAmount(R resource);

        /** Create a copy of a resource with the given amount. */
        R copyWithAmount(R resource, int amount);

        /** Create an identity-only copy of a resource (amount=1). */
        R copyAsIdentity(R resource);

        /** Create a copy of a resource. */
        R copy(R resource);

        /** Get the display name of a resource for chat messages. */
        String getLocalizedName(R resource);

        /** Convert a native resource to an AE stack. */
        AE toAEStack(R resource);

        /** Convert an AE stack to a native resource. */
        R fromAEStack(AE aeStack);

        /** Get the AE stack size. */
        long getAEStackSize(AE aeStack);

        /** Get the ME inventory for this resource type. */
        IMEInventory<AE> getMEInventory(IStorageGrid storage);

        /** Write a resource to NBT. */
        void writeResourceToNBT(R resource, NBTTagCompound tag);

        /** Read a resource from NBT. */
        @Nullable
        R readResourceFromNBT(NBTTagCompound tag);

        /** Write a resource to a ByteBuf stream. Default wraps writeResourceToNBT via ByteBufUtils. */
        default void writeResourceToStream(R resource, ByteBuf data) {
            NBTTagCompound tag = new NBTTagCompound();
            writeResourceToNBT(resource, tag);
            ByteBufUtils.writeTag(data, tag);
        }

        /** Read a resource from a ByteBuf stream. Default wraps readResourceFromNBT via ByteBufUtils. */
        @Nullable
        default R readResourceFromStream(ByteBuf data) {
            NBTTagCompound tag = ByteBufUtils.readTag(data);
            return tag != null ? readResourceFromNBT(tag) : null;
        }

        /**
         * Create a recovery item for remainder resources that couldn't be stored.
         * Return ItemStack.EMPTY if no recovery item is available for this type.
         */
        ItemStack createRecoveryItem(R identity, long amount);
    }

    /**
     * Callback interface for host/logic notifications and environment access.
     */
    public interface Callbacks {

        /** Whether this is an export interface. */
        boolean isExport();

        /** Mark the host as dirty and save. */
        void markDirtyAndSave();

        /** Mark the host for a network (client) update. */
        void markForNetworkUpdate();

        /** Wake up the interface if in adaptive polling mode. */
        void wakeUpIfAdaptive();

        /** Get the effective filter slot count based on capacity upgrades. */
        int getEffectiveFilterSlots();

        /** Get the AE2 grid proxy for network access. */
        AENetworkProxy getGridProxy();

        /** Get the action source for ME network operations. */
        IActionSource getActionSource();

        /**
         * Get the maximum request size for the AE network.
         * Used to clamp requests to avoid issues with some mods.
         */
        long getMaxAENetworkRequestSize();

        /** Get the world this host is in (may be null during loading). */
        @Nullable
        World getHostWorld();

        /** Get the position of this host in the world. */
        BlockPos getHostPos();
    }

    private final ResourceOps<R, AE, K> ops;
    private final Callbacks callbacks;

    /**
     * Direct references to the parent logic's arrays.
     * These are NOT copies, modifications here are visible to the parent and subclasses.
     */
    private final R[] filters;
    private final R[] storage;
    private final long[] amounts;

    /** Maximum amount per slot. Managed by the parent logic (get/set via accessor). */
    private long maxSlotSize;

    // ============================== Derived data structures ==============================
    // All maps/sets are private. External access goes through accessor methods.

    /** Mapping of filter resource keys to their corresponding slot index. */
    private final Map<K, Integer> filterToSlotMap = new HashMap<>();

    /** Reverse mapping: slot index to filter key. */
    private final Map<Integer, K> slotToFilterMap = new HashMap<>();

    /**
     * List of slot indices that have filters, in slot order.
     * Cleared and refilled in-place during refreshFilterMap().
     */
    private final List<Integer> filterSlotList = new ArrayList<>();

    /** Unmodifiable view of filterSlotList for external read-only access. */
    private final List<Integer> filterSlotListView = Collections.unmodifiableList(filterSlotList);

    /** Mapping of storage resource keys to their corresponding slot index. */
    private final Map<K, Integer> storageToSlotMap = new HashMap<>();

    /** Reverse mapping: slot index to storage key. */
    private final Map<Integer, K> slotToStorageKeyMap = new HashMap<>();

    /**
     * Set of slot indices that are "orphaned", the stored identity doesn't match the filter.
     * Export interfaces return orphaned resources to the ME network.
     * A slot is orphaned when it has a non-null identity AND either:
     * - No filter is set for that slot, OR
     * - The filter key doesn't match the storage key.
     */
    private final Set<Integer> orphanedSlots = new HashSet<>();

    public InterfaceInventoryManager(ResourceOps<R, AE, K> ops, Callbacks callbacks,
                                     R[] filters, R[] storage, long[] amounts,
                                     long maxSlotSize) {
        this.ops = ops;
        this.callbacks = callbacks;
        this.filters = filters;
        this.storage = storage;
        this.amounts = amounts;
        this.maxSlotSize = this.validateMaxSlotSize(maxSlotSize);
    }

    /**
     * Get the maximum allowed slot size from config.
     * @return The configured max slot size limit (defaults to Long.MAX_VALUE)
     */
    public long getMaxMaxSlotSize() {
        return CellsConfig.interfaceMaxSlotSizeLimit;
    }

    public long getMaxSlotSize() {
        return this.maxSlotSize;
    }

    /**
     * Get the maximum slot size as an int, clamped to Integer.MAX_VALUE for external API compatibility.
     */
    public int getMaxSlotSizeInt() {
        return (int) Math.min(this.maxSlotSize, Integer.MAX_VALUE);
    }

    public long validateMaxSlotSize(long size) {
        return Math.max(MIN_MAX_SLOT_SIZE, Math.min(size, getMaxMaxSlotSize()));
    }

    /**
     * Set the maximum slot capacity.
     * If export and reduced, returns overflow to the network.
     */
    public long setMaxSlotSize(long size) {
        long oldSize = this.maxSlotSize;
        this.maxSlotSize = this.validateMaxSlotSize(size);

        this.callbacks.markDirtyAndSave();

        if (this.callbacks.isExport()) {
            if (oldSize > this.maxSlotSize) this.returnOverflowToNetwork();
            if (oldSize < this.maxSlotSize) this.callbacks.wakeUpIfAdaptive();
        }

        return this.maxSlotSize;
    }

    // ============================== Accessor methods for encapsulated data ==============================

    /**
     * @return Unmodifiable view of the list of slot indices that have filters, in slot order.
     */
    public List<Integer> getFilterSlotList() {
        return this.filterSlotListView;
    }

    /**
     * @return Unmodifiable set of slot indices that have non-null storage identities.
     *         Note: some of these may have amount == 0 due to identity preservation
     *         (when a filter matches the slot). Callers should check amounts if needed.
     */
    public Set<Integer> getOccupiedStorageSlots() {
        return Collections.unmodifiableSet(this.slotToStorageKeyMap.keySet());
    }

    /**
     * @return Unmodifiable list of slot indices that have non-null storage identities and amount > 0.
     *         This list may not be sorted, so be mindful when iterating.
     */
    public List<Integer> getNonEmptyStorageSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot : this.slotToStorageKeyMap.keySet()) {
            if (getSlotAmount(slot) > 0) slots.add(slot);
        }

        return Collections.unmodifiableList(slots);
    }

    /**
     * Get the filter key for a specific slot.
     * @return The filter key, or null if no filter in that slot
     */
    @Nullable
    public K getFilterKey(int slot) {
        return this.slotToFilterMap.get(slot);
    }

    /**
     * Get the slot index for a given filter key.
     * @return The slot index, or null if no such filter key
     */
    @Nullable
    public Integer getFilterSlot(K key) {
        return this.filterToSlotMap.get(key);
    }

    /**
     * Check if a filter with the given key exists.
     */
    public boolean containsFilterKey(@Nonnull K key) {
        return this.filterToSlotMap.containsKey(key);
    }

    /**
     * Get the storage key for a specific slot.
     * @return The storage key, or null if no storage identity in that slot
     */
    @Nullable
    public K getStorageKey(int slot) {
        return this.slotToStorageKeyMap.get(slot);
    }

    /**
     * Get the raw storage identity (without amount) for a specific slot.
     * Used by stream serialization and external handlers that need type info.
     * @return The storage identity, or null if slot is empty
     */
    @Nullable
    public R getStorageIdentity(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;
        return this.storage[slot];
    }

    /**
     * Get the raw filter resource for a specific slot.
     * Used by stream serialization that needs direct filter access.
     * @return The filter resource, or null if no filter
     */
    @Nullable
    public R getRawFilter(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;
        return this.filters[slot];
    }

    /**
     * Set a filter resource directly in the array (bypassing onFilterChanged).
     * Used only by stream deserialization to bulk-populate filters before a final refreshFilterMap().
     *
     * @param slot The filter slot index
     * @param resource The filter resource, or null to clear
     */
    public void setFilterDirect(int slot, @Nullable R resource) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        this.filters[slot] = resource;
    }

    /**
     * Clear all storage slots for deserialization (bypasses identity preservation in clearSlot).
     * Used by stream deserialization to do a full reset before repopulating.
     *
     * @return true if any data was actually cleared
     */
    public boolean clearAllStorageForDeserialization() {
        boolean changed = false;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null || this.amounts[i] != 0) {
                this.storage[i] = null;
                this.amounts[i] = 0;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Clear all filter slots for deserialization.
     * Used by stream deserialization to do a full reset before repopulating.
     *
     * @return true if any data was actually cleared
     */
    public boolean clearAllFiltersForDeserialization() {
        boolean changed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) {
                this.filters[i] = null;
                changed = true;
            }
        }
        return changed;
    }

    // ============================== Filter map refresh ==============================

    /**
     * Refresh the filter-to-slot mapping and recalculate orphan status for all storage slots.
     * Also rebuilds the storage key map from current storage identities.
     * <p>
     * The filterSlotList is cleared and refilled IN PLACE (not reassigned) so that
     * external aliases pointing to the same List object always have up-to-date data.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();
        this.slotToFilterMap.clear();
        this.filterSlotList.clear();

        for (int i = 0; i < TOTAL_SLOTS; i++) {
            R filter = this.filters[i];
            if (filter == null) continue;

            K key = this.ops.createKey(filter);
            if (key != null) {
                this.filterToSlotMap.put(key, i);
                this.slotToFilterMap.put(i, key);
                this.filterSlotList.add(i);
            }
        }

        // Rebuild storage key map and orphan status from current storage state
        this.storageToSlotMap.clear();
        this.slotToStorageKeyMap.clear();
        this.orphanedSlots.clear();

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            R identity = this.storage[i];
            if (identity == null) continue;

            K storageKey = this.ops.createKey(identity);
            if (storageKey != null) {
                this.storageToSlotMap.put(storageKey, i);
                this.slotToStorageKeyMap.put(i, storageKey);
            }

            recalculateOrphanStatus(i, storageKey);
        }
    }

    // ============================== Storage key management ==============================

    /**
     * Update the storage key map and orphan status for a single slot.
     * Called whenever the storage identity changes.
     *
     * @param slot The storage slot index
     * @param resource The new resource identity, or null if the slot is being cleared
     */
    public void updateStorageKeyForSlot(int slot, @Nullable R resource) {
        // Remove old storage key mapping
        K oldKey = this.slotToStorageKeyMap.remove(slot);
        if (oldKey != null) this.storageToSlotMap.remove(oldKey);

        if (resource != null) {
            K newKey = this.ops.createKey(resource);
            if (newKey != null) {
                this.storageToSlotMap.put(newKey, slot);
                this.slotToStorageKeyMap.put(slot, newKey);
            }

            // Recalculate orphan status: orphaned if filter key doesn't match storage key
            recalculateOrphanStatus(slot, newKey);
        } else {
            // No identity, cannot be orphaned
            this.orphanedSlots.remove(slot);
        }
    }

    /**
     * Recalculate orphan status for a slot.
     * A slot is orphaned when it has a storage identity that doesn't match its filter.
     *
     * @param slot The storage slot index
     * @param storageKey The current storage key (may be null)
     */
    public void recalculateOrphanStatus(int slot, @Nullable K storageKey) {
        if (storageKey == null) {
            this.orphanedSlots.remove(slot);
            return;
        }

        K filterKey = this.slotToFilterMap.get(slot);
        if (filterKey == null || !filterKey.equals(storageKey)) {
            this.orphanedSlots.add(slot);
        } else {
            this.orphanedSlots.remove(slot);
        }
    }

    /**
     * Check if a slot is orphaned (has identity that doesn't match its filter).
     * O(1) lookup using the orphanedSlots set.
     */
    public boolean isOrphanedSlot(int slot) {
        return this.orphanedSlots.contains(slot);
    }

    // ============================== Filter operations ==============================

    /**
     * Get the filter for a slot, converted to AE stack.
     */
    @Nullable
    public AE getFilter(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;

        R filter = this.filters[slot];
        return filter != null ? this.ops.toAEStack(filter) : null;
    }

    /**
     * Set the filter for a slot from an AE stack. Calls onFilterChanged.
     *
     * @param slot The filter slot index
     * @param aeResource The AE-wrapped resource, or null to clear
     */
    public void setFilter(int slot, @Nullable AE aeResource) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        this.filters[slot] = aeResource != null
                ? this.ops.copyWithAmount(this.ops.fromAEStack(aeResource), 1)
                : null;
        onFilterChanged(slot);
    }

    /**
     * Clear filter slots.
     * Import: only clears filters where the corresponding slot is empty.
     * Export: clears all filter slots.
     */
    public void clearFilters() {
        if (this.callbacks.isExport()) {
            for (int i = 0; i < FILTER_SLOTS; i++) this.filters[i] = null;
        } else {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                // Only clear filter if the corresponding storage slot is empty (amount-based,
                // since preserved identities with amount=0 count as empty)
                if (this.amounts[i] <= 0) this.filters[i] = null;
            }
        }

        this.refreshFilterMap();
        this.callbacks.markDirtyAndSave();
        this.callbacks.markForNetworkUpdate();
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots available
     */
    public int findEmptyFilterSlot() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] == null) return i;
        }

        return -1;
    }

    /**
     * Check if a filter with the given key exists.
     * O(1) operation using the internal HashMap.
     */
    public boolean isInFilter(@Nonnull K key) {
        return this.filterToSlotMap.containsKey(key);
    }

    /**
     * Check if a resource is in the filter.
     * Creates a key and delegates to isInFilter(K).
     */
    public boolean isResourceInFilter(@Nullable R resource) {
        if (resource == null) return false;

        K key = this.ops.createKey(resource);
        return key != null && isInFilter(key);
    }

    /**
     * Find the slot index for a given key.
     * @return The slot index, or -1 if not found
     */
    public int findSlotByKey(@Nonnull K key) {
        Integer slot = this.filterToSlotMap.get(key);
        return slot != null ? slot : -1;
    }

    // ============================== Filter NBT merge ==============================

    /**
     * Merge filters from NBT (memory card upload), skipping duplicates.
     * Reports skipped filters to the player as a chat message.
     *
     * @param sourceFilters Array of source filters to merge (may contain nulls)
     * @param player Player to notify about skipped filters, or null
     */
    public void mergeFilters(List<R> sourceFilters, @Nullable EntityPlayer player) {
        List<R> skippedFilters = new ArrayList<>();

        for (R sourceFilter : sourceFilters) {
            if (sourceFilter == null) continue;

            K sourceKey = this.ops.createKey(sourceFilter);
            if (sourceKey == null) continue;

            // Skip if filter already exists in target
            if (this.filterToSlotMap.containsKey(sourceKey)) continue;

            // Find an empty slot
            int targetSlot = findEmptyFilterSlot();
            if (targetSlot < 0) {
                skippedFilters.add(this.ops.copy(sourceFilter));
                continue;
            }

            // Add the filter
            this.filters[targetSlot] = this.ops.copy(sourceFilter);
            this.filterToSlotMap.put(sourceKey, targetSlot);
            this.slotToFilterMap.put(targetSlot, sourceKey);
        }

        this.refreshFilterMap();

        // Notify player about skipped filters
        if (player != null && !skippedFilters.isEmpty()) {
            String filters = skippedFilters.stream()
                .map(this.ops::getLocalizedName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation("message.cells.filters_not_added", skippedFilters.size(), filters));
        }
    }

    // ============================== Filter change handling ==============================

    /**
     * Handle a single filter slot change. Refreshes maps, handles orphan cleanup,
     * returns orphaned resources to the ME network, and clears stale preserved identities.
     *
     * @param slot The slot that changed
     */
    public void onFilterChanged(int slot) {
        // Refresh maps first so orphan status is up to date
        this.refreshFilterMap();

        if (this.callbacks.isExport()) {
            // Export: if filter changed, return orphaned resources in that slot
            if (this.orphanedSlots.contains(slot) && this.amounts[slot] > 0) {
                returnSlotToNetwork(slot);
            }
        }

        // If storage has a preserved identity that no longer matches the new filter,
        // clear it so the slot can be repopulated with the correct identity
        R identity = this.storage[slot];
        if (identity != null && this.amounts[slot] <= 0 && this.orphanedSlots.contains(slot)) {
            this.storage[slot] = null;
            updateStorageKeyForSlot(slot, null);
        }

        this.callbacks.wakeUpIfAdaptive();
        this.callbacks.markDirtyAndSave();
    }

    // ============================== Slot amount helpers ==============================

    /**
     * Get the amount stored in a specific slot.
     * Uses the parallel amounts array for long precision.
     */
    public long getSlotAmount(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        return this.amounts[slot];
    }

    /**
     * Set the amount stored in a specific slot.
     * Uses the parallel amounts array for long precision.
     */
    public void setSlotAmount(int slot, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;
        this.amounts[slot] = amount;
    }

    /**
     * Set the resource and amount in a specific slot.
     * Use it only for OVERWRITING a slot, not for incremental changes.
     * Updates the storage key map and orphan tracking.
     *
     * @param slot The storage slot index
     * @param resource The resource to store (identity only, not amount), or null to clear identity
     * @param amount The amount to store
     */
    public void setResourceInSlotWithAmount(int slot, R resource, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;
        this.storage[slot] = resource;
        this.amounts[slot] = amount;

        updateStorageKeyForSlot(slot, resource);
    }

    /**
     * Same as setResourceInSlotWithAmount(int, R, long) but uses the resource's identity and amount.
     *
     * @param slot The storage slot index
     * @param resource The resource to store (including amount)
     */
    public void setResourceInSlotWithAmount(int slot, R resource) {
        this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), this.ops.getAmount(resource));
    }

    /**
     * Clear a specific slot, zeroing its amount.
     * <p>
     * Identity preservation: when a slot has a matching filter (not orphaned),
     * the identity is kept even when amount reaches 0. This avoids unnecessary
     * copyAsIdentity calls when the slot is refilled with the same resource type.
     * The identity is only fully cleared when:
     * <ul>
     *   <li>No filter is set for the slot, OR</li>
     *   <li>The slot is orphaned (identity doesn't match filter)</li>
     * </ul>
     *
     * @param slot The storage slot index
     */
    public void clearSlot(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        this.amounts[slot] = 0;

        // Keep identity if filter matches (avoids copy on refill)
        if (this.filters[slot] != null && !this.orphanedSlots.contains(slot)) return;

        // No filter or orphaned, fully clear the identity
        this.storage[slot] = null;
        updateStorageKeyForSlot(slot, null);
    }

    /**
     * Adjust the amount stored in a specific slot by a delta value.
     * Used by GUI containers for long-safe insertion/extraction operations.
     * <p>
     * If the resulting amount is <= 0, the slot is cleared (both identity and amount).
     * If the slot was empty (identity null) and delta > 0, this does nothing.
     * Use setResourceInSlot() to place items in empty slots.
     *
     * @param slot  The storage slot index
     * @param delta The amount to add (positive) or subtract (negative)
     * @return The actual amount added/removed (may be clamped to available space/contents)
     */
    public long adjustSlotAmount(int slot, long delta) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;

        R identity = this.storage[slot];

        // Can't adjust an empty slot (use setResourceInSlot instead)
        if (identity == null || this.amounts[slot] <= 0) return 0;

        long currentAmount = this.amounts[slot];
        long newAmount = currentAmount + delta;

        if (newAmount <= 0) {
            // Slot depleted, clearSlot preserves identity if filter matches
            long removed = currentAmount;
            this.clearSlot(slot);
            this.callbacks.markDirtyAndSave();
            return -removed; // Return negative to indicate removal
        }

        // Clamp to max slot size
        if (newAmount > this.maxSlotSize) newAmount = this.maxSlotSize;

        long actualDelta = newAmount - currentAmount;
        this.amounts[slot] = newAmount;
        this.callbacks.markDirtyAndSave();
        return actualDelta;
    }

    /**
     * Create a resource stack from the storage identity with the given amount.
     * Clamps the amount to int for external API compatibility.
     *
     * @param slot The storage slot containing the identity
     * @param amount The amount to apply (will be clamped to Integer.MAX_VALUE)
     * @return The resource with the clamped amount, or null if slot is empty
     */
    @Nullable
    public R createStackFromSlot(int slot, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        if (identity == null) return null;

        int clampedAmount = (int) Math.min(amount, Integer.MAX_VALUE);
        return this.ops.copyWithAmount(identity, clampedAmount);
    }

    // ============================== Slot access ==============================

    public boolean isSlotEmpty(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return true;
        return this.storage[slot] == null || this.amounts[slot] <= 0;
    }

    @Nullable
    public R getResourceInSlot(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        if (identity == null || this.amounts[slot] <= 0) return null;

        // Create a stack with the current amount (clamped to int for external APIs)
        return createStackFromSlot(slot, this.amounts[slot]);
    }

    /**
     * Set the resource in a specific storage slot.
     * Used for GUI-based resource pouring.
     *
     * @param slot The storage slot index
     * @param resource The resource to set (with amount), or null to clear
     */
    public void setResourceInSlot(int slot, @Nullable R resource) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        if (resource == null) {
            this.clearSlot(slot);
        } else {
            this.setResourceInSlotWithAmount(slot, resource);
        }

        // Rebuild filter map since storage changed
        this.refreshFilterMap();

        // Trigger network update to sync storage to clients
        this.callbacks.markForNetworkUpdate();

        // Wake up to process the new content (import will push to network)
        this.callbacks.wakeUpIfAdaptive();
    }

    // ============================== Insert/Drain for external APIs ==============================

    /**
     * Insert resource into a specific storage slot (import interface operation).
     * Uses the parallel amounts array for long precision internally.
     *
     * @return The amount actually inserted (clamped to int for external API compat)
     */
    public int insertIntoSlot(int slot, R resource) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        if (resource == null || this.ops.getAmount(resource) <= 0) return 0;

        R identity = this.storage[slot];

        // Use cached storage key for O(1) matching instead of resourcesMatch
        if (identity != null) {
            K storageKey = this.slotToStorageKeyMap.get(slot);
            K incomingKey = this.ops.createKey(resource);
            if (storageKey == null || !storageKey.equals(incomingKey)) return 0;
        }

        long currentAmount = this.amounts[slot];
        long spaceAvailable = this.maxSlotSize - currentAmount;
        if (spaceAvailable <= 0) return 0;

        // Input amount is int (from external APIs), but we store as long
        int inputAmount = this.ops.getAmount(resource);
        long toInsert = Math.min(inputAmount, spaceAvailable);

        // Identity may already be preserved from a previous clearSlot (filter match).
        // Only set identity if truly null (first ever use or after orphan clear).
        if (identity == null) {
            this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), toInsert);
        } else {
            this.amounts[slot] += toInsert;
        }

        this.callbacks.markDirtyAndSave();
        this.callbacks.markForNetworkUpdate();
        this.callbacks.wakeUpIfAdaptive();

        // Return int for external API compatibility (always fits since input was int)
        return (int) toInsert;
    }

    /**
     * Internal insert with long amount support.
     * Used for ME network operations that use long amounts.
     *
     * @return The amount actually inserted (as long)
     */
    public long insertIntoSlotLong(int slot, R resource, long amount) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return 0;
        if (resource == null || amount <= 0) return 0;

        R identity = this.storage[slot];

        // Use cached storage key for O(1) matching
        if (identity != null) {
            K storageKey = this.slotToStorageKeyMap.get(slot);
            K incomingKey = this.ops.createKey(resource);
            if (storageKey == null || !storageKey.equals(incomingKey)) return 0;
        }

        long currentAmount = this.amounts[slot];
        long spaceAvailable = this.maxSlotSize - currentAmount;
        if (spaceAvailable <= 0) return 0;

        long toInsert = Math.min(amount, spaceAvailable);

        // Identity may already be preserved from a previous clearSlot (filter match).
        if (identity == null) {
            this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), toInsert);
        } else {
            this.amounts[slot] += toInsert;
        }

        this.callbacks.markDirtyAndSave();
        this.callbacks.markForNetworkUpdate();
        this.callbacks.wakeUpIfAdaptive();

        return toInsert;
    }

    /**
     * Drain resource from a specific storage slot (export interface operation).
     *
     * @param maxDrain Maximum amount to drain (int for external API compat)
     * @return The resource extracted (clamped to int), or null if nothing extracted
     */
    @Nullable
    public R drainFromSlot(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlotLong(slot, maxDrain, doDrain);
    }

    /**
     * Internal drain with long amount support.
     *
     * @param maxDrain Maximum amount to drain (as long)
     * @return The resource extracted (clamped to int for external APIs), or null if nothing extracted
     */
    @Nullable
    public R drainFromSlotLong(int slot, long maxDrain, boolean doDrain) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;

        R identity = this.storage[slot];
        long currentAmount = this.amounts[slot];
        if (identity == null || currentAmount <= 0) return null;

        long toDrain = Math.min(maxDrain, currentAmount);

        // Clamp to int for external API return (create stack with int amount)
        int clampedDrain = (int) Math.min(toDrain, Integer.MAX_VALUE);
        R drained = this.ops.copyWithAmount(identity, clampedDrain);

        if (doDrain) {
            this.amounts[slot] -= toDrain;
            if (this.amounts[slot] <= 0) this.clearSlot(slot);

            this.callbacks.markDirtyAndSave();
            this.callbacks.markForNetworkUpdate();

            // Wake up to request more resources (export replenishes)
            this.callbacks.wakeUpIfAdaptive();
        }

        return drained;
    }

    // ============================== Filtered receive/drain (external handler API) ==============================

    /**
     * Receive a resource into this interface based on filter configuration.
     * Used by import interface external handlers.
     *
     * @param resource The resource to receive
     * @param doTransfer If true, actually perform the transfer; if false, simulate
     * @param hasOverflowUpgrade Whether overflow upgrade is installed
     * @param hasTrashUnselectedUpgrade Whether trash unselected upgrade is installed
     * @return The amount accepted (may include voided amounts if upgrades are installed)
     */
    public int receiveFiltered(R resource, boolean doTransfer,
                               boolean hasOverflowUpgrade, boolean hasTrashUnselectedUpgrade) {
        if (resource == null || this.ops.getAmount(resource) <= 0) return 0;

        K key = this.ops.createKey(resource);
        if (key == null) return 0;

        int inputAmount = this.ops.getAmount(resource);

        // No filter matches - void if trash unselected upgrade installed, reject otherwise
        Integer slot = this.filterToSlotMap.get(key);
        if (slot == null) return hasTrashUnselectedUpgrade ? inputAmount : 0;

        // Insert into matching slot using parallel amounts array
        long currentAmount = this.amounts[slot];
        long space = this.maxSlotSize - currentAmount;

        // Slot full - void overflow if upgrade installed, reject otherwise
        if (space <= 0) return hasOverflowUpgrade ? inputAmount : 0;

        // Input is int, but we can accept up to maxSlotSize (long)
        long toInsert = Math.min(inputAmount, space);

        if (doTransfer) {
            // Identity may already be preserved from a previous clearSlot (filter match).
            if (this.storage[slot] == null) {
                this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), toInsert);
            } else {
                this.amounts[slot] += toInsert;
            }

            this.callbacks.markDirtyAndSave();
            this.callbacks.markForNetworkUpdate();

            // Wake up to import resources
            this.callbacks.wakeUpIfAdaptive();
        }

        // If overflow upgrade installed, accept all resource (void the excess)
        int excess = inputAmount - (int) toInsert;
        if (excess > 0 && hasOverflowUpgrade) return inputAmount;

        return (int) toInsert;
    }

    /**
     * Drain any available resource from this interface.
     * Used by export interface external handlers for untyped drain requests.
     */
    @Nullable
    public R drainAny(int maxDrain, boolean doDrain) {
        // Drain from first non-empty slot
        for (int slot : this.filterSlotList) {
            if (this.storage[slot] != null && this.amounts[slot] > 0) {
                return drainFromSlot(slot, maxDrain, doDrain);
            }
        }

        return null;
    }

    /**
     * Drain a specific resource from this interface.
     * Used by export interface external handlers for typed drain requests.
     */
    @Nullable
    public R drainSpecific(R request, boolean doDrain) {
        if (request == null || this.ops.getAmount(request) <= 0) return null;

        K key = this.ops.createKey(request);
        if (key == null) return null;

        Integer slot = this.filterToSlotMap.get(key);
        if (slot == null) return null;

        return drainFromSlot(slot, this.ops.getAmount(request), doDrain);
    }

    /**
     * Check if this interface can receive the given resource type.
     * Used by import interface external handlers for capability queries.
     */
    public boolean canReceive(R resource) {
        if (resource == null) return false;

        K key = this.ops.createKey(resource);
        return key != null && this.filterToSlotMap.containsKey(key);
    }

    /**
     * Check if this interface can drain the given resource type.
     * Used by export interface external handlers for capability queries.
     */
    public boolean canDrain(R resource) {
        // Same logic as canReceive - we can drain what we have filters for
        return canReceive(resource);
    }

    // ============================== Capacity reduction ==============================

    /**
     * Handle capacity reduction by clearing filters and returning resources from removed pages.
     * Called when capacity cards are removed.
     *
     * @param oldCount Old number of capacity cards
     * @param newCount New number of capacity cards
     */
    public void handleCapacityReduction(int oldCount, int newCount) {
        int newMaxSlot = (1 + newCount) * AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;
        int oldMaxSlot = (1 + oldCount) * AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;
        oldMaxSlot = Math.min(oldMaxSlot, STORAGE_SLOTS);

        World world = this.callbacks.getHostWorld();
        BlockPos pos = this.callbacks.getHostPos();

        // Process slots that are being removed
        for (int slot = newMaxSlot; slot < oldMaxSlot; slot++) {
            // Clear the filter
            this.filters[slot] = null;

            // Return resource to network
            R identity = this.storage[slot];
            long amount = this.amounts[slot];
            if (identity != null && amount > 0) {
                long notInserted = insertIntoNetwork(identity, amount);

                // Create a single recovery item for remainder (supports long amounts)
                if (notInserted > 0) {
                    ItemStack drop = this.ops.createRecoveryItem(identity, notInserted);
                    if (!drop.isEmpty() && world != null && pos != null) {
                        Block.spawnAsEntity(world, pos, drop);
                    }
                    // If no recovery item available, remainder is voided
                    // This should never happen as we handle every type
                }
            }

            // Force-clear slot (including any preserved identity with amount=0)
            this.storage[slot] = null;
            this.amounts[slot] = 0;
            updateStorageKeyForSlot(slot, null);
        }

        this.refreshFilterMap();
        this.callbacks.markDirtyAndSave();
    }

    // ============================== ME network operations ==============================

    /**
     * Check if there's work to do based on direction.
     */
    public boolean hasWorkToDo() {
        if (this.callbacks.isExport()) {
            // Check if any configured slot needs resources
            for (int i : this.filterSlotList) {
                if (this.amounts[i] < this.maxSlotSize) return true;
            }
        } else {
            // Check if any filtered slot has resources to import
            for (int i : this.filterToSlotMap.values()) {
                if (this.storage[i] != null && this.amounts[i] > 0) return true;
            }
        }

        return false;
    }

    /**
     * Import resources from internal storage into the ME network.
     * Uses the parallel amounts array for long precision.
     */
    public boolean importResources() {
        boolean didWork = false;

        try {
            IStorageGrid storageGrid = this.callbacks.getGridProxy().getStorage();
            IMEInventory<AE> inventory = this.ops.getMEInventory(storageGrid);

            for (int slot : this.filterToSlotMap.values()) {
                R identity = this.storage[slot];
                long amount = this.amounts[slot];
                if (identity == null || amount <= 0) continue;

                // Create AE stack with identity only - setStackSize will set the actual amount
                AE aeStack = this.ops.toAEStack(this.ops.copyWithAmount(identity, 1));
                aeStack.setStackSize(Math.min(amount, this.callbacks.getMaxAENetworkRequestSize()));

                AE remaining = inventory.injectItems(aeStack, Actionable.MODULATE, this.callbacks.getActionSource());

                if (remaining == null) {
                    this.clearSlot(slot);
                    didWork = true;
                } else if (this.ops.getAEStackSize(remaining) < amount) {
                    this.amounts[slot] = this.ops.getAEStackSize(remaining);
                    didWork = true;
                }
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.callbacks.markForNetworkUpdate();

        return didWork;
    }

    /**
     * Export resources from the ME network into storage slots.
     * Uses the parallel amounts array for long precision.
     */
    public boolean exportResources() {
        boolean didWork = false;

        try {
            IStorageGrid storageGrid = this.callbacks.getGridProxy().getStorage();
            IMEInventory<AE> inventory = this.ops.getMEInventory(storageGrid);

            // First, return any orphaned or overflow resources to the network
            returnOrphanedToNetwork();
            returnOverflowToNetwork();

            for (int slot : this.filterSlotList) {
                R filter = this.filters[slot];
                if (filter == null) continue;

                R identity = this.storage[slot];
                long currentAmount = this.amounts[slot];

                // Skip orphaned slots (identity doesn't match filter)
                if (this.orphanedSlots.contains(slot)) continue;

                long space = this.maxSlotSize - currentAmount;
                if (space <= 0) continue;

                // Request resources from network (AE2 uses long natively)
                AE request = this.ops.toAEStack(this.ops.copyWithAmount(filter, 1));
                request.setStackSize(Math.min(space, this.callbacks.getMaxAENetworkRequestSize()));

                AE extracted = inventory.extractItems(request, Actionable.MODULATE, this.callbacks.getActionSource());
                if (extracted == null || this.ops.getAEStackSize(extracted) <= 0) continue;

                long extractedAmount = this.ops.getAEStackSize(extracted);

                // Identity may already be preserved from a previous clearSlot (filter match).
                if (identity == null) {
                    this.setResourceInSlotWithAmount(slot,
                            this.ops.copyAsIdentity(this.ops.fromAEStack(extracted)), extractedAmount);
                } else {
                    this.amounts[slot] += extractedAmount;
                }

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        if (didWork) this.callbacks.markForNetworkUpdate();

        return didWork;
    }

    /**
     * Insert resources into the ME network with long amount support.
     *
     * @param identity The resource identity (type/NBT)
     * @param amount The amount to insert (long)
     * @return Amount that could not be inserted (long)
     */
    public long insertIntoNetwork(R identity, long amount) {
        if (identity == null || amount <= 0) return 0;

        try {
            IStorageGrid storage = this.callbacks.getGridProxy().getStorage();
            IMEInventory<AE> inventory = this.ops.getMEInventory(storage);

            // Create AE stack with full long amount
            AE aeStack = this.ops.toAEStack(this.ops.copyWithAmount(identity, 1));
            aeStack.setStackSize(amount);

            AE remaining = inventory.injectItems(aeStack, Actionable.MODULATE, this.callbacks.getActionSource());

            if (remaining == null) return 0;

            return this.ops.getAEStackSize(remaining);
        } catch (GridAccessException e) {
            return amount;
        }
    }

    /**
     * Return a slot's contents to the network.
     */
    public void returnSlotToNetwork(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        R identity = this.storage[slot];
        long amount = this.amounts[slot];
        if (identity == null || amount <= 0) return;

        long notInserted = insertIntoNetwork(identity, amount);

        if (notInserted <= 0) {
            this.clearSlot(slot);
        } else {
            this.amounts[slot] = notInserted;
        }

        this.callbacks.markForNetworkUpdate();
    }

    /**
     * Return orphaned resources (no matching filter) to the network.
     * Uses the pre-computed orphanedSlots set for O(1) per-slot lookup.
     */
    public void returnOrphanedToNetwork() {
        if (this.orphanedSlots.isEmpty()) return;

        // Iterate a copy to avoid ConcurrentModificationException (clearSlot modifies orphanedSlots)
        for (int slot : new ArrayList<>(this.orphanedSlots)) {
            R identity = this.storage[slot];
            if (identity == null || this.amounts[slot] <= 0) continue;

            // Orphaned, return to network
            returnSlotToNetwork(slot);
        }
    }

    /**
     * Return overflow resources (above maxSlotSize) to the network.
     */
    public void returnOverflowToNetwork() {
        boolean didWork = false;

        for (int slot : this.getNonEmptyStorageSlots()) {
            R identity = this.storage[slot];
            long amount = this.amounts[slot];
            if (amount <= this.maxSlotSize) continue;

            // Has overflow - return excess to network
            long overflow = amount - this.maxSlotSize;
            long notInserted = insertIntoNetwork(identity, overflow);

            // Reduce slot by what was successfully returned
            long returned = overflow - notInserted;
            if (returned > 0) {
                this.amounts[slot] -= returned;
                didWork = true;
            }
        }

        if (didWork) this.callbacks.markForNetworkUpdate();
    }

    // ============================== Drop collection ==============================

    /**
     * Collect stored resources that should be dropped (not upgrades).
     * Attempts to return stored resources to the ME network first.
     * Creates recovery items for any resources that couldn't be returned.
     *
     * @param drops List to add drops to
     */
    public void getStorageDrops(List<ItemStack> drops) {
        // Try to return all storage contents to the network
        for (int slot : this.getNonEmptyStorageSlots()) {
            R identity = this.storage[slot];

            // Try to insert into network
            long notInserted = insertIntoNetwork(identity, this.amounts[slot]);

            // Create a single recovery item for the remainder (supports long amounts)
            if (notInserted > 0) {
                ItemStack drop = this.ops.createRecoveryItem(identity, notInserted);
                if (!drop.isEmpty()) drops.add(drop);
            }

            // Clear the slot regardless
            this.clearSlot(slot);
        }
    }

    // ============================== NBT serialization ==============================

    /**
     * Read max slot size from NBT.
     * Sets the field directly without triggering markDirtyAndSave or overflow returns,
     * since the host/world may not be initialized yet during chunk loading (NPE on parts).
     */
    public void readFromNBT(NBTTagCompound data) {
        if (data.hasKey("maxSlotSize")) {
            this.maxSlotSize = this.validateMaxSlotSize(data.getLong("maxSlotSize"));
        }
    }

    /**
     * Write max slot size to NBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        data.setLong("maxSlotSize", this.maxSlotSize);  
    }

    /**
     * Write filter data to NBT.
     */
    public void writeFiltersToNBT(NBTTagCompound data, String name) {
        NBTTagCompound filtersMap = new NBTTagCompound();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            R filter = this.filters[i];
            if (filter == null) continue;

            NBTTagCompound filterTag = new NBTTagCompound();
            this.ops.writeResourceToNBT(filter, filterTag);
            filtersMap.setTag("#" + i, filterTag);
        }
        data.setTag(name, filtersMap);
    }

    /**
     * Read filter data from NBT.
     */
    public void readFiltersFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound filtersMap = data.getCompoundTag(name);
        for (String key : filtersMap.getKeySet()) {
            if (!key.startsWith("#")) continue;

            try {
                int slot = Integer.parseInt(key.substring(1));
                if (slot < 0 || slot >= FILTER_SLOTS) continue;

                NBTTagCompound filterTag = filtersMap.getCompoundTag(key);
                if (filterTag.isEmpty()) continue;

                R filter = this.ops.readResourceFromNBT(filterTag);
                if (filter != null) this.filters[slot] = filter;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Write storage data to NBT. Writes both identity and amounts.
     */
    public void writeStorageToNBT(NBTTagCompound data, String storageKey) {
        NBTTagCompound storageMap = new NBTTagCompound();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] == null || this.amounts[i] <= 0) continue;

            NBTTagCompound slotTag = new NBTTagCompound();
            this.ops.writeResourceToNBT(this.storage[i], slotTag);
            // Write amount as long for full long precision
            slotTag.setLong("Amount", this.amounts[i]);
            storageMap.setTag(String.valueOf(i), slotTag);
        }
        data.setTag(storageKey, storageMap);
    }

    /**
     * Read storage data from NBT. Override in subclass for type-specific migration.
     * Reads both identity and amounts from NBT, supporting long amounts.
     */
    public void readStorageFromNBT(NBTTagCompound data, String storageKey) {
        if (!data.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound storageMap = data.getCompoundTag(storageKey);
        for (String key : storageMap.getKeySet()) {
            try {
                int slot = Integer.parseInt(key);
                if (slot < 0 || slot >= STORAGE_SLOTS) continue;

                NBTTagCompound slotTag = storageMap.getCompoundTag(key);
                R resource = this.ops.readResourceFromNBT(slotTag);
                if (resource == null) continue;

                // Read amount: prefer "Amount" (long), fall back to resource's native int amount
                long amount;
                if (slotTag.hasKey("Amount")) {
                    amount = slotTag.getLong("Amount");
                } else {
                    // Legacy migration: use the resource's native int amount
                    amount = this.ops.getAmount(resource);
                }

                this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), amount);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ============================== Stream serialization ==============================

    /**
     * Read storage data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readStorageFromStream(ByteBuf data) {
        boolean changed = this.clearAllStorageForDeserialization();

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            long amount = data.readLong();

            R resource = this.ops.readResourceFromStream(data);
            this.setResourceInSlotWithAmount(slot, this.ops.copyAsIdentity(resource), amount);

            changed = true;
        }

        return changed;
    }

    /**
     * Write storage data to a ByteBuf stream for client sync.
     * Only iterates occupied slots, via slotToStorageKeyMap.
     */
    public void writeStorageToStream(ByteBuf data) {
        // getNonEmptyStorageSlots returns non-null, >0 slots
        List<Integer> slots = this.getNonEmptyStorageSlots();

        data.writeShort(slots.size());

        for (int slot : slots) {
            data.writeShort(slot);
            data.writeLong(this.amounts[slot]);
            this.ops.writeResourceToStream(this.storage[slot], data);
        }
    }

    /**
     * Read filter data from a ByteBuf stream for client sync.
     * @return true if any data changed
     */
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = this.clearAllFiltersForDeserialization();

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();

            R resource = this.ops.readResourceFromStream(data);
            this.filters[slot] = resource;

            changed = true;
        }

        this.refreshFilterMap();
        return changed;
    }

    /**
     * Write filter data to a ByteBuf stream for client sync.
     * Uses filterSlotList to iterate only occupied filter slots.
     */
    public void writeFiltersToStream(ByteBuf data) {
        // filterSlotList contains exactly the slots with non-null filters (maintained by refreshFilterMap)
        data.writeShort(this.filterSlotList.size());

        for (int slot : this.filterSlotList) {
            data.writeShort(slot);
            this.ops.writeResourceToStream(this.filters[slot], data);
        }
    }
}
