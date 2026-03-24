package com.cells.cells.creative.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

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
public class ContainerCreativeCell extends AbstractCreativeCellSyncContainer<CreativeCellFilterHandler, ItemStack> {

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
    protected ItemStack getSyncStack(int slot) {
        ItemStack stack = filterHandler.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected void setSyncStack(int slot, @Nullable ItemStack stack) {
        filterHandler.setStackInSlot(slot, stack != null ? stack : ItemStack.EMPTY);
    }

    @Override
    @Nullable
    protected ItemStack copySyncStack(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() ? stack.copy() : null;
    }

    @Override
    protected boolean syncStacksEqual(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        if (b == null || b.isEmpty()) return false;
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    protected boolean isSyncStackEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    protected boolean filterContains(@Nonnull ItemStack stack) {
        return filterHandler.isInFilter(stack);
    }

    @Override
    @Nullable
    protected ItemStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        // For items, the container IS the resource
        return container.isEmpty() ? null : container;
    }
}
