package com.cells.util;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * Singleton empty IItemHandler implementation.
 * <p>
 * Used to avoid creating duplicate anonymous IItemHandler instances
 * in cells that don't support config or upgrade inventories.
 */
public final class EmptyItemHandler implements IItemHandler {

    /** Singleton instance */
    public static final EmptyItemHandler INSTANCE = new EmptyItemHandler();

    private EmptyItemHandler() {
        // Private constructor for singleton
    }

    @Override
    public int getSlots() {
        return 0;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return false;
    }
}
