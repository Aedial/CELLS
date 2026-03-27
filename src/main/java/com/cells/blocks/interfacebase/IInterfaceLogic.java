package com.cells.blocks.interfacebase;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer ;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

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
     * @return Maximum fluid/item amount per slot.
     */
    int getMaxSlotSize();

    /**
     * Set the maximum fluid/item amount per slot.
     */
    void setMaxSlotSize(int size);

    /**
     * @return Polling rate in ticks.
     */
    int getPollingRate();

    /**
     * Set the polling rate in ticks.
     */
    void setPollingRate(int ticks);

    // ================================= Pagination =================================

    /**
     * @return Number of capacity upgrades currently installed.
     */
    int getInstalledCapacityUpgrades();

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
     * @return The starting slot index for the current page.
     */
    int getCurrentPageStartSlot();

    /**
     * @return Number of slots per page.
     */
    int getSlotsPerPage();

    /**
     * @return Total number of filter slots (across all pages).
     */
    int getFilterSlots();

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
     * Refresh cached upgrade state after upgrade slot changes.
     */
    void refreshUpgrades();

    /**
     * Handle upgrade inventory changes - refreshes upgrades and marks dirty.
     */
    void onUpgradeChanged();

    /**
     * Get the upgrade inventory for this interface.
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * @return true if the overflow upgrade is installed (import only).
     */
    boolean hasOverflowUpgrade();

    /**
     * @return true if the trash unselected upgrade is installed (import only).
     */
    boolean hasTrashUnselectedUpgrade();

    // ================================= Wake Logic =================================

    /**
     * Set polling rate with player feedback.
     */
    void setPollingRate(int ticks, EntityPlayer player);

    /**
     * Wake up the interface if it's in adaptive polling mode.
     * Called on network events to ensure timely processing.
     */
    void wakeUpIfAdaptive();

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
     */
    TickRateModulation onTick();

    /**
     * Get the type name for localization (e.g., "item", "fluid", "gas").
     */
    String getTypeName();
}
