package com.cells.cells.creative.gas;

import javax.annotation.Nullable;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;

import com.mekeng.github.common.me.inventory.IGasInventory;


/**
 * Adapter to expose CreativeGasCellFilterHandler as IGasInventory.
 * <p>
 * Used for gas sync between client and server via GasSyncHelper.
 */
public class CreativeGasCellTankAdapter implements IGasInventory {

    private final CreativeGasCellFilterHandler filterHandler;
    private final GasTank[] tanks;

    public CreativeGasCellTankAdapter(CreativeGasCellFilterHandler filterHandler) {
        this.filterHandler = filterHandler;
        this.tanks = new GasTank[filterHandler.getSlots()];

        // Ghost slot - 1 mB capacity
        for (int i = 0; i < tanks.length; i++) tanks[i] = new GasTank(1);
    }

    @Override
    public int size() {
        return filterHandler.getSlots();
    }

    @Override
    public boolean usable(int index) {
        return index >= 0 && index < size();
    }

    @Override
    public GasTank[] getTanks() {
        return tanks;
    }

    @Override
    @Nullable
    public GasStack getGasStack(int index) {
        return filterHandler.getGasInSlot(index);
    }

    @Override
    public int addGas(int index, GasStack stack, boolean simulate) {
        // Ghost slots don't actually add gas
        return 0;
    }

    @Override
    public GasStack removeGas(int index, GasStack stack, boolean simulate) {
        // Ghost slots don't actually remove gas
        return null;
    }

    @Override
    public GasStack removeGas(int index, int amount, boolean simulate) {
        // Ghost slots don't actually remove gas
        return null;
    }

    @Override
    public void setGas(int index, GasStack stack) {
        filterHandler.setGasInSlot(index, stack);
    }

    @Override
    public void setCap(int cap) {
        // Ghost slots have fixed 1 mB capacity
    }
}
