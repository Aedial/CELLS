package com.cells.cells.creative;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.me.storage.MEInventoryHandler;

import appeng.api.storage.data.IAEStack;


/**
 * Cell inventory handler for Creative Cells.
 * <p>
 * Wraps the CreativeEssentiaCellInventory and provides the ICellInventoryHandler interface.
 * No partition filtering is needed since the inventory handles its own filter list.
 * <p>
 * Access is READ_WRITE: provides "infinite" content for extraction, voids matching inserts.
 */
public class AbstractCreativeCellInventoryHandler<
        T extends IAEStack<T>,
        C extends AbstractCreativeCellInventory<T, ?, ?, ?>
    >
    extends MEInventoryHandler<T>
    implements ICellInventoryHandler<T> {

    private final C inventory;

    public AbstractCreativeCellInventoryHandler(C inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;

        this.setBaseAccess(AccessRestriction.READ_WRITE);
    }

    @Override
    public ICellInventory<T> getCellInv() {
        return inventory;
    }

    @Override
    public boolean isPreformatted() {
        // Creative cells are always "preformatted" since they only provide partitioned content
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
