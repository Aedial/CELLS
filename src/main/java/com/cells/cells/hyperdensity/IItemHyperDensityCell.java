package com.cells.cells.hyperdensity;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;


/**
 * Interface for hyper-density storage cell items.
 * Hyper-density cells internally multiply their storage capacity by a large factor,
 * allowing them to store vastly more than their displayed byte count suggests.
 * 
 * For example, a "1k HD Cell" might display as 1k bytes but actually store
 * 1k * 2,147,483,648 = ~2.1 trillion bytes worth of items.
 */
public interface IItemHyperDensityCell extends ICellWorkbenchItem {

    /**
     * Get the displayed byte capacity of this cell.
     * This is the value shown in tooltips and GUI (e.g., 1k, 4k, etc.).
     */
    long getDisplayBytes(@Nonnull ItemStack cellItem);

    /**
     * Get the internal byte multiplier.
     * The actual storage capacity is getDisplayBytes() * getByteMultiplier().
     * 
     * @return The multiplier applied to display bytes (e.g., 2147483648L for 2GB multiplier)
     */
    long getByteMultiplier();

    /**
     * Get the actual total byte capacity (display bytes * multiplier).
     * This is the real storage capacity used internally.
     */
    default long getTotalBytes(@Nonnull ItemStack cellItem) {
        return multiplyWithOverflowProtection(getDisplayBytes(cellItem), getByteMultiplier());
    }

    /**
     * Get the bytes used per stored type (also multiplied).
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
     * Check if this ItemStack is a valid hyper-density storage cell.
     */
    boolean isHyperDensityCell(@Nonnull ItemStack i);

    /**
     * Multiply two longs with overflow protection.
     * Returns Long.MAX_VALUE if overflow would occur.
     */
    static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;

        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }
}
