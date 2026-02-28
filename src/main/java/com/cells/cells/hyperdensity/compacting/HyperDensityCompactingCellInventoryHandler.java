package com.cells.cells.hyperdensity.compacting;

import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler wrapper for hyper-density compacting cells.
 * <p>
 * Extends the common base class with compression chain awareness:
 * items in the compression chain are allowed through the filter even
 * if they're not in the partition list.
 */
public class HyperDensityCompactingCellInventoryHandler extends AbstractCellInventoryHandler<IAEItemStack> {

    public HyperDensityCompactingCellInventoryHandler(IMEInventory<IAEItemStack> inventory, IStorageChannel<IAEItemStack> channel) {
        super(inventory, channel);
    }

    /**
     * Override to allow compression chain items through the filter.
     */
    @Override
    public boolean passesBlackOrWhitelist(IAEItemStack input) {
        if (super.passesBlackOrWhitelist(input)) return true;

        if (myWhitelist != IncludeExclude.WHITELIST) return false;

        ICellInventory<IAEItemStack> ci = getCellInv();
        if (!(ci instanceof HyperDensityCompactingCellInventory)) return false;

        HyperDensityCompactingCellInventory hdCompacting = (HyperDensityCompactingCellInventory) ci;

        return hdCompacting.isInCompressionChain(input);
    }
}
