package com.cells.blocks.interfacebase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/**
 * Typed interface for resource interface logic classes.
 * Extends {@link IInterfaceLogic} with generic filter and storage methods.
 * <p>
 * This enables DRY default implementations in type-specific host interfaces
 * (IFluidInterfaceHost, IGasInterfaceHost) that delegate to the logic.
 *
 * @param <AE> The AE2 stack type (IAEFluidStack, IAEGasStack)
 * @param <K>  The key type for hashable lookups (FluidStackKey, GasStackKey)
 */
public interface IResourceInterfaceLogic<AE, K> extends IInterfaceLogic {

    // ================================= Filter Operations =================================

    /**
     * Get the filter at a specific slot.
     *
     * @param slot The slot index (0-based, across all pages)
     * @return The filter stack, or null if slot is empty or invalid
     */
    @Nullable
    AE getFilter(int slot);

    /**
     * Set the filter at a specific slot.
     *
     * @param slot  The slot index (0-based, across all pages)
     * @param stack The stack to set as filter, or null to clear
     */
    void setFilter(int slot, @Nullable AE stack);

    // ================================= Storage State =================================

    /**
     * Check if storage at a specific slot is empty.
     *
     * @param slot The slot index (0-based, across all pages)
     * @return true if the storage slot is empty
     */
    boolean isStorageEmpty(int slot);

    // ================================= Key Operations =================================

    /**
     * Check if a key is present in the filter cache.
     *
     * @param key The key to check
     * @return true if the filter exists
     */
    boolean isInFilter(@Nonnull K key);

    /**
     * Find the slot index for a given key.
     *
     * @param key The key to find
     * @return The slot index, or -1 if not found
     */
    int findSlotByKey(@Nonnull K key);

    /**
     * Add a filter to the first available slot using AE stack type.
     *
     * @param stack The stack to add as a filter
     * @return The slot index where the filter was added, or -1 if no space available
     */
    int addToFirstAvailableSlotAE(@Nonnull AE stack);

    /**
     * Get the amount stored in a specific slot.
     * Uses long precision for accurate overflow handling.
     *
     * @param slot The storage slot index
     * @return The amount stored in the slot (0 if invalid or empty)
     */
    long getSlotAmount(int slot);

    /**
     * Adjust the amount stored in a specific slot by a delta value.
     * Used by GUI containers for long-safe insertion/extraction operations.
     *
     * @param slot  The storage slot index
     * @param delta The amount to add (positive) or subtract (negative)
     * @return The actual amount added/removed
     */
    long adjustSlotAmount(int slot, long delta);
}
