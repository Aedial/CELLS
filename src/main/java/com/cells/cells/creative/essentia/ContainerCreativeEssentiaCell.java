package com.cells.cells.creative.essentia;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Creative ME Essentia Cell GUI.
 * <p>
 * Provides a 9x7 grid of essentia filter slots.
 * Uses unified PacketResourceSlot for sync. Only accessible in creative mode.
 */
public class ContainerCreativeEssentiaCell extends AbstractCreativeCellSyncContainer<CreativeEssentiaCellFilterHandler, EssentiaStack> {

    public ContainerCreativeEssentiaCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeEssentiaCellFilterHandler(playerInv.player.getHeldItem(hand)));

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeEssentiaCell.class;
    }

    // ================================= Sync Methods =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.ESSENTIA;
    }

    @Override
    @Nullable
    protected EssentiaStack getSyncStack(int slot) {
        return filterHandler.getEssentiaInSlot(slot);
    }

    @Override
    protected void setSyncStack(int slot, @Nullable EssentiaStack stack) {
        filterHandler.setEssentiaInSlot(slot, stack);
    }

    @Override
    @Nullable
    protected EssentiaStack copySyncStack(@Nullable EssentiaStack stack) {
        return stack != null ? stack.copy() : null;
    }

    @Override
    protected boolean syncStacksEqual(@Nullable EssentiaStack a, @Nullable EssentiaStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean isSyncStackEmpty(@Nullable EssentiaStack stack) {
        return stack == null;
    }

    @Override
    protected boolean filterContains(@Nonnull EssentiaStack stack) {
        return filterHandler.isInFilter(stack);
    }

    @Override
    @Nullable
    protected EssentiaStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        return QuickAddHelper.getEssentiaFromItemStack(container);
    }

    // ================================= Filter Operations =================================

    /**
     * Set an essentia filter at a specific slot.
     */
    public void setEssentiaFilter(int slot, EssentiaStack essentia) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        filterHandler.setEssentiaInSlot(slot, essentia);
    }
}
