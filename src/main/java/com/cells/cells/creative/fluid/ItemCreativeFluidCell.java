package com.cells.cells.creative.fluid;

import net.minecraftforge.fluids.FluidStack;

import com.cells.cells.creative.fluid.CreativeFluidCellFilterHandler;
import com.cells.cells.creative.AbstractCreativeCellItem;
import com.cells.gui.CellsGuiHandler;


public class ItemCreativeFluidCell extends AbstractCreativeCellItem<FluidStack, CreativeFluidCellFilterHandler> {

    public ItemCreativeFluidCell() {
        super("creative_fluid_cell", CellsGuiHandler.GUI_CREATIVE_FLUID_CELL, "fluid", CreativeFluidCellFilterHandler::new);
    }

    @Override
    protected String formatStackDisplay(FluidStack stack) {
        return "§9" + stack.getLocalizedName();
    }

    @Override
    protected boolean isFilterEmpty(FluidStack filter) {
        return filter == null || filter.amount <= 0;
    }
}
