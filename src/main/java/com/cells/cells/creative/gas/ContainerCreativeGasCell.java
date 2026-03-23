package com.cells.cells.creative.gas;

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

import appeng.util.Platform;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.container.sync.IGasSyncContainer;
import com.mekeng.github.util.helpers.GasSyncHelper;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.gui.QuickAddHelper;


/**
 * Container for the Creative ME Gas Cell GUI.
 * <p>
 * Provides a 9x7 grid of gas filter slots.
 * Only accessible in creative mode.
 */
public class ContainerCreativeGasCell extends AbstractCreativeCellContainer<CreativeGasCellFilterHandler>
        implements IGasSyncContainer {

    /** Helper for syncing gases to client */
    private GasSyncHelper filterSync;

    public ContainerCreativeGasCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeGasCellFilterHandler(playerInv.player.getHeldItem(hand)));
        this.filterSync = GasSyncHelper.create(new CreativeGasCellTankAdapter(filterHandler), 0);

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeGasCell.class;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Sync gas filters to clients
        if (Platform.isServer()) this.filterSync.sendDiff(this.listeners);
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);
        this.filterSync.sendFull(Collections.singleton(listener));
    }

    /**
     * Receive gas slot updates from server (implements IGasSyncContainer).
     */
    @Override
    public void receiveGasSlots(Map<Integer, IAEGasStack> gases) {
        this.filterSync.readPacket(gases);
    }

    /**
     * Set a gas filter at a specific slot (called from GUI/packet).
     */
    public void setGasFilter(int slot, GasStack gas) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;

        filterHandler.setGasInSlot(slot, gas);

        // Rebuild sync helper so it notices the change
        this.filterSync = GasSyncHelper.create(new CreativeGasCellTankAdapter(filterHandler), 0);
    }

    /**
     * Add a gas filter at the first available slot (for quick-add).
     * Returns true if successful.
     */
    public boolean addToFilter(GasStack gas) {
        if (gas == null) return false;

        // Check if already exists
        if (filterHandler.isInFilter(gas)) return false;

        // Find first empty slot
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (filterHandler.getGasInSlot(i) == null) {
                filterHandler.setGasInSlot(i, gas);
                this.filterSync = GasSyncHelper.create(new CreativeGasCellTankAdapter(filterHandler), 0);
                return true;
            }
        }

        return false;
    }

    @Override
    public void clearAllFilters() {
        filterHandler.clearAll();

        // Rebuild sync helper
        this.filterSync = GasSyncHelper.create(new CreativeGasCellTankAdapter(filterHandler), 0);
    }

    /**
     * Handle shift-click: extract gas from container and add as filter.
     * The actual item stays in place (return empty), only the filter is set.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Try to extract gas from the item using QuickAddHelper
        ItemStack clickedStack = slot.getStack();
        GasStack gas = QuickAddHelper.getGasFromItemStack(clickedStack);
        if (gas != null) addToFilter(gas);

        // Return empty so the actual item stays in place
        return ItemStack.EMPTY;
    }
}
