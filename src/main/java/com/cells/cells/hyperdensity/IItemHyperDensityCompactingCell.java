package com.cells.cells.hyperdensity;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;

import com.cells.api.IItemCompactingCell;


/**
 * Interface for hyper-density compacting storage cell items.
 * Combines the compacting cell functionality with the HD byte multiplier.
 * 
 * Due to overflow concerns (compacting uses base units * conversion rates),
 * HD compacting cells are limited to 16M tier maximum.
 */
public interface IItemHyperDensityCompactingCell extends IItemCompactingCell, ICellWorkbenchItem {

    /**
     * Get the displayed byte capacity of this cell.
     * This is the value shown in tooltips and GUI.
     */
    long getDisplayBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the internal byte multiplier.
     * The actual storage capacity is getDisplayBytes() * getByteMultiplier().
     */
    long getByteMultiplier();

    /**
     * Get the total byte capacity (with multiplier applied).
     */
    long getBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the bytes used per stored type (with multiplier applied).
     */
    long getBytesPerType(@Nonnull ItemStack cellItem);

    /**
     * Get the idle power drain of this cell.
     */
    double getIdleDrain();

    /**
     * Check if the given item is blacklisted from this cell.
     */
    boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition);

    /**
     * Check if this cell can be stored inside other storage cells.
     */
    boolean storableInStorageCell();

    /**
     * Multiply two longs with overflow protection.
     */
    static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }
}
