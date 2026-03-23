package com.cells.cells.creative.gas;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.me.storage.MEInventoryHandler;

import com.mekeng.github.common.me.data.IAEGasStack;


/**
 * Cell inventory handler for Creative Gas Cell.
 * <p>
 * Wraps the CreativeGasCellInventory and provides the ICellInventoryHandler interface.
 * No partition filtering is needed since the inventory handles its own filter list.
 * <p>
 * Access is READ_WRITE: provides infinite gases for extraction, voids matching inserts.
 */
public class CreativeGasCellInventoryHandler extends MEInventoryHandler<IAEGasStack>
    implements ICellInventoryHandler<IAEGasStack> {

    private final CreativeGasCellInventory inventory;

    public CreativeGasCellInventoryHandler(CreativeGasCellInventory inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;

        // Creative cells provide infinite gases and void matching inserts
        this.setBaseAccess(AccessRestriction.READ_WRITE);
    }

    @Override
    public ICellInventory<IAEGasStack> getCellInv() {
        return inventory;
    }

    @Override
    public boolean isPreformatted() {
        // Creative cells are always "preformatted" since they only provide partitioned gases
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
