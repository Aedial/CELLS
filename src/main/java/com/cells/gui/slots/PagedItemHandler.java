package com.cells.gui.slots;

import javax.annotation.Nonnull;
import java.util.function.IntSupplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * A wrapper around an IItemHandler that provides a view of a specific page
 * of slots. The actual slot index accessed is offset by (currentPage * slotsPerPage).
 * <p>
 * This allows pagination by having slot indices 0-35 always map to the current
 * page's actual inventory indices.
 */
public class PagedItemHandler implements IItemHandler {

    private final IItemHandler delegate;
    private final int slotsPerPage;
    private final IntSupplier currentPageSupplier;
    private final IntSupplier totalPagesSupplier;

    /**
     * Create a paged view of an inventory.
     *
     * @param delegate The underlying inventory
     * @param slotsPerPage Number of slots per page (typically 36)
     * @param currentPageSupplier Supplier for current page index (0-based)
     * @param totalPagesSupplier Supplier for total number of pages
     */
    public PagedItemHandler(IItemHandler delegate, int slotsPerPage,
                            IntSupplier currentPageSupplier, IntSupplier totalPagesSupplier) {
        this.delegate = delegate;
        this.slotsPerPage = slotsPerPage;
        this.currentPageSupplier = currentPageSupplier;
        this.totalPagesSupplier = totalPagesSupplier;
    }

    /**
     * Get the actual inventory index for a given displayed slot.
     */
    private int getActualSlot(int displaySlot) {
        return displaySlot + (currentPageSupplier.getAsInt() * slotsPerPage);
    }

    @Override
    public int getSlots() {
        // Always expose one page worth of slots to the GUI
        return slotsPerPage;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        int actualSlot = getActualSlot(slot);
        if (actualSlot >= delegate.getSlots()) return ItemStack.EMPTY;

        return delegate.getStackInSlot(actualSlot);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        int actualSlot = getActualSlot(slot);
        if (actualSlot >= delegate.getSlots()) return stack;

        return delegate.insertItem(actualSlot, stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int actualSlot = getActualSlot(slot);
        if (actualSlot >= delegate.getSlots()) return ItemStack.EMPTY;

        return delegate.extractItem(actualSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int actualSlot = getActualSlot(slot);
        if (actualSlot >= delegate.getSlots()) return 0;

        return delegate.getSlotLimit(actualSlot);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        int actualSlot = getActualSlot(slot);
        if (actualSlot >= delegate.getSlots()) return false;

        return delegate.isItemValid(actualSlot, stack);
    }

    /**
     * Get the actual slot index in the underlying inventory for a displayed slot.
     */
    public int getActualSlotIndex(int displaySlot) {
        return getActualSlot(displaySlot);
    }
}
