package com.cells.blocks.interfacebase;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.IAEFluidTank;
import appeng.tile.inventory.AppEngInternalInventory;


/**
 * Extended interface for Fluid Interface hosts (both import and export, both tile and part).
 * Provides access to fluid filter/tank/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 */
public interface IFluidInterfaceHost extends IInterfaceHost {

    /**
     * @return The filter inventory (fluid filters)
     */
    IAEFluidTank getFilterInventory();

    /**
     * @return The upgrade inventory
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Refresh the filter-to-slot map after filter changes.
     */
    void refreshFilterMap();

    /**
     * Refresh cached upgrade status after upgrade slot changes.
     */
    void refreshUpgrades();

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * Clear filter slots. Import only clears filters where the corresponding
     * tank is empty (to prevent orphaning fluids). Export clears all filters.
     */
    void clearFilters();

    /**
     * @return The world this host is in.
     */
    World getHostWorld();

    // Fluid tank access

    /**
     * Check if a specific tank slot is empty.
     */
    boolean isTankEmpty(int slot);

    /**
     * Get the filter fluid for a specific slot.
     */
    IAEFluidStack getFilterFluid(int slot);

    /**
     * Set the filter fluid for a specific slot.
     */
    void setFilterFluid(int slot, IAEFluidStack fluid);

    /**
     * Get the fluid currently in a tank slot.
     */
    FluidStack getFluidInTank(int slot);

    // Pagination support

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

    // Import-specific upgrades (return false for export interfaces)

    /**
     * @return true if the overflow upgrade is installed (import only).
     */
    default boolean hasOverflowUpgrade() {
        return false;
    }

    /**
     * @return true if the trash unselected upgrade is installed (import only).
     */
    default boolean hasTrashUnselectedUpgrade() {
        return false;
    }

    // Direction-specific tank operations (called by handlers internally)

    /**
     * Insert fluid into a tank slot (import interfaces only).
     *
     * @param slot The tank slot index
     * @param fluid The fluid to insert
     * @return The amount actually inserted
     */
    default int insertFluidIntoTank(int slot, FluidStack fluid) {
        throw new UnsupportedOperationException("insertFluidIntoTank is only supported on import interfaces");
    }

    /**
     * Extract fluid from a tank slot (export interfaces only).
     *
     * @param slot The tank slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The fluid extracted, or null if nothing extracted
     */
    default FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        throw new UnsupportedOperationException("drainFluidFromTank is only supported on export interfaces");
    }
}
