package com.cells.cells.creative.fluid;

import com.cells.cells.creative.AbstractCreativeCellInventoryHandler;

import appeng.api.storage.data.IAEFluidStack;


/**
 * Cell inventory handler for Creative Fluid Cell.
 */
public class CreativeFluidCellInventoryHandler extends AbstractCreativeCellInventoryHandler<IAEFluidStack, CreativeFluidCellInventory> {

    public CreativeFluidCellInventoryHandler(CreativeFluidCellInventory inventory) {
        super(inventory);
    }
}
