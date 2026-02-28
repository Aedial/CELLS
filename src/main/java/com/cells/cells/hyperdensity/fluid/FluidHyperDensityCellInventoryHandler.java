package com.cells.cells.hyperdensity.fluid;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler wrapper for hyper-density fluid cells.
 * <p>
 * Uses the common base class for upgrade/partition processing.
 */
public class FluidHyperDensityCellInventoryHandler extends AbstractCellInventoryHandler<IAEFluidStack> {

    public FluidHyperDensityCellInventoryHandler(IMEInventory<IAEFluidStack> inventory, IStorageChannel<IAEFluidStack> channel) {
        super(inventory, channel);
    }
}
