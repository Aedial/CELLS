package com.cells.cells.normal.compacting;

import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler wrapper for compacting cells.
 * <p>
 * Extends the common base class with compression chain awareness:
 * items in the compression chain are allowed through the filter even
 * if they're not in the partition list.
 */
public class CompactingCellInventoryHandler extends AbstractCellInventoryHandler<IAEItemStack> {

    public CompactingCellInventoryHandler(IMEInventory<IAEItemStack> inventory, IStorageChannel<IAEItemStack> channel) {
        super(inventory, channel);
    }

    /**
     * Override to allow compression chain items through the filter.
     * The underlying CompactingCellInventory handles the actual validation.
     */
    @Override
    public boolean passesBlackOrWhitelist(IAEItemStack input) {
        // First check normal partition list
        if (super.passesBlackOrWhitelist(input)) return true;

        // For compacting cells with whitelist mode, also check if item is in compression chain
        if (myWhitelist != IncludeExclude.WHITELIST) return false;

        ICellInventory<IAEItemStack> ci = getCellInv();
        if (!(ci instanceof CompactingCellInventory)) return false;

        CompactingCellInventory compacting = (CompactingCellInventory) ci;

        return compacting.isInCompressionChain(input);
    }
}
