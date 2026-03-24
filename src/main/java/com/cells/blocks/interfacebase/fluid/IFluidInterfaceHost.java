package com.cells.blocks.interfacebase.fluid;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.gui.slots.FluidTankSlot;
import com.cells.util.FluidStackKey;


/**
 * Extended interface for Fluid Interface hosts (both import and export, both tile and part).
 * Provides access to fluid filter/tank/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link com.cells.blocks.interfacebase.IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 * <p>
 * Pagination, clearing, and slot info methods are inherited from {@link IFilterableInterfaceHost}
 * with default implementations that delegate to {@link #getInterfaceLogic()}.
 */
public interface IFluidInterfaceHost
    extends IFilterableInterfaceHost<IAEFluidStack, FluidStackKey>,
            FluidTankSlot.IFluidTankHost {

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

    // ================================= Fluid Tank Access =================================

    /**
     * Check if a specific tank slot is empty.
     */
    boolean isTankEmpty(int slot);

    /**
     * Get the filter fluid for a specific slot.
     */
    IAEFluidStack getFilterFluid(int slot);

    /**
     * Set the filter fluid for a specific slot.
     */
    void setFilterFluid(int slot, IAEFluidStack fluid);

    /**
     * Get the fluid currently in a tank slot.
     */
    FluidStack getFluidInTank(int slot);

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

    // ============================== IFilterableInterfaceHost Implementation ==============================

    @Override
    @Nullable
    default IAEFluidStack getFilter(int slot) {
        return getFilterFluid(slot);
    }

    @Override
    default void setFilter(int slot, @Nullable IAEFluidStack stack) {
        setFilterFluid(slot, stack);
    }

    @Override
    default boolean isStorageEmpty(int slot) {
        return isTankEmpty(slot);
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

