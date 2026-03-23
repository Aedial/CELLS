package com.cells.blocks.interfacebase.fluid;

import java.util.function.IntSupplier;

import appeng.api.storage.data.IAEFluidStack;

import com.cells.gui.slots.FluidFilterSlot;


/**
 * Fluid filter slot for Fluid Import/Export Interface.
 * <p>
 * Thin wrapper around {@link FluidFilterSlot} that gets fluid from {@link IFluidInterfaceHost}.
 */
public class GuiFluidFilterSlot extends FluidFilterSlot {

    /**
     * Create a filter slot with pagination support.
     *
     * @param host The fluid interface host
     * @param displaySlot The display slot index (0-35 for one page)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public GuiFluidFilterSlot(final IFluidInterfaceHost host, final int displaySlot,
                              final int x, final int y, final IntSupplier pageOffsetSupplier) {
        super(slot -> host.getFilterFluid(slot), displaySlot, x, y, pageOffsetSupplier);
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
