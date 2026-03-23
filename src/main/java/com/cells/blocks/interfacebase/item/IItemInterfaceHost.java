package com.cells.blocks.interfacebase.item;

import com.cells.blocks.interfacebase.IInterfaceHost;
import net.minecraft.item.ItemStack;

import appeng.tile.inventory.AppEngInternalInventory;


/**
 * Extended interface for Item Interface hosts (both import and export, both tile and part).
 * Provides access to item filter/storage/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 */
public interface IItemInterfaceHost extends IInterfaceHost {

    /**
     * @return The filter inventory (ghost items, 1 stack size each)
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
     * Import uses filter-to-slot mapping; export checks direct filter match.
     */
    boolean isItemValidForSlot(int slot, ItemStack stack);

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
     * storage slot is empty (to prevent orphaning items). Export clears all filters.
     */
    void clearFilters();

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
}
