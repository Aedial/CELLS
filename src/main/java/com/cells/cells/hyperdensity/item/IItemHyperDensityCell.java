package com.cells.cells.hyperdensity.item;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.common.IHyperDensityCellType;


/**
 * Interface for hyper-density item storage cell items.
 * <p>
 * Extends the common HD cell interface with item-specific behavior.
 */
public interface IItemHyperDensityCell extends IHyperDensityCellType<IAEItemStack> {

    /**
     * Check if this ItemStack is a valid hyper-density storage cell.
     * Alias for the common interface method.
     */
    boolean isHyperDensityCell(@Nonnull ItemStack i);
}
