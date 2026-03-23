package com.cells.cells.creative.fluid;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEFluidStack;
import appeng.me.storage.MEInventoryHandler;


/**
 * Cell inventory handler for Creative Fluid Cell.
 * <p>
 * Wraps the CreativeFluidCellInventory and provides the ICellInventoryHandler interface.
 * No partition filtering is needed since the inventory handles its own filter list.
 * <p>
 * Access is READ_WRITE: provides infinite fluids for extraction, voids matching inserts.
 */
public class CreativeFluidCellInventoryHandler extends MEInventoryHandler<IAEFluidStack>
    implements ICellInventoryHandler<IAEFluidStack> {

    private final CreativeFluidCellInventory inventory;

    public CreativeFluidCellInventoryHandler(CreativeFluidCellInventory inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;

        // Creative cells provide infinite fluids and void matching inserts
        this.setBaseAccess(AccessRestriction.READ_WRITE);
    }

    @Override
    public ICellInventory<IAEFluidStack> getCellInv() {
        return inventory;
    }

    @Override
    public boolean isPreformatted() {
        // Creative cells are always "preformatted" since they only provide partitioned fluids
        return inventory.hasPartitionedContent();
    }

    @Override
    public boolean isFuzzy() {
        return false;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return IncludeExclude.WHITELIST;
    }
}
