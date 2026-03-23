package com.cells.cells.creative.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.cells.cells.creative.AbstractCreativeCellFilterHandler;
import com.cells.util.FluidStackKey;


/**
 * NBT-backed handler for storing filter fluids in a Creative Fluid Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost fluids in the cell's NBT.
 * Each slot holds a single-mB copy of a fluid for filtering purposes.
 */
public class CreativeFluidCellFilterHandler extends AbstractCreativeCellFilterHandler<FluidStack, FluidStackKey> {

    private static final String NBT_KEY_FILTERS = "CreativeFluidFilters";

    public CreativeFluidCellFilterHandler(@Nonnull ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected String getNBTKey() {
        return NBT_KEY_FILTERS;
    }

    @Override
    @Nullable
    protected FluidStack readStackFromNBT(@Nonnull NBTTagCompound nbt) {
        return FluidStack.loadFluidStackFromNBT(nbt);
    }

    @Override
    protected void writeStackToNBT(@Nonnull FluidStack stack, @Nonnull NBTTagCompound nbt) {
        stack.writeToNBT(nbt);
    }

    @Override
    @Nullable
    protected FluidStackKey createKey(@Nullable FluidStack stack) {
        return FluidStackKey.of(stack);
    }

    @Override
    @Nonnull
    protected FluidStack createGhostCopy(@Nonnull FluidStack stack) {
        FluidStack copy = stack.copy();
        copy.amount = 1;
        return copy;
    }

    @Override
    protected boolean isStackEmpty(@Nullable FluidStack stack) {
        return stack == null || stack.amount <= 0;
    }

    /**
     * Get the fluid in a specific slot.
     * Convenience method that delegates to getStackInSlot.
     */
    @Nullable
    public FluidStack getFluidInSlot(int slot) {
        return getStackInSlot(slot);
    }

    /**
     * Set the fluid in a specific slot.
     * Convenience method that delegates to setStackInSlot.
     */
    public void setFluidInSlot(int slot, @Nullable FluidStack stack) {
        setStackInSlot(slot, stack);
    }

    /**
     * Check if a fluid stack is in the filter.
     */
    public boolean isInFilter(@Nullable FluidStack stack) {
        return isStackInFilter(stack);
    }
}
