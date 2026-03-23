package com.cells.integration.mekanismenergistics;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import mekanism.api.gas.GasStack;

import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.blocks.interfacebase.IInterfaceHost;
import com.mekeng.github.common.me.data.IAEGasStack;


/**
 * Extended interface for Gas Interface hosts (both import and export, both tile and part).
 * Provides access to gas filter/tank/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 */
public interface IGasInterfaceHost extends IInterfaceHost {

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
     * tank is empty (to prevent orphaning gases). Export clears all filters.
     */
    void clearFilters();

    /**
     * @return The world this host is in.
     */
    World getHostWorld();

    // Gas tank access

    /**
     * Check if a specific tank slot is empty.
     */
    boolean isTankEmpty(int slot);

    /**
     * Get the filter gas for a specific slot.
     */
    IAEGasStack getFilterGas(int slot);

    /**
     * Set the filter gas for a specific slot.
     */
    void setFilterGas(int slot, IAEGasStack gas);

    /**
     * Get the gas currently in a tank slot.
     */
    GasStack getGasInTank(int slot);

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
     * Insert gas into a tank slot (import interfaces only).
     *
     * @param slot The tank slot index
     * @param gas The gas to insert
     * @return The amount actually inserted
     */
    default int insertGasIntoTank(int slot, GasStack gas) {
        throw new UnsupportedOperationException("insertGasIntoTank is only supported on import interfaces");
    }

    /**
     * Extract gas from a tank slot (export interfaces only).
     *
     * @param slot The tank slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The gas extracted, or null if nothing extracted
     */
    default GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        throw new UnsupportedOperationException("drainGasFromTank is only supported on export interfaces");
    }
}
