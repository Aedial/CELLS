package com.cells.cells.creative;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.storage.MEInventoryHandler;


/**
 * Cell inventory handler for Creative Cell.
 * <p>
 * Wraps the CreativeCellInventory and provides the ICellInventoryHandler interface.
 * No partition filtering is needed since the inventory handles its own filter list.
 * <p>
 * Access is restricted to READ (extraction only).
 */
public class CreativeCellInventoryHandler extends MEInventoryHandler<IAEItemStack>
    implements ICellInventoryHandler<IAEItemStack> {

    private final CreativeCellInventory inventory;

    public CreativeCellInventoryHandler(CreativeCellInventory inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;

        // Creative cells are extract-only
        this.setBaseAccess(AccessRestriction.READ);
    }

    @Override
    public ICellInventory<IAEItemStack> getCellInv() {
        return inventory;
    }

    @Override
    public boolean isPreformatted() {
        // Creative cells are always "preformatted" since they only provide partitioned items
        return inventory.getPartitionedItems().size() > 0;
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
