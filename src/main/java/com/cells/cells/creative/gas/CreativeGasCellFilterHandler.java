package com.cells.cells.creative.gas;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import mekanism.api.gas.GasStack;

import com.cells.cells.creative.AbstractCreativeCellFilterHandler;
import com.cells.integration.mekanismenergistics.GasStackKey;


/**
 * NBT-backed handler for storing filter gases in a Creative Gas Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost gases in the cell's NBT.
 * Each slot holds a single-unit copy of a gas for filtering purposes.
 */
public class CreativeGasCellFilterHandler extends AbstractCreativeCellFilterHandler<GasStack, GasStackKey> {

    private static final String NBT_KEY_FILTERS = "CreativeGasFilters";

    public CreativeGasCellFilterHandler(@Nonnull ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected String getNBTKey() {
        return NBT_KEY_FILTERS;
    }

    @Override
    @Nullable
    protected GasStack readStackFromNBT(@Nonnull NBTTagCompound nbt) {
        return GasStack.readFromNBT(nbt);
    }

    @Override
    protected void writeStackToNBT(@Nonnull GasStack stack, @Nonnull NBTTagCompound nbt) {
        stack.write(nbt);
    }

    @Override
    @Nullable
    protected GasStackKey createKey(@Nullable GasStack stack) {
        return GasStackKey.of(stack);
    }

    @Override
    @Nonnull
    protected GasStack createGhostCopy(@Nonnull GasStack stack) {
        GasStack copy = stack.copy();
        copy.amount = 1;
        return copy;
    }

    @Override
    protected boolean isStackEmpty(@Nullable GasStack stack) {
        return stack == null || stack.amount <= 0;
    }

    /**
     * Get the gas in a specific slot.
     * Convenience method that delegates to getStackInSlot.
     */
    @Nullable
    public GasStack getGasInSlot(int slot) {
        return getStackInSlot(slot);
    }

    /**
     * Set the gas in a specific slot.
     * Convenience method that delegates to setStackInSlot.
     */
    public void setGasInSlot(int slot, @Nullable GasStack stack) {
        setStackInSlot(slot, stack);
    }

    /**
     * Check if a gas stack is in the filter.
     */
    public boolean isInFilter(@Nullable GasStack stack) {
        return isStackInFilter(stack);
    }
}
