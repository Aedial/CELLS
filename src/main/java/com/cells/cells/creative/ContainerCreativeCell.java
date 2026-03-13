package com.cells.cells.creative;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotFake;


/**
 * Container for the Creative ME Cell GUI.
 * <p>
 * Provides a 9x7 grid of fake slots for setting filter items.
 * Only accessible in creative mode.
 */
public class ContainerCreativeCell extends AEBaseContainer {

    /** The hand holding the cell, used to lock the slot */
    private final EnumHand hand;

    /** Index of the held cell in the player's inventory (-1 for offhand) */
    private final int lockedSlotIndex;

    /** The cell ItemStack - cached reference to the held cell */
    private final ItemStack cellStack;

    /** The filter slot handler backed by cell NBT */
    private final CreativeCellFilterHandler filterHandler;

    /** Number of columns in the filter grid */
    public static final int GRID_COLS = 9;

    /** Number of rows in the filter grid */
    public static final int GRID_ROWS = 7;

    /** Total number of filter slots */
    public static final int FILTER_SLOTS = GRID_COLS * GRID_ROWS;

    /** X position where filter slots start */
    public static final int FILTER_START_X = 8;

    /** Y position where filter slots start */
    public static final int FILTER_START_Y = 19;

    public ContainerCreativeCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.lockedSlotIndex = (hand == EnumHand.MAIN_HAND) ? playerInv.currentItem : -1;
        this.cellStack = playerInv.player.getHeldItem(hand);
        this.filterHandler = new CreativeCellFilterHandler(cellStack);

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
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Must be in creative mode to interact
        if (!playerIn.isCreative()) return false;

        // Cell must still be in the player's hand
        ItemStack held = playerIn.getHeldItem(hand);

        return !held.isEmpty() && held.getItem() instanceof ItemCreativeCell;
    }

    /**
     * Prevent moving the held cell via hotbar swap, shift-click, etc.
     * Custom handling for filter slots to support ghost item setting.
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @Nonnull EntityPlayer player) {
        // Prevent interactions with the locked slot (the cell in hand) if the container is open
        if (lockedSlotIndex >= 0 && slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.inventory instanceof InventoryPlayer) {
                int playerSlot = slot.getSlotIndex();
                if (playerSlot == lockedSlotIndex) return ItemStack.EMPTY;
            }
        }

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
     * Clear all filter slots.
     */
    public void clearAllFilters() {
        filterHandler.clearAll();
    }

    /**
     * Get the filter handler for external access.
     */
    public CreativeCellFilterHandler getFilterHandler() {
        return filterHandler;
    }

    /**
     * Override transferStackInSlot to prevent shift-clicking items into filter slots.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // Don't allow shift-click transfers to filter slots
        // FIXME: add filter when shift-clicking, then return empty
        return ItemStack.EMPTY;
    }
}
