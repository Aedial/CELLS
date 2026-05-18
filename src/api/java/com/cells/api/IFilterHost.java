package com.cells.api;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;


/**
 * Shared filter contract for CELLS devices.
 */
public interface IFilterHost {

    /**
     * Total number of currently available filter slots.
     */
    int getFilterSlots();

    /**
     * Get the filter in the given slot.
     */
    @Nonnull
    ItemStack getFilter(int slot);

    /**
     * Set or clear the filter in the given slot.
     */
    void setFilter(int slot, @Nonnull ItemStack stack);

    /**
     * Clear every filter slot.
     */
    void clearFilters();
}