package com.cells.integration.mekanismenergistics;

import java.util.function.IntSupplier;

import com.mekeng.github.common.me.data.IAEGasStack;

import com.cells.gui.slots.GasFilterSlot;


/**
 * Gas filter slot for Gas Import/Export Interface.
 * <p>
 * Thin wrapper around {@link GasFilterSlot} that gets gas from {@link ContainerGasInterface}.
 */
public class GuiGasFilterSlot extends GasFilterSlot {

    /**
     * Create a filter slot with pagination support.
     *
     * @param container The gas interface container (for reading cached filters)
     * @param displaySlot The display slot index (0-35 for one page)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     */
    public GuiGasFilterSlot(final ContainerGasInterface container, final int displaySlot,
                            final int x, final int y, final IntSupplier pageOffsetSupplier) {
        super(slot -> container.getClientFilterGas(slot), displaySlot, x, y, pageOffsetSupplier);
    }

    /**
     * Returns the gas ingredient for JEI integration.
     */
    @Override
    public Object getIngredient() {
        final IAEGasStack gs = getResource();
        return gs == null ? null : gs.getGasStack();
    }
}
