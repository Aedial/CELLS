package com.cells.blocks.interfacebase.fluid;

import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;

import com.cells.blocks.interfacebase.IResourceInterfaceHost;
import com.cells.gui.slots.FluidTankSlot;
import com.cells.util.FluidStackKey;


/**
 * Extended interface for Fluid Interface hosts (both import and export, both tile and part).
 * Provides access to fluid filter/tank operations.
 * <p>
 * Filter and storage methods are inherited from {@link IResourceInterfaceHost}
 * which provides default implementations delegating to the typed logic.
 */
public interface IFluidInterfaceHost
    extends IResourceInterfaceHost<IAEFluidStack, FluidStackKey>,
            FluidTankSlot.IFluidTankHost {

    /**
     * Get the fluid currently in a tank slot.
     * Required by {@link FluidTankSlot.IFluidTankHost} for GUI rendering.
     */
    FluidStack getFluidInTank(int slot);

    /**
     * Insert fluid into a tank slot (import interfaces only).
     *
     * @param slot The tank slot index
     * @param fluid The fluid to insert
     * @return The amount actually inserted
     */
    default int insertFluidIntoTank(int slot, FluidStack fluid) {
        throw new UnsupportedOperationException("insertFluidIntoTank is only supported on import interfaces");
    }

    /**
     * Extract fluid from a tank slot (export interfaces only).
     *
     * @param slot The tank slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The fluid extracted, or null if nothing extracted
     */
    default FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        throw new UnsupportedOperationException("drainFluidFromTank is only supported on export interfaces");
    }

    @Override
    @Nullable
    default FluidStackKey createKey(@Nullable IAEFluidStack stack) {
        if (stack == null) return null;
        return FluidStackKey.of(stack.getFluidStack());
    }

    @Override
    default String getTypeLocalizationKey() {
        return "cells.type.fluid";
    }
}
