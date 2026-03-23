package com.cells.cells.creative.item;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.container.slot.SlotFake;

import com.cells.cells.creative.AbstractCreativeCellContainer;


/**
 * Container for the Creative ME Cell GUI.
 * <p>
 * Provides a 9x7 grid of fake slots for setting filter items.
 * Only accessible in creative mode.
 */
public class ContainerCreativeCell extends AbstractCreativeCellContainer<CreativeCellFilterHandler> {

    public ContainerCreativeCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeCellFilterHandler(playerInv.player.getHeldItem(hand)));

        // FIXME: could add a method to create a slot

        // Add 9x7 filter slots
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int slotIndex = row * GRID_COLS + col;
                int x = FILTER_START_X + col * 18;
                int y = FILTER_START_Y + row * 18;

                addSlotToContainer(new SlotFake(filterHandler, slotIndex, x, y));
            }
        }

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeCell.class;
    }

    /**
     * Custom handling for filter slots to support ghost item setting.
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @Nonnull EntityPlayer player) {
        // Handle filter slot clicks (ghost items)
        if (slotId >= 0 && slotId < FILTER_SLOTS && clickTypeIn == ClickType.PICKUP) {
            return handleFilterSlotClick(slotId, player);
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * Handle left/right click on a filter slot.
     * Sets or clears the ghost item filter.
     */
    private ItemStack handleFilterSlotClick(int slotId, EntityPlayer player) {
        ItemStack cursor = player.inventory.getItemStack();
        ItemStack currentFilter = filterHandler.getStackInSlot(slotId);

        if (cursor.isEmpty()) {
            // Empty cursor - clear the filter
            if (!currentFilter.isEmpty()) {
                filterHandler.setStackInSlot(slotId, ItemStack.EMPTY);
            }
        } else {
            // Set the filter to the cursor item (as ghost)
            filterHandler.setStackInSlot(slotId, cursor);
        }

        // Return current cursor unchanged (ghost slots don't consume items)
        return cursor;
    }

    /**
     * Add an item filter at the first available slot (for quick-add).
     * Returns true if successful.
     */
    public boolean addToFilter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // Check if already exists
        if (filterHandler.isInFilter(stack)) return false;

        // Find first empty slot
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filterHandler.getStackInSlot(i).isEmpty()) {
                filterHandler.setStackInSlot(i, stack);
                return true;
            }
        }

        return false;
    }

    @Override
    public void clearAllFilters() {
        filterHandler.clearAll();
    }

    /**
     * Handle shift-click: add the clicked item as a ghost filter (if from player inventory).
     * The actual item stays in place (return empty), only the filter is set.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Only process shift-clicks from the player inventory (not from filter slots)
        if (slotIndex < FILTER_SLOTS) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        if (!clickedStack.isEmpty()) addToFilter(clickedStack);

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }
}
