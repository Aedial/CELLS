package com.cells.cells.creative.fluid;

import appeng.api.storage.data.IAEFluidStack;

import com.cells.gui.slots.FluidFilterSlot;


/**
 * Fluid filter slot for Creative Fluid Cell.
 * <p>
 * Thin wrapper around {@link FluidFilterSlot} that gets fluid from tank adapter.
 */
public class GuiCreativeFluidFilterSlot extends FluidFilterSlot {

    /**
     * Create a filter slot.
     *
     * @param tankAdapter The tank adapter that provides filter data
     * @param slot The slot index
     * @param x X position in GUI
     * @param y Y position in GUI
     */
    public GuiCreativeFluidFilterSlot(final CreativeFluidCellTankAdapter tankAdapter,
                                      final int slot, final int x, final int y) {
        super(tankAdapter::getFluidInSlot, slot, x, y);
    }

    /**
     * Returns the fluid ingredient for JEI integration.
     */
    @Override
    public Object getIngredient() {
        final IAEFluidStack fs = getResource();
        return fs == null ? null : fs.getFluidStack();
    }
}
