package com.cells.cells.creative.essentia;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellFilterHandler;
import com.cells.integration.thaumicenergistics.EssentiaStackKey;


/**
 * NBT-backed handler for storing filter essentia in a Creative Essentia Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost essentia in the cell's NBT.
 * Each slot holds a single-unit copy of an essentia for filtering purposes.
 */
public class CreativeEssentiaCellFilterHandler extends AbstractCreativeCellFilterHandler<EssentiaStack, EssentiaStackKey> {

    private static final String NBT_KEY_FILTERS = "CreativeEssentiaFilters";

    public CreativeEssentiaCellFilterHandler(@Nonnull ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected String getNBTKey() {
        return NBT_KEY_FILTERS;
    }

    @Override
    @Nullable
    protected EssentiaStack readStackFromNBT(@Nonnull NBTTagCompound nbt) {
        return EssentiaStack.readFromNBT(nbt);
    }

    @Override
    protected void writeStackToNBT(@Nonnull EssentiaStack stack, @Nonnull NBTTagCompound nbt) {
        stack.write(nbt);
    }

    @Override
    @Nullable
    protected EssentiaStackKey createKey(@Nullable EssentiaStack stack) {
        return EssentiaStackKey.of(stack);
    }

    @Override
    @Nonnull
    protected EssentiaStack createGhostCopy(@Nonnull EssentiaStack stack) {
        EssentiaStack copy = stack.copy();
        copy.setAmount(1);
        return copy;
    }

    @Override
    protected boolean isStackEmpty(@Nullable EssentiaStack stack) {
        return stack == null || stack.getAmount() <= 0;
    }

    /**
     * Get the essentia in a specific slot.
     * Convenience method that delegates to getStackInSlot.
     */
    @Nullable
    public EssentiaStack getEssentiaInSlot(int slot) {
        return getStackInSlot(slot);
    }

    /**
     * Set the essentia in a specific slot.
     * Convenience method that delegates to setStackInSlot.
     */
    public void setEssentiaInSlot(int slot, @Nullable EssentiaStack stack) {
        setStackInSlot(slot, stack);
    }

    /**
     * Check if an essentia stack is in the filter.
     */
    public boolean isInFilter(@Nullable EssentiaStack stack) {
        return isStackInFilter(stack);
    }

    /**
     * Check if an aspect is in the filter.
     * Convenience method for checking by Aspect directly.
     */
    public boolean isInFilter(@Nullable Aspect aspect) {
        if (aspect == null) return false;

        EssentiaStackKey key = EssentiaStackKey.of(aspect);
        return key != null && isInFilter(key);
    }
}
