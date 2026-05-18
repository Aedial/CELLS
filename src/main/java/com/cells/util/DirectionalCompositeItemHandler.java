package com.cells.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;


/**
 * Combines separate import and export handlers without overlapping their visible slots.
 */
public class DirectionalCompositeItemHandler implements IItemHandler {

    private final IItemHandler importHandler;
    private final IItemHandler exportHandler;
    private final int importSlotCount;
    private final int exportSlotCount;

    public DirectionalCompositeItemHandler(IItemHandler importHandler, IItemHandler exportHandler) {
        this.importHandler = importHandler;
        this.exportHandler = exportHandler;
        this.importSlotCount = importHandler.getSlots();
        this.exportSlotCount = exportHandler.getSlots();
    }

    @Override
    public int getSlots() {
        return this.importSlotCount + this.exportSlotCount;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0) return ItemStack.EMPTY;

        // TODO: For piping that iterate over all slots to match items, what would be the most efficient?
        //       The current behavior prioritizes import slots, but I feel we could gain some efficiency
        //       by returning the empty insert slot (early insertion), then returning export slots,
        //       and finally returning the empty extract slot (late extraction).
        //       This way, extract does not have to iterate over the import slots (ideally).
        if (slot < this.importSlotCount) {
            return this.importHandler.getStackInSlot(slot);
        }

        int exportSlot = slot - this.importSlotCount;
        if (exportSlot < this.exportSlotCount) {
            return this.exportHandler.getStackInSlot(exportSlot);
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || this.importSlotCount <= 0) return stack;

        // Slot is ignored (slotless), but eh, might as well pass it if we need it later
        return this.importHandler.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < this.importSlotCount) return ItemStack.EMPTY;

        int exportSlot = slot - this.importSlotCount;
        if (exportSlot < 0 || exportSlot >= this.exportSlotCount) return ItemStack.EMPTY;

        return this.exportHandler.extractItem(exportSlot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0) return 0;

        if (slot < this.importSlotCount) return this.importHandler.getSlotLimit(slot);

        int exportSlot = slot - this.importSlotCount;
        if (exportSlot < this.exportSlotCount) {
            return this.exportHandler.getSlotLimit(exportSlot);
        }

        return 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty() || this.importSlotCount <= 0) return false;

        if (slot < 0 || slot >= this.importSlotCount) slot = 0;

        return this.importHandler.isItemValid(slot, stack);
    }
}