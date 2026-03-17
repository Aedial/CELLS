package com.cells.cells.creative;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.util.Platform;

import com.cells.util.ItemStackKey;


/**
 * NBT-backed item handler for storing filter items in a Creative Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost items in the cell's NBT.
 * Each slot holds a single-count copy of an item for filtering purposes.
 */
public class CreativeCellFilterHandler implements IItemHandlerModifiable {

    /** Total number of filter slots (9x7 grid) */
    public static final int SLOT_COUNT = 63;

    private static final String NBT_KEY_FILTERS = "CreativeFilters";

    private final ItemStack cellStack;

    /** Cache of filter keys for quick matching */
    private Set<ItemStackKey> cachedFilterKeys = new HashSet<>();

    public CreativeCellFilterHandler(@Nonnull ItemStack cellStack) {
        this.cellStack = cellStack;
        loadCacheFromNBT();
    }

    @Override
    public int getSlots() {
        return SLOT_COUNT;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        if (slot >= filters.tagCount()) return ItemStack.EMPTY;

        NBTTagCompound slotNBT = filters.getCompoundTagAt(slot);
        if (slotNBT.isEmpty()) return ItemStack.EMPTY;

        return new ItemStack(slotNBT);
    }

    public void loadCacheFromNBT() {
        cachedFilterKeys.clear();

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < filters.tagCount(); i++) {
            NBTTagCompound slotNBT = filters.getCompoundTagAt(i);
            if (!slotNBT.isEmpty()) {
                ItemStack filterStack = new ItemStack(slotNBT);
                cachedFilterKeys.add(ItemStackKey.of(filterStack));
            }
        }
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(NBT_KEY_FILTERS, Constants.NBT.TAG_COMPOUND);

        // Ensure the list has enough entries
        while (filters.tagCount() <= slot) {
            filters.appendTag(new NBTTagCompound());
        }

        // Store as single-count ghost item
        if (stack.isEmpty()) {
            filters.set(slot, new NBTTagCompound());
        } else {
            ItemStack ghost = stack.copy();
            ghost.setCount(1);
            NBTTagCompound slotNBT = new NBTTagCompound();
            ghost.writeToNBT(slotNBT);
            filters.set(slot, slotNBT);
        }

        cellNBT.setTag(NBT_KEY_FILTERS, filters);

        // Update cache
        loadCacheFromNBT();
    }

    public boolean isInFilter(@Nonnull ItemStackKey key) {
        return cachedFilterKeys.contains(key);
    }

    public boolean isInFilter(@Nonnull ItemStack stack) {
        return isInFilter(ItemStackKey.of(stack));
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        // Ghost slots don't consume items - they just set the filter
        if (!simulate) setStackInSlot(slot, stack);

        return stack;
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // Ghost slots don't extract - clear the filter instead
        if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);

        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
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
