package com.cells.blocks.interfacebase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.tile.inventory.AppEngInternalInventory;


/**
 * Generic interface for interface hosts that support filter operations with key-based lookup.
 * Provides a unified API for filter management across different stack types (Item, Fluid, Gas, Essentia).
 * <p>
 * This interface enables efficient filter operations using cached HashMaps for O(1) lookups,
 * avoiding the need to iterate over all slots for duplicate checks and filter searches.
 * <p>
 * Most methods delegate to the underlying {@link IInterfaceLogic} instance.
 *
 * @param <T> The stored stack type (ItemStack, IAEFluidStack, IAEGasStack)
 * @param <K> The key type for hashable lookups (ItemStackKey, FluidStackKey, GasStackKey)
 */
public interface IFilterableInterfaceHost<T, K> extends IInterfaceHost {

    // ================================= Logic Access =================================

    /**
     * Get the underlying logic instance that handles all interface operations.
     * This enables DRY default implementations that delegate to the logic.
     *
     * @return The logic instance, never null
     */
    @Nonnull
    IInterfaceLogic getInterfaceLogic();

    // ================================= Slot Information (delegated to logic) =================================

    /**
     * @return Number of effective filter slots based on installed capacity upgrades.
     */
    default int getEffectiveFilterSlots() {
        return getInterfaceLogic().getEffectiveFilterSlots();
    }

    // ================================= Pagination (delegated to logic) =================================

    /**
     * @return The current page index (0-based).
     */
    default int getCurrentPage() {
        return getInterfaceLogic().getCurrentPage();
    }

    /**
     * Set the current page index.
     *
     * @param page The page index (0-based)
     */
    default void setCurrentPage(int page) {
        getInterfaceLogic().setCurrentPage(page);
    }

    /**
     * @return Total number of pages.
     */
    default int getTotalPages() {
        return getInterfaceLogic().getTotalPages();
    }

    // ================================= Filter Access =================================

    /**
     * Get the filter at a specific slot.
     *
     * @param slot The slot index (0-based, across all pages)
     * @return The filter stack, or null if slot is empty or invalid
     */
    @Nullable
    T getFilter(int slot);

    /**
     * Set the filter at a specific slot.
     *
     * @param slot  The slot index (0-based, across all pages)
     * @param stack The stack to set as filter, or null to clear
     */
    void setFilter(int slot, @Nullable T stack);

    // ================================= Storage State =================================

    /**
     * Check if storage at a specific slot is empty.
     * For import interfaces, filters cannot be changed when storage has content.
     *
     * @param slot The slot index (0-based, across all pages)
     * @return true if the storage slot is empty
     */
    boolean isStorageEmpty(int slot);

    // ================================= Key Operations =================================

    /**
     * Create a key from a stack for hashable lookups.
     *
     * @param stack The stack to create a key from
     * @return The key, or null if the stack is null/empty
     */
    @Nullable
    K createKey(@Nullable T stack);

    /**
     * Check if a key is present in the filter cache.
     * This is an O(1) operation using the internal HashMap.
     *
     * @param key The key to check
     * @return true if the filter exists
     */
    boolean isInFilter(@Nonnull K key);

    /**
     * Check if a stack is present in the filter cache.
     * Convenience method that creates a key and checks it.
     *
     * @param stack The stack to check
     * @return true if the filter exists
     */
    default boolean isStackInFilter(@Nullable T stack) {
        K key = createKey(stack);
        return key != null && isInFilter(key);
    }

    /**
     * Find the slot index for a given key.
     * Returns -1 if not found.
     *
     * @param key The key to find
     * @return The slot index, or -1 if not found
     */
    int findSlotByKey(@Nonnull K key);

    // ================================= Filter Modification =================================

    /**
     * Rebuild the internal filter cache (key -> slot map).
     * Should be called after any direct filter modifications.
     */
    default void refreshFilterMap() {
        getInterfaceLogic().refreshFilterMap();
    }

    /**
     * Clear all filters across all pages.
     * This will only clear filters for slots whose storage is currently empty.
     */
    default void clearFilters() {
        getInterfaceLogic().clearFilters();
    }

    // ================================= Upgrades (delegated to logic) =================================

    /**
     * @return The upgrade inventory.
     */
    default AppEngInternalInventory getUpgradeInventory() {
        return getInterfaceLogic().getUpgradeInventory();
    }

    /**
     * Refresh cached upgrade status after upgrade slot changes.
     */
    default void refreshUpgrades() {
        getInterfaceLogic().refreshUpgrades();
    }

    // ================================= Localization =================================

    /**
     * @return The localization key for the type name (e.g., "cells.type.item", "cells.type.fluid")
     */
    String getTypeLocalizationKey();
}
