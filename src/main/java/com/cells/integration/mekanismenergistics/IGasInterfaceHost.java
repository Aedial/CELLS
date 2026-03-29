package com.cells.integration.mekanismenergistics;

import javax.annotation.Nullable;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;

import com.cells.blocks.interfacebase.IResourceInterfaceHost;
import com.cells.gui.slots.GasTankSlot;


/**
 * Extended interface for Gas Interface hosts (both import and export, both tile and part).
 * Provides access to gas filter/tank operations.
 * <p>
 * Filter and storage methods are inherited from {@link IResourceInterfaceHost}
 * which provides default implementations delegating to the typed logic.
 */
public interface IGasInterfaceHost
    extends IResourceInterfaceHost<IAEGasStack, GasStackKey>,
            GasTankSlot.IGasTankHost {

    /**
     * Get the gas currently in a tank slot.
     * Required by {@link GasTankSlot.IGasTankHost} for GUI rendering.
     */
    GasStack getGasInTank(int slot);

    @Override
    default long getGasAmount(int slot) {
        return getInterfaceLogic().getSlotAmount(slot);
    }

    /**
     * Set the gas in a tank slot (for import interface gas pouring).
     *
     * @param slot The tank slot index
     * @param gas The gas to set, or null to clear
     */
    void setGasInTank(int slot, @Nullable GasStack gas);

    /**
     * Insert gas into a tank slot (import interfaces only).
     *
     * @param slot The tank slot index
     * @param gas The gas to insert
     * @return The amount actually inserted
     */
    default int insertGasIntoTank(int slot, GasStack gas) {
        throw new UnsupportedOperationException("insertGasIntoTank is only supported on import interfaces");
    }

    /**
     * Extract gas from a tank slot (export interfaces only).
     *
     * @param slot The tank slot index
     * @param maxDrain Maximum amount to drain
     * @param doDrain Whether to actually drain or just simulate
     * @return The gas extracted, or null if nothing extracted
     */
    default GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        throw new UnsupportedOperationException("drainGasFromTank is only supported on export interfaces");
    }

    @Override
    @Nullable
    default GasStackKey createKey(@Nullable IAEGasStack stack) {
        if (stack == null) return null;
        return GasStackKey.of(stack.getGasStack());
    }

    @Override
    default String getTypeLocalizationKey() {
        return "cells.type.gas";
    }
}
