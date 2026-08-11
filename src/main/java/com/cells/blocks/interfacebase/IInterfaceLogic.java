package com.cells.blocks.interfacebase;

import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.ticking.TickingRequest;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.tile.inventory.AppEngInternalInventory;


/**
 * Common interface for interface logic classes (Item, Fluid, Gas, Essentia).
 * Provides the shared contract for pagination, filter operations, and configuration
 * that all logic implementations must support.
 */
public interface IInterfaceLogic {

    // ================================= Configuration =================================

    /**
     * Validate the max slot size value, clamping to a valid range if necessary.
     * @return The validated max slot size.
     */
    long validateMaxSlotSize(long size);

    /**
     * @return Maximum fluid/item amount per slot.
     */
    long getMaxSlotSize();

    /**
     * Set the maximum fluid/item amount per slot.
     * @return The new max slot size after clamping to valid range.
     */
    long setMaxSlotSize(long size);

    // ================================= Per-Slot Size Overrides =================================

    /**
     * Get the effective size for a specific slot.
     * Returns the per-slot override if set, otherwise the global maxSlotSize.
     */
    long getEffectiveMaxSlotSize(int slot);

    /**
     * Set a per-slot size override.
     * @return The validated override size.
     */
    long setMaxSlotSizeOverride(int slot, long size);

    /**
     * Get the per-slot size override, or -1 if no override is set.
     */
    long getMaxSlotSizeOverride(int slot);

    /**
     * Clear the per-slot size override, reverting to the global maxSlotSize.
     */
    void clearMaxSlotSizeOverride(int slot);

    /**
     * Get an unmodifiable view of all per-slot size overrides.
     * Used for container sync.
     */
    Map<Integer, Long> getmaxSlotSizeOverrides();

    /**
     * @return Polling rate in ticks.
     */
    int getPollingRate();

    /**
     * Set the polling rate in ticks.
     * @return The new polling rate after clamping to valid range.
     */
    int setPollingRate(int ticks);

    // ================================= Pagination =================================

    /**
     * @return Total number of pages (1 base + 1 per capacity card).
     */
    int getTotalPages();

    /**
     * @return Current page index (0-based).
     */
    int getCurrentPage();

    /**
     * Set the current page index, clamped to valid range.
     */
    void setCurrentPage(int page);

    /**
     * @return Number of effective filter slots based on installed capacity upgrades.
     */
    int getEffectiveFilterSlots();

    // ================================= Filter Operations =================================

    /**
     * Clear all filters. Import only clears filters where the corresponding
     * storage slot is empty (to prevent orphaning items). Export clears all filters.
     */
    void clearFilters();

    /**
     * Rebuild the internal filter cache (key -> slot map).
     * Should be called after any direct filter modifications.
     */
    void refreshFilterMap();

    // ================================= Upgrades =================================

    /**
     * Get the upgrade inventory.
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Refresh cached upgrade state after upgrade slot changes.
     */
    void refreshUpgrades();

    /**
     * Handle upgrade inventory changes - refreshes upgrades and marks dirty.
     */
    void onChangeInventory(IItemHandler inv, int slot, ItemStack removed, ItemStack added);

    // ================================= Wake Logic =================================

    /**
     * Set polling rate with player feedback.
     */
    int setPollingRate(int ticks, EntityPlayer player);

    /**
     * Wake up the interface if it's in adaptive polling mode.
     * Called on network events to ensure timely processing.
     */
    void wakeUpIfAdaptive();

    /**
     * Run one immediate polling cycle for all available tabs, including Pull/Push Card operations.
     * This manually triggers the same resource movement path the scheduler would
     * normally perform on a later tick.
     *
     * @return true when the cycle moved any resources
     */
    boolean triggerImmediatePollingAction();

    /**
     * Called when the grid proxy becomes ready (after onReady/addToWorld).
     * Re-scans the adjacent capability cache and re-registers tick rate.
     * <p>
     * During readFromNBT, adjacent TEs may not yet be loaded (chunk load order is
     * non-deterministic), so the initial capability scan may find nothing despite
     * neighbors being present. This re-scan runs after all TEs are in the world,
     * ensuring the push/pull card detects adjacent containers correctly.
     */
    default void onGridReady() { }

    // ================================= NBT/Stream Serialization =================================

    /**
     * Read logic state from NBT.
     *
     * @param data The NBT compound to read from
     */
    void readFromNBT(NBTTagCompound data);

    /**
     * Read logic state from NBT with tile/part distinction.
     * Default implementation ignores isTile and delegates to {@link #readFromNBT(NBTTagCompound)}.
     * Override in subclasses that need legacy format support (e.g., ItemInterfaceLogic).
     *
     * @param data The NBT compound to read from
     * @param isTile Whether this is being read by a tile (vs a part)
     */
    default void readFromNBT(NBTTagCompound data, boolean isTile) {
        readFromNBT(data);
    }

    /**
     * Write logic state to NBT.
     */
    void writeToNBT(NBTTagCompound data);

    /**
     * Download settings for memory card or NEI recipe.
     */
    NBTTagCompound downloadSettings();

    /**
     * Download settings specifically for dismantling (includes upgrade contents).
     */
    NBTTagCompound downloadSettingsForDismantle();

    /**
     * Download settings including filter data.
     */
    NBTTagCompound downloadSettingsWithFilter();

    /**
     * Upload settings from memory card.
     */
    void uploadSettings(NBTTagCompound compound, EntityPlayer player);

    /**
     * Read storage contents from stream for client sync.
     */
    boolean readStorageFromStream(ByteBuf data);

    /**
     * Write storage contents to stream for client sync.
     */
    void writeStorageToStream(ByteBuf data);

    /**
     * Read filter contents from stream for client sync.
     */
    boolean readFiltersFromStream(ByteBuf data);

    /**
     * Write filter contents to stream for client sync.
     */
    void writeFiltersToStream(ByteBuf data);

    // ================================= Drops =================================

    /**
     * Get items to drop when the interface is broken.
     */
    void getDrops(List<ItemStack> drops);

    /**
     * Get only storage items to drop (for wrenching, where upgrades are saved to NBT).
     */
    void getStorageDrops(List<ItemStack> drops);

    // ================================= Ticking =================================

    /**
     * Get the ticking request for this logic.
     */
    TickingRequest getTickingRequest();

    /**
     * Process a tick.
     *
     * @param ticksSinceLastCall Number of ticks elapsed since the last call (from AE2 tick manager)
     */
    TickRateModulation onTick(int ticksSinceLastCall);

    /**
     * Get the type name for localization (e.g., "item", "fluid", "gas").
     */
    String getTypeName();
}
