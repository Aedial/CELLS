package com.cells.cells.hyperdensity.fluid;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEFluidStack;

import com.cells.cells.common.IHyperDensityCellType;


/**
 * Interface for hyper-density fluid storage cell items.
 * <p>
 * Extends the common HD cell interface with fluid-specific behavior.
 */
public interface IItemFluidHyperDensityCell extends IHyperDensityCellType<IAEFluidStack> {

}
