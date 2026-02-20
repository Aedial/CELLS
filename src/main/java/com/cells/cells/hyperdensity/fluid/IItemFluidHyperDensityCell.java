package com.cells.cells.hyperdensity.fluid;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.util.CellMathHelper;


/**
 * Interface for hyper-density fluid storage cell items.
 * Hyper-density cells internally multiply their storage capacity by a large factor,
 * allowing them to store vastly more than their displayed byte count suggests.
 * <p>
 * For example, a "1k HD Fluid Cell" might display as 1k bytes but actually store
 * 1k * 2,147,483,648 = ~2.1 trillion bytes worth of fluids.
 */
public interface IItemFluidHyperDensityCell extends ICellWorkbenchItem {

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
        return CellMathHelper.multiplyWithOverflowProtection(getDisplayBytes(cellItem), getByteMultiplier());
    }

    /**
     * Get the bytes used per stored type (also multiplied).
     */
    long getBytesPerType(@Nonnull ItemStack cellItem);

    /**
     * Get the maximum number of fluid types this cell can hold.
     */
    int getMaxTypes();

    /**
     * Get the idle power drain of this cell.
     */
    double getIdleDrain();

    /**
     * Check if the given fluid is blacklisted from this cell.
     */
    boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEFluidStack requestedAddition);

    /**
     * Check if this cell can be stored inside other storage cells.
     */
    boolean storableInStorageCell();

    /**
     * Check if this ItemStack is a valid hyper-density fluid storage cell.
     */
    boolean isFluidHyperDensityCell(@Nonnull ItemStack i);
}
