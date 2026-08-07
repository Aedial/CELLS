package com.cells.cells.creative.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Creative ME Cell GUI.
 * <p>
 * Provides a 9x7 grid of filter slots for setting filter items.
 * Uses unified PacketResourceSlot for sync. Only accessible in creative mode.
 * <p>
 * Note: Filter slots are implemented as custom GUI slots (ItemFilterSlot),
 * not container SlotFake, for consistency with other interfaces.
 */
public class ContainerCreativeCell extends AbstractCreativeCellSyncContainer<CreativeCellFilterHandler, IAEItemStack> {

    public ContainerCreativeCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeCellFilterHandler(playerInv.player.getHeldItem(hand)));
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeCell.class;
    }

    // ================================= Sync Methods =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    @Nullable
    protected IAEItemStack getSyncStack(int slot) {
        ItemStack stack = filterHandler.getStackInSlot(slot);
        if (stack.isEmpty()) return null;
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
    }

    @Override
    protected void setSyncStack(int slot, @Nullable IAEItemStack stack) {
        ItemStack raw = stack != null ? stack.getDefinition() : ItemStack.EMPTY;
        filterHandler.setStackInSlot(slot, raw);
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean filterContains(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();
        return filterHandler.isInFilter(definition);
    }

    @Override
    @Nullable
    protected IAEItemStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        ItemStack filterStack = QuickAddHelper.getItemFromItemStack(container);
        if (filterStack.isEmpty()) return null;

        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(filterStack);
    }
}
