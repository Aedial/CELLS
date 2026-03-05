package com.cells.blocks.exportinterface;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;

import com.cells.blocks.importinterface.IImportInterfaceHost;


/**
 * Extended interface for Export Interface hosts (both tile and part).
 * Provides access to inventories and configuration for the container/GUI.
 */
public interface IExportInterfaceInventoryHost extends IImportInterfaceHost {

    /**
     * @return The filter inventory (ghost items)
     */
    AppEngInternalInventory getFilterInventory();

    /**
     * @return The storage inventory (actual items)
     */
    AppEngInternalInventory getStorageInventory();

    /**
     * @return The upgrade inventory
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Refresh the filter map after changes.
     */
    void refreshFilterMap();

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * Refresh upgrade status cache.
     */
    void refreshUpgrades();

    /**
     * @return The maximum number of items allowed per slot.
     */
    int getMaxSlotSize();

    /**
     * Set the maximum number of items allowed per slot.
     */
    void setMaxSlotSize(int size);

    /**
     * @return The polling rate in ticks.
     */
    int getPollingRate();

    /**
     * Set the polling rate in ticks.
     */
    void setPollingRate(int ticks);

    /**
     * Clear all filters and return all storage items to the network.
     */
    void clearFilters();

    /**
     * @return The block position of this host (tile position or part's host tile position).
     */
    BlockPos getHostPos();

    /**
     * @return The settings of this host as an NBTTagCompound, for saving to memory cards or other uses.
     */
    public NBTTagCompound downloadSettings(SettingsFrom from);

    // Pagination support

    /**
     * @return The number of installed capacity upgrades.
     */
    int getInstalledCapacityUpgrades();

    /**
     * @return Total number of pages (1 base + 1 per capacity card).
     */
    int getTotalPages();

    /**
     * @return The current page index (0-based).
     */
    int getCurrentPage();

    /**
     * Set the current page index (0-based), clamped to valid range.
     */
    void setCurrentPage(int page);

    /**
     * @return The starting slot index for the current page.
     */
    int getCurrentPageStartSlot();
}
