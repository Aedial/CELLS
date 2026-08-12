package com.cells.cells.creative.essentia;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Creative ME Essentia Cell GUI.
 * <p>
 * Provides a 9x7 grid of essentia filter slots.
 * Uses unified PacketResourceSlot for sync. Only accessible in creative mode.
 */
public class ContainerCreativeEssentiaCell extends AbstractCreativeCellSyncContainer<CreativeEssentiaCellFilterHandler, IAEEssentiaStack> {

    public ContainerCreativeEssentiaCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeEssentiaCellFilterHandler(playerInv.player.getHeldItem(hand)));
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
    protected IAEEssentiaStack getSyncStack(int slot) {
        EssentiaStack raw = filterHandler.getEssentiaInSlot(slot);
        return raw != null ? AEEssentiaStack.fromEssentiaStack(raw) : null;
    }

    @Override
    protected void setSyncStack(int slot, @Nullable IAEEssentiaStack stack) {
        EssentiaStack raw = stack != null ? stack.getStack() : null;
        filterHandler.setEssentiaInSlot(slot, raw);
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEEssentiaStack a, @Nullable IAEEssentiaStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean filterContains(@Nonnull IAEEssentiaStack stack) {
        EssentiaStack raw = stack.getStack();
        return raw != null && filterHandler.isInFilter(raw);
    }

    @Override
    @Nullable
    protected IAEEssentiaStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        EssentiaStack raw = QuickAddHelper.getEssentiaFromItemStack(container);
        return raw != null ? AEEssentiaStack.fromEssentiaStack(raw) : null;
    }
}
