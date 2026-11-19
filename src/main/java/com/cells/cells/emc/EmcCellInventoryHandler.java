package com.cells.cells.emc;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEItemStack;

import com.cells.profiling.ProfilingInventoryHandler;


/**
 * AE2 inventory handler for the EMC cell.
 */
public class EmcCellInventoryHandler extends ProfilingInventoryHandler<IAEItemStack>
        implements ICellInventoryHandler<IAEItemStack> {

    private final EmcCellInventory inventory;

    public EmcCellInventoryHandler(EmcCellInventory inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;
        this.setBaseAccess(AccessRestriction.READ_WRITE);
    }

    @Override
    public ICellInventory<IAEItemStack> getCellInv() {
        return this.inventory;
    }

    @Override
    public boolean isPreformatted() {
        return this.inventory.hasPartitionedContent();
    }

    // We do not filter precisely, as ProjectE may allow items of same NBT to be inserted
    @Override
    public boolean isFuzzy() {
        return true;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return IncludeExclude.WHITELIST;
    }
}