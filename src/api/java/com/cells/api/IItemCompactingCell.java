package com.cells.api;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


/**
 * API interface for compacting storage cell items.
 * <p>
 * Use instanceof to check if an item is a compacting cell:
 * {@code if (cellStack.getItem() instanceof IItemCompactingCell)}
 *
 * Then call {@link #initializeCompactingCellChain} to set up the compression chain
 * when partitioning from an external GUI.
 */
public interface IItemCompactingCell {

    /**
     * Initialize the compression chain for this compacting cell.
     * <p>
     * Call this when partitioning a compacting cell from an external GUI (like Cell Terminal).
     * This sets up the compression chain immediately so items can be inserted/extracted
     * in any compressed form without needing to insert items first.
     *
     * @param cellStack The compacting cell ItemStack
     * @param partitionItem The item to use as partition (pass empty to read from config)
     * @param world The world for recipe lookups
     */
    void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem, @Nonnull World world);
}
