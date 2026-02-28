package com.cells.cells.hyperdensity.item;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler wrapper for hyper-density cells.
 * <p>
 * Uses the common base class for upgrade/partition processing.
 */
public class HyperDensityCellInventoryHandler extends AbstractCellInventoryHandler<IAEItemStack> {

    public HyperDensityCellInventoryHandler(IMEInventory<IAEItemStack> inventory, IStorageChannel<IAEItemStack> channel) {
        super(inventory, channel);
    }
}
