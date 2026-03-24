package com.cells.blocks.interfacebase.item;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.gui.slots.ItemStorageSlot;
import com.cells.util.ItemStackKey;


/**
 * Extended interface for Item Interface hosts (both import and export, both tile and part).
 * Provides access to item filter/storage/upgrade inventories and configuration.
 * <p>
 * The {@link #isExport()} method from {@link com.cells.blocks.interfacebase.IInterfaceHost} determines whether
 * this is an import or export interface, which affects filter clearing behavior
 * and available upgrades.
 * <p>
 * Pagination, clearing, and slot info methods are inherited from {@link IFilterableInterfaceHost}
 * with default implementations that delegate to {@link #getInterfaceLogic()}.
 */
public interface IItemInterfaceHost
    extends IFilterableInterfaceHost<ItemStack, ItemStackKey>,
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
     * @return The upgrade inventory
     */
    AppEngInternalInventory getUpgradeInventory();

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     * Import uses filter-to-slot mapping; export checks direct filter match.
     */
    boolean isItemValidForSlot(int slot, ItemStack stack);

    /**
     * Refresh cached upgrade status after upgrade slot changes.
     */
    void refreshUpgrades();

    /**
     * Check if an item is a valid upgrade for this interface.
     */
    boolean isValidUpgrade(ItemStack stack);

    /**
     * @return The number of installed capacity upgrades.
     */
    int getInstalledCapacityUpgrades();

    /**
     * @return The starting slot index for the current page.
     */
    int getCurrentPageStartSlot();

    // ================================= Import-specific Upgrades =================================

    /**
     * @return true if the overflow upgrade is installed (import only).
     */
    default boolean hasOverflowUpgrade() {
        return false;
    }

    /**
     * @return true if the trash unselected upgrade is installed (import only).
     */
    default boolean hasTrashUnselectedUpgrade() {
        return false;
    }

    // ============================== IFilterableInterfaceHost Implementation ==============================

    @Override
    @Nullable
    default ItemStack getFilter(int slot) {
        IItemHandler filterInv = getFilterInventory();
        if (slot < 0 || slot >= filterInv.getSlots()) return null;

        ItemStack stack = filterInv.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    default void setFilter(int slot, @Nullable ItemStack stack) {
        IItemHandlerModifiable filterInv = getFilterInventory();
        if (slot < 0 || slot >= filterInv.getSlots()) return;

        if (stack == null || stack.isEmpty()) {
            filterInv.setStackInSlot(slot, ItemStack.EMPTY);
        } else {
            ItemStack ghost = stack.copy();
            ghost.setCount(1);
            filterInv.setStackInSlot(slot, ghost);
        }
    }

    @Override
    default boolean isStorageEmpty(int slot) {
        IItemHandler storageInv = getStorageInventory();
        if (slot < 0 || slot >= storageInv.getSlots()) return true;

        return storageInv.getStackInSlot(slot).isEmpty();
    }

    // ============================== ItemStorageSlot.IItemStorageHost Implementation ==============================

    @Override
    @Nullable
    default ItemStack getItemInStorage(int slotIndex) {
        IItemHandler storageInv = getStorageInventory();
        if (slotIndex < 0 || slotIndex >= storageInv.getSlots()) return null;

        ItemStack stack = storageInv.getStackInSlot(slotIndex);
        return stack.isEmpty() ? null : stack;
    }

    // ============================== IFilterableInterfaceHost Implementation (continued) ==============================

    @Override
    @Nullable
    default ItemStackKey createKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ItemStackKey.of(stack);
    }

    @Override
    default String getTypeLocalizationKey() {
        return "cells.type.item";
    }
}
