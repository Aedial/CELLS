package com.cells.cells.creative.item;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cells.cells.creative.AbstractCreativeCellFilterHandler;
import com.cells.integration.jei.cellview.CellViewHelper;
import com.cells.util.ItemStackKey;


/**
 * NBT-backed item handler for storing filter items in a Creative Cell.
 * <p>
 * Stores 63 filter slots (9x7 grid) as ghost items in the cell's NBT.
 * Each slot holds a single-count copy of an item for filtering purposes.
 * <p>
 * Implements IItemHandlerModifiable for compatibility with AE2's config inventory API.
 */
public class CreativeCellFilterHandler
        extends AbstractCreativeCellFilterHandler<ItemStack, ItemStackKey>
        implements IItemHandlerModifiable {

    private static final String NBT_KEY_FILTERS = "CreativeFilters";

    public CreativeCellFilterHandler(@Nonnull ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected String getNBTKey() {
        return NBT_KEY_FILTERS;
    }

    @Override
    protected ItemStack readStackFromNBT(@Nonnull NBTTagCompound nbt) {
        return new ItemStack(nbt);
    }

    @Override
    protected void writeStackToNBT(@Nonnull ItemStack stack, @Nonnull NBTTagCompound nbt) {
        stack.writeToNBT(nbt);
    }

    @Override
    protected ItemStackKey createKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ItemStackKey.of(stack);
    }

    @Override
    @Nonnull
    protected ItemStack createGhostCopy(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    protected boolean isStackEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        ItemStack result = super.getStackInSlot(slot);
        return result != null ? result : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (stack == null) stack = ItemStack.EMPTY;

        // Reject invalid stacks (e.g., storage cells)
        if (!stack.isEmpty() && !isItemValid(slot, stack)) return;

        super.setStackInSlot(slot, stack);
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
        // Reject storage cells - they shouldn't be used as filters
        // Use CellViewHelper.isCell() for the most generic check possible
        return stack.isEmpty() || !CellViewHelper.isCell(stack);
    }

    /**
     * Check if an item stack is in the filter.
     */
    public boolean isInFilter(@Nonnull ItemStack stack) {
        return isStackInFilter(stack);
    }
}
