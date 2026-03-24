package com.cells.cells.creative.gas;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Creative ME Gas Cell GUI.
 * <p>
 * Provides a 9x7 grid of gas filter slots.
 * Uses unified PacketResourceSlot for sync. Only accessible in creative mode.
 */
public class ContainerCreativeGasCell extends AbstractCreativeCellSyncContainer<CreativeGasCellFilterHandler, IAEGasStack> {

    public ContainerCreativeGasCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeGasCellFilterHandler(playerInv.player.getHeldItem(hand)));

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeGasCell.class;
    }

    // ================================= Sync Methods =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.GAS;
    }

    @Override
    @Nullable
    protected IAEGasStack getSyncStack(int slot) {
        GasStack gas = filterHandler.getGasInSlot(slot);
        return gas != null ? AEGasStack.of(gas) : null;
    }

    @Override
    protected void setSyncStack(int slot, @Nullable IAEGasStack stack) {
        filterHandler.setGasInSlot(slot, stack != null ? stack.getGasStack() : null);
    }

    @Override
    @Nullable
    protected IAEGasStack copySyncStack(@Nullable IAEGasStack stack) {
        return stack != null ? stack.copy() : null;
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEGasStack a, @Nullable IAEGasStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean isSyncStackEmpty(@Nullable IAEGasStack stack) {
        return stack == null;
    }

    @Override
    protected boolean filterContains(@Nonnull IAEGasStack stack) {
        return filterHandler.isInFilter(stack.getGasStack());
    }

    @Override
    @Nullable
    protected IAEGasStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        GasStack gas = QuickAddHelper.getGasFromItemStack(container);
        return gas != null ? AEGasStack.of(gas) : null;
    }

    // ================================= Filter Operations =================================

    /**
     * Set a gas filter at a specific slot (called from GUI/packet).
     */
    public void setGasFilter(int slot, GasStack gas) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        filterHandler.setGasInSlot(slot, gas);
    }
}
