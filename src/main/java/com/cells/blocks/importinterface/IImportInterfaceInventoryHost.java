package com.cells.blocks.importinterface;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;


/**
 * Extended interface for Import Interface hosts (both tile and part).
 * Provides access to inventories and configuration for the container/GUI.
 */
public interface IImportInterfaceInventoryHost extends IImportInterfaceHost {

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
     * Check if an item is valid for a specific storage slot based on the filter.
     */
    boolean isItemValidForSlot(int slot, ItemStack stack);

    /**
     * Refresh the filter map after changes.
     */
    void refreshFilterMap();

    /**
     * Check if the overflow upgrade is installed.
     */
    boolean hasOverflowUpgrade();

    /**
     * Check if the trash unselected upgrade is installed.
     */
    boolean hasTrashUnselectedUpgrade();

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
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * @return The block position of this host (tile position or part's host tile position).
     */
    BlockPos getHostPos();

    /**
     * @return The settings of this host as an NBTTagCompound, for saving to memory cards or other uses.
     */
    public NBTTagCompound downloadSettings(SettingsFrom from);
}
