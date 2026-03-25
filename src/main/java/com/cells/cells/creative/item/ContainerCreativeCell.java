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

        // Filter slots are handled as custom GUI slots in the GUI class
        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
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
    @Nullable
    protected IAEItemStack copySyncStack(@Nullable IAEItemStack stack) {
        return stack != null ? stack.copy() : null;
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean isSyncStackEmpty(@Nullable IAEItemStack stack) {
        return stack == null;
    }

    @Override
    protected boolean filterContains(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();
        return filterHandler.isInFilter(definition);
    }

    @Override
    @Nullable
    protected IAEItemStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        // For items, the container IS the resource - convert to IAEItemStack
        if (container.isEmpty()) return null;
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(container);
    }

    // ================================= GUI Support =================================

    /**
     * Get filter at slot as IAEItemStack for unified GUI slot rendering.
     * This provides the same interface as item interfaces use.
     */
    @Nullable
    public IAEItemStack getFilter(int slot) {
        return getSyncStack(slot);
    }
}
