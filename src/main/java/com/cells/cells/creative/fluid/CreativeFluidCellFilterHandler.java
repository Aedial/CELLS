package com.cells.cells.creative.fluid;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import appeng.util.Platform;

import com.cells.util.FluidStackKey;


/**
 * NBT-backed handler for storing filter fluids in a Creative Fluid Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost fluids in the cell's NBT.
 * Each slot holds a single-mB copy of a fluid for filtering purposes.
 */
public class CreativeFluidCellFilterHandler {

    /** Total number of filter slots (9x7 grid) */
    public static final int SLOT_COUNT = 63;

    private static final String NBT_KEY_FILTERS = "CreativeFluidFilters";

    private final ItemStack cellStack;

    /** Cache of filter keys for quick matching */
    private Set<FluidStackKey> cachedFilterKeys = new HashSet<>();

    public CreativeFluidCellFilterHandler(@Nonnull ItemStack cellStack) {
        this.cellStack = cellStack;
        loadCacheFromNBT();
    }

    public int getSlots() {
        return SLOT_COUNT;
    }

    @Nullable
    public FluidStack getFluidInSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        if (slot >= filters.tagCount()) return null;

        NBTTagCompound slotNBT = filters.getCompoundTagAt(slot);
        if (slotNBT.isEmpty()) return null;

        return FluidStack.loadFluidStackFromNBT(slotNBT);
    }

    public void loadCacheFromNBT() {
        cachedFilterKeys.clear();

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < filters.tagCount(); i++) {
            NBTTagCompound slotNBT = filters.getCompoundTagAt(i);

            if (!slotNBT.isEmpty()) {
                FluidStack filterStack = FluidStack.loadFluidStackFromNBT(slotNBT);
                FluidStackKey key = FluidStackKey.of(filterStack);
                if (key != null) cachedFilterKeys.add(key);
            }
        }
    }

    public void setFluidInSlot(int slot, @Nullable FluidStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        // Ensure the list has enough entries
        while (filters.tagCount() <= slot) filters.appendTag(new NBTTagCompound());

        // Store as single-mB ghost fluid
        if (stack == null) {
            filters.set(slot, new NBTTagCompound());
        } else {
            FluidStack ghost = stack.copy();
            ghost.amount = 1;
            NBTTagCompound slotNBT = new NBTTagCompound();
            ghost.writeToNBT(slotNBT);
            filters.set(slot, slotNBT);
        }

        cellNBT.setTag(NBT_KEY_FILTERS, filters);

        // Update cache
        loadCacheFromNBT();
    }

    public boolean isInFilter(@Nonnull FluidStackKey key) {
        return cachedFilterKeys.contains(key);
    }

    public boolean isInFilter(@Nullable FluidStack stack) {
        if (stack == null) return false;

        FluidStackKey key = FluidStackKey.of(stack);
        return key != null && isInFilter(key);
    }

    /**
     * Get the count of non-empty filter slots.
     */
    public int getFilterCount() {
        return cachedFilterKeys.size();
    }

    /**
     * Clear all filter slots.
     */
    public void clearAll() {
        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        cellNBT.setTag(NBT_KEY_FILTERS, new NBTTagList());
        loadCacheFromNBT();
    }
}
