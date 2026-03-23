package com.cells.cells.creative;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import appeng.util.Platform;


/**
 * Abstract NBT-backed handler for storing filter stacks in Creative Cells.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost items in the cell's NBT.
 * Each slot holds a single-unit copy of a stack for filtering purposes.
 *
 * @param <T> The stack type (ItemStack, FluidStack, GasStack, EssentiaStack)
 * @param <K> The key type for quick matching in a HashSet
 */
public abstract class AbstractCreativeCellFilterHandler<T, K> {

    /** Total number of filter slots (9x7 grid) */
    public static final int SLOT_COUNT = 63;

    protected final ItemStack cellStack;

    /** Cache of filter keys for quick matching */
    protected Set<K> cachedFilterKeys = new HashSet<>();

    protected AbstractCreativeCellFilterHandler(@Nonnull ItemStack cellStack) {
        this.cellStack = cellStack;
        loadCacheFromNBT();
    }

    /**
     * @return The NBT key used to store filters for this cell type
     */
    protected abstract String getNBTKey();

    /**
     * Read a stack from NBT.
     *
     * @param nbt The compound to read from
     * @return The stack, or null if invalid
     */
    @Nullable
    protected abstract T readStackFromNBT(@Nonnull NBTTagCompound nbt);

    /**
     * Write a stack to NBT.
     *
     * @param stack The stack to write
     * @param nbt   The compound to write to
     */
    protected abstract void writeStackToNBT(@Nonnull T stack, @Nonnull NBTTagCompound nbt);

    /**
     * Create a key for the given stack for quick matching.
     *
     * @param stack The stack to create a key for
     * @return The key, or null if the stack is invalid
     */
    @Nullable
    protected abstract K createKey(@Nullable T stack);

    /**
     * Create a single-unit copy of the stack for storage as a ghost.
     *
     * @param stack The stack to copy
     * @return A single-unit copy
     */
    @Nonnull
    protected abstract T createGhostCopy(@Nonnull T stack);

    /**
     * Check if the given stack is null or empty.
     *
     * @param stack The stack to check
     * @return true if null or empty
     */
    protected abstract boolean isStackEmpty(@Nullable T stack);

    public int getSlots() {
        return SLOT_COUNT;
    }

    @Nullable
    public T getStackInSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return null;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(getNBTKey(), Constants.NBT.TAG_COMPOUND);

        if (slot >= filters.tagCount()) return null;

        NBTTagCompound slotNBT = filters.getCompoundTagAt(slot);
        if (slotNBT.isEmpty()) return null;

        return readStackFromNBT(slotNBT);
    }

    public void loadCacheFromNBT() {
        cachedFilterKeys.clear();

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(getNBTKey(), Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < filters.tagCount(); i++) {
            NBTTagCompound slotNBT = filters.getCompoundTagAt(i);

            if (!slotNBT.isEmpty()) {
                T stack = readStackFromNBT(slotNBT);
                K key = createKey(stack);
                if (key != null) cachedFilterKeys.add(key);
            }
        }
    }

    public void setStackInSlot(int slot, @Nullable T stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;

        NBTTagCompound cellNBT = Platform.openNbtData(cellStack);
        NBTTagList filters = cellNBT.getTagList(getNBTKey(), Constants.NBT.TAG_COMPOUND);

        // Ensure the list has enough entries
        while (filters.tagCount() <= slot) filters.appendTag(new NBTTagCompound());

        // Store as single-unit ghost
        if (isStackEmpty(stack)) {
            filters.set(slot, new NBTTagCompound());
        } else {
            T ghost = createGhostCopy(stack);
            NBTTagCompound slotNBT = new NBTTagCompound();
            writeStackToNBT(ghost, slotNBT);
            filters.set(slot, slotNBT);
        }

        cellNBT.setTag(getNBTKey(), filters);

        // Update cache
        loadCacheFromNBT();
    }

    public boolean isInFilter(@Nonnull K key) {
        return cachedFilterKeys.contains(key);
    }

    /**
     * Check if a stack is in the filter using the key lookup.
     * Subclasses can override or add convenience methods for checking by other types.
     *
     * @param stack The stack to check
     * @return true if in filter
     */
    public boolean isStackInFilter(@Nullable T stack) {
        if (isStackEmpty(stack)) return false;

        K key = createKey(stack);
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
        cellNBT.setTag(getNBTKey(), new NBTTagList());
        loadCacheFromNBT();
    }
}
