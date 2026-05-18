package com.cells.api;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


/**
 * API contract for compacting storage cells.
 */
public interface IItemCompactingCell {

    /**
     * Initialize the compression chain for this compacting cell after its
     * partition has been assigned.
     *
     * @param cellStack The compacting cell ItemStack
     * @param partitionItem The item to use as partition (pass empty to read from config)
     * @param world The world for recipe lookups
     */
    void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem, @Nonnull World world);
}
