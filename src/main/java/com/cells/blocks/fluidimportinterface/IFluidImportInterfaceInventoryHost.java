package com.cells.blocks.fluidimportinterface;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.IAEFluidTank;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;

import com.cells.blocks.importinterface.IImportInterfaceHost;


/**
 * Extended interface for Fluid Import Interface hosts (both tile and part).
 * Provides access to inventories and configuration for the container/GUI.
 */
public interface IFluidImportInterfaceInventoryHost extends IImportInterfaceHost {

    /**
     * @return The filter inventory (fluid filters)
     */
    IAEFluidTank getFilterInventory();

    /**
     * @return The upgrade inventory
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Refresh upgrade status cache.
     */
    void refreshUpgrades();

    /**
     * @return The maximum fluid amount allowed per tank.
     */
    int getMaxSlotSize();

    /**
     * Set the maximum fluid amount allowed per tank.
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
     * @return The world this host is in.
     */
    World getHostWorld();

    /**
     * Check if a specific tank slot is empty.
     */
    boolean isTankEmpty(int slot);

    /**
     * Set the filter fluid for a specific slot.
     */
    void setFilterFluid(int slot, IAEFluidStack fluid);

    /**
     * Get the filter fluid for a specific slot.
     */
    IAEFluidStack getFilterFluid(int slot);

    /**
     * Get the fluid currently in a tank slot.
     */
    FluidStack getFluidInTank(int slot);

    /**
     * Insert fluid into a tank slot.
     *
     * @param slot The tank slot index
     * @param fluid The fluid to insert
     * @return The amount actually inserted
     */
    int insertFluidIntoTank(int slot, FluidStack fluid);

    /**
     * Refresh the filter-to-slot map. Should be called after filter changes.
     */
    void refreshFilterMap();

    /**
     * Clear all filter slots. For Import interfaces, only clears slots where
     * the corresponding tank is empty (to prevent orphaning fluids).
     */
    void clearFilters();

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
    * @return The settings of this host as an NBTTagCompound, for saving to memory cards or other uses.
    */
    public NBTTagCompound downloadSettings(SettingsFrom from);
}
