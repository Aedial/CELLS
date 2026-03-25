package com.cells.blocks.interfacebase.item;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.storage.data.IAEItemStack;

import com.cells.blocks.interfacebase.IResourceInterfaceHost;
import com.cells.gui.slots.ItemStorageSlot;
import com.cells.util.ItemStackKey;


/**
 * Extended interface for Item Interface hosts (both import and export, both tile and part).
 * Provides access to item filter/storage inventories and item-specific operations.
 * <p>
 * Filter and storage methods are inherited from {@link IResourceInterfaceHost}
 * which provides default implementations delegating to the typed logic.
 */
public interface IItemInterfaceHost
    extends IResourceInterfaceHost<IAEItemStack, ItemStackKey>,
            ItemStorageSlot.IItemStorageHost {

    /**
     * @return The filter inventory (ghost items, 1 stack size each)
     */
    IItemHandlerModifiable getFilterInventory();

    /**
     * @return The storage inventory (actual items)
     */
    IItemHandlerModifiable getStorageInventory();

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     * Import uses filter-to-slot mapping; export checks direct filter match.
     */
    boolean isItemValidForSlot(int slot, ItemStack stack);

    @Override
    @Nullable
    default ItemStack getItemInStorage(int slotIndex) {
        IItemHandler storageInv = getStorageInventory();
        if (slotIndex < 0 || slotIndex >= storageInv.getSlots()) return null;

        ItemStack stack = storageInv.getStackInSlot(slotIndex);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    @Nullable
    default ItemStackKey createKey(@Nullable IAEItemStack stack) {
        if (stack == null) return null;
        return ItemStackKey.of(stack.getDefinition());
    }

    @Override
    default String getTypeLocalizationKey() {
        return "cells.type.item";
    }
}
