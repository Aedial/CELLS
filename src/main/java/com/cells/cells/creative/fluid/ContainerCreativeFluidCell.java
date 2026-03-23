package com.cells.cells.creative.fluid;

import java.util.Collections;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.container.IFluidSyncContainer;
import appeng.fluids.helper.FluidSyncHelper;
import appeng.util.Platform;

import com.cells.cells.creative.AbstractCreativeCellContainer;


/**
 * Container for the Creative ME Fluid Cell GUI.
 * <p>
 * Provides a 9x7 grid of fluid filter slots for setting filter fluids.
 * Only accessible in creative mode.
 */
public class ContainerCreativeFluidCell extends AbstractCreativeCellContainer<CreativeFluidCellFilterHandler>
        implements IFluidSyncContainer {

    /** Helper for syncing fluids to client */
    private FluidSyncHelper filterSync;

    public ContainerCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeFluidCellFilterHandler(playerInv.player.getHeldItem(hand)));
        this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeFluidCell.class;
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
    public boolean addToFilter(FluidStack fluid) {
        if (fluid == null) return false;

        // Check if already exists
        if (filterHandler.isInFilter(fluid)) return false;

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

    @Override
    public void clearAllFilters() {
        filterHandler.clearAll();

        // Rebuild sync helper
        this.filterSync = new FluidSyncHelper(new CreativeFluidCellTankAdapter(filterHandler), 0);
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
        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Try to extract fluid from the item
        ItemStack clickedStack = slot.getStack();
        FluidStack fluid = FluidUtil.getFluidContained(clickedStack);
        if (fluid != null) addToFilter(fluid);

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }
}
