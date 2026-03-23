package com.cells.cells.creative.essentia;

import appeng.api.config.AccessRestriction;
import appeng.api.config.IncludeExclude;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.me.storage.MEInventoryHandler;

import thaumicenergistics.api.storage.IAEEssentiaStack;


/**
 * Cell inventory handler for Creative Essentia Cell.
 * <p>
 * Wraps the CreativeEssentiaCellInventory and provides the ICellInventoryHandler interface.
 * No partition filtering is needed since the inventory handles its own filter list.
 * <p>
 * Access is READ_WRITE: provides infinite essentia for extraction, voids matching inserts.
 */
public class CreativeEssentiaCellInventoryHandler extends MEInventoryHandler<IAEEssentiaStack>
    implements ICellInventoryHandler<IAEEssentiaStack> {

    private final CreativeEssentiaCellInventory inventory;

    public CreativeEssentiaCellInventoryHandler(CreativeEssentiaCellInventory inventory) {
        super(inventory, inventory.getChannel());
        this.inventory = inventory;

        // Creative cells provide infinite essentia and void matching inserts
        this.setBaseAccess(AccessRestriction.READ_WRITE);
    }

    @Override
    public ICellInventory<IAEEssentiaStack> getCellInv() {
        return inventory;
    }

    @Override
    public boolean isPreformatted() {
        // Creative cells are always "preformatted" since they only provide partitioned essentia
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
