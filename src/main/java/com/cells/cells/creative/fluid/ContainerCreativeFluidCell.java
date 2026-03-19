package com.cells.cells.creative.fluid;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.container.AEBaseContainer;
import appeng.fluids.container.IFluidSyncContainer;
import appeng.fluids.helper.FluidSyncHelper;
import appeng.util.Platform;


/**
 * Container for the Creative ME Fluid Cell GUI.
 * <p>
 * Provides a 9x7 grid of fluid filter slots for setting filter fluids.
 * Only accessible in creative mode.
 */
public class ContainerCreativeFluidCell extends AEBaseContainer implements IFluidSyncContainer {

    /** The hand holding the cell, used to lock the slot */
    private final EnumHand hand;

    /** Index of the held cell in the player's inventory (-1 for offhand) */
    private final int lockedSlotIndex;

    /** The cell ItemStack - cached reference to the held cell */
    private final ItemStack cellStack;

    /** The filter slot handler backed by cell NBT */
    private final CreativeFluidCellFilterHandler filterHandler;

    /** Helper for syncing fluids to client */
    private FluidSyncHelper filterSync;

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

    public ContainerCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.lockedSlotIndex = (hand == EnumHand.MAIN_HAND) ? playerInv.currentItem : -1;
        this.cellStack = playerInv.player.getHeldItem(hand);
        this.filterHandler = new CreativeFluidCellFilterHandler(cellStack);
        this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Must be in creative mode to interact
        if (!playerIn.isCreative()) return false;

        // Cell must still be in the player's hand
        ItemStack held = playerIn.getHeldItem(hand);

        return !held.isEmpty() && held.getItem() instanceof ItemCreativeFluidCell;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Sync fluid filters to clients
        if (Platform.isServer()) this.filterSync.sendDiff(this.listeners);
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);
        this.filterSync.sendFull(Collections.singleton(listener));
    }

    /**
     * Receive fluid slot updates from server (implements IFluidSyncContainer).
     */
    @Override
    public void receiveFluidSlots(Map<Integer, IAEFluidStack> fluids) {
        this.filterSync.readPacket(fluids);
    }

    /**
     * Set a fluid filter at a specific slot (called from GUI/packet).
     */
    public void setFluidFilter(int slot, FluidStack fluid) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        filterHandler.setFluidInSlot(slot, fluid);

        // Rebuild sync helper so it notices the change
        this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);
    }

    /**
     * Add a fluid filter at the first available slot (for quick-add).
     * Returns true if successful.
     */
    public boolean addFluidFilter(FluidStack fluid) {
        if (fluid == null) return false;

        // Check if already exists
        for (int i = 0; i < FILTER_SLOTS; i++) {
            FluidStack existing = filterHandler.getFluidInSlot(i);
            if (existing != null && existing.isFluidEqual(fluid)) return false;
        }

        // Find first empty slot
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filterHandler.getFluidInSlot(i) == null) {
                filterHandler.setFluidInSlot(i, fluid);
                this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);
                return true;
            }
        }

        return false;
    }

    /**
     * Clear all filter slots.
     */
    public void clearAllFilters() {
        filterHandler.clearAll();

        // Rebuild sync helper
        this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);
    }

    /**
     * Get the filter handler for external access.
     */
    public CreativeFluidCellFilterHandler getFilterHandler() {
        return filterHandler;
    }

    /**
     * Handle shift-click: extract fluid from container and add as filter.
     * The actual item stays in place (return empty), only the filter is set.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        // Fluid slots would be indices 0-62, player slots start at 63
        // But we have no SlotFake here, so all slots are player inventory
        net.minecraft.inventory.Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Try to extract fluid from the item
        ItemStack clickedStack = slot.getStack();
        FluidStack fluid = FluidUtil.getFluidContained(clickedStack);
        if (fluid != null) addFluidFilter(fluid);

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }
}
