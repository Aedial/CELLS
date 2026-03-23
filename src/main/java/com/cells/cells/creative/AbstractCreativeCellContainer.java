package com.cells.cells.creative;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.container.AEBaseContainer;


/**
 * Abstract base container for Creative Cell GUIs (item and fluid variants).
 * <p>
 * Provides common functionality:
 * - Player inventory binding with locked cell slot
 * - Creative-only access validation
 * - Grid layout constants
 *
 * @param <H> Type of the filter handler used by this container
 */
public abstract class AbstractCreativeCellContainer<H> extends AEBaseContainer {

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

    /** The hand holding the cell, used to lock the slot */
    protected final EnumHand hand;

    /** Index of the held cell in the player's inventory (-1 for offhand) */
    protected final int lockedSlotIndex;

    /** The cell ItemStack - cached reference to the held cell */
    protected final ItemStack cellStack;

    /** The filter handler backed by cell NBT */
    protected final H filterHandler;

    protected AbstractCreativeCellContainer(InventoryPlayer playerInv, EnumHand hand, H filterHandler) {
        super(playerInv, null, null);
        this.hand = hand;
        this.lockedSlotIndex = (hand == EnumHand.MAIN_HAND) ? playerInv.currentItem : -1;
        this.cellStack = playerInv.player.getHeldItem(hand);
        this.filterHandler = filterHandler;
    }

    /**
     * Get the expected cell item class for validation.
     */
    protected abstract Class<? extends Item> getCellItemClass();

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Must be in creative mode to interact
        if (!playerIn.isCreative()) return false;

        // Cell must still be in the player's hand
        ItemStack held = playerIn.getHeldItem(hand);
        if (held.isEmpty()) return false;

        return getCellItemClass().isInstance(held.getItem());
    }

    /**
     * Prevent moving the held cell via hotbar swap, shift-click, etc.
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @Nonnull EntityPlayer player) {
        // Prevent interactions with the locked slot (the cell in hand)
        if (lockedSlotIndex >= 0 && slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.inventory instanceof InventoryPlayer) {
                int playerSlot = slot.getSlotIndex();
                if (playerSlot == lockedSlotIndex) return ItemStack.EMPTY;
            }
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * Clear all filter slots.
     */
    public abstract void clearAllFilters();

    /**
     * Get the filter handler for external access.
     */
    public H getFilterHandler() {
        return filterHandler;
    }
}
