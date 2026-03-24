package com.cells.integration.mekanismenergistics;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import mekanism.api.gas.GasStack;

import appeng.tile.inventory.AppEngInternalInventory;

import com.mekeng.github.common.me.data.IAEGasStack;

import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.gui.slots.GasTankSlot;


/**
 * Extended interface for Gas Interface hosts (both import and export, both tile and part).
 * Provides access to gas filter/tank/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link com.cells.blocks.interfacebase.IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 * <p>
 * Pagination, clearing, and slot info methods are inherited from {@link IFilterableInterfaceHost}
 * with default implementations that delegate to {@link #getInterfaceLogic()}.
 */
public interface IGasInterfaceHost
    extends IFilterableInterfaceHost<IAEGasStack, GasStackKey>,
            GasTankSlot.IGasTankHost {

    /**
     * @return The upgrade inventory
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Refresh cached upgrade status after upgrade slot changes.
     */
    void refreshUpgrades();

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * @return The world this host is in.
     */
    World getHostWorld();

    // ================================= Gas Tank Access =================================

    /**
     * Check if a specific tank slot is empty.
     */
    boolean isTankEmpty(int slot);

    /**
     * Get the filter gas for a specific slot.
     */
    IAEGasStack getFilterGas(int slot);

    /**
     * Set the filter gas for a specific slot.
     */
    void setFilterGas(int slot, IAEGasStack gas);

    /**
     * Get the gas currently in a tank slot.
     */
    GasStack getGasInTank(int slot);

    /**
     * Set the gas in a tank slot (for import interface gas pouring).
     *
     * @param slot The tank slot index
     * @param gas The gas to set, or null to clear
     */
    void setGasInTank(int slot, @Nullable GasStack gas);

    /**
     * @return Number of capacity upgrades currently installed.
     */
    int getInstalledCapacityUpgrades();

    /**
     * @return The starting slot index for the current page.
     */
    int getCurrentPageStartSlot();

    // ================================= Import-specific Upgrades =================================

    /**
     * @return true if the overflow upgrade is installed (import only).
     */
    default boolean hasOverflowUpgrade() {
        return false;
    }

    /**
     * @return true if the trash unselected upgrade is installed (import only).
     */
    default boolean hasTrashUnselectedUpgrade() {
        return false;
    }

    // ================================= Direction-specific Operations =================================

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

    // ============================== IFilterableInterfaceHost Implementation ==============================

    @Override
    @Nullable
    default IAEGasStack getFilter(int slot) {
        return getFilterGas(slot);
    }

    @Override
    default void setFilter(int slot, @Nullable IAEGasStack stack) {
        setFilterGas(slot, stack);
    }

    @Override
    default boolean isStorageEmpty(int slot) {
        return isTankEmpty(slot);
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
