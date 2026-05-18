package com.cells.blocks.combinedinterface;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.gui.QuickAddHelper;
import com.cells.items.ItemRecoveryContainer;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;


/**
 * Helper class for item-specific storage interactions in the combined interface container.
 * <p>
 * Extracted from {@link com.cells.blocks.interfacebase.item.ContainerItemInterface} to be
 * invoked by {@link ContainerCombinedInterface} when the item tab is active.
 */
final class CombinedContainerItemHelper {

    private CombinedContainerItemHelper() {}

    /**
     * Handle direct storage interaction (pickup/setdown) for item tab.
     * Same logic as ContainerItemInterface.handleStorageInteraction.
     */
    static boolean handleStorageInteraction(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            EntityPlayerMP player,
            int storageSlot,
            boolean halfStack
    ) {
        ItemInterfaceLogic itemLogic = host.getItemLogic();
        IItemHandlerModifiable storage = itemLogic.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack held = player.inventory.getItemStack();
        ItemStack stored = storage.getStackInSlot(storageSlot);
        boolean isExport = host.isExport();

        if (held.isEmpty()) {
            // Empty cursor: export only extracts
            if (isExport && !stored.isEmpty()) {
                long storedAmount = itemLogic.getSlotAmount(storageSlot);

                int extractAmount;
                if (halfStack) {
                    extractAmount = (int) Math.max(1, Math.min(storedAmount / 2, 32));
                } else {
                    extractAmount = (int) Math.min(storedAmount, 64);
                }

                ItemStack toExtract = stored.copy();
                toExtract.setCount(extractAmount);

                itemLogic.adjustSlotAmount(storageSlot, -extractAmount);

                player.inventory.setItemStack(toExtract);
                container.sendHeldItemUpdate(player);
                itemLogic.refreshFilterMap();
                itemLogic.wakeUpIfAdaptive();
            }

            return true;
        }

        // Item in cursor: import only inserts
        if (isExport) return true;

        ItemStack transferStack = ItemRecoveryContainer.getHeldItemTransferStack(held);
        if (transferStack == null || transferStack.isEmpty()) return true;

        if (!itemLogic.isItemValidForSlot(storageSlot, transferStack)) return true;

        long requestedAmount = ItemRecoveryContainer.getHeldItemTransferAmount(held, halfStack);
        if (requestedAmount <= 0) return true;

        if (stored.isEmpty()) {
            long toTransfer = Math.min(requestedAmount, itemLogic.getEffectiveMaxSlotSize(storageSlot));
            if (toTransfer <= 0) return true;

            int initialAmount = (int) Math.min(toTransfer, Integer.MAX_VALUE);
            ItemStack toInsert = transferStack.copy();
            toInsert.setCount(initialAmount);
            storage.setStackInSlot(storageSlot, toInsert);

            long remainingToInsert = toTransfer - initialAmount;
            if (remainingToInsert > 0) itemLogic.adjustSlotAmount(storageSlot, remainingToInsert);

            ItemRecoveryContainer.consumeHeldTransfer(player, held, toTransfer);
            container.sendHeldItemUpdate(player);
            itemLogic.refreshFilterMap();

            return true;
        }

        // Non-empty: merge only if same item
        if (!ItemStack.areItemsEqual(transferStack, stored) || !ItemStack.areItemStackTagsEqual(transferStack, stored)) {
            return true;
        }

        long currentAmount = itemLogic.getSlotAmount(storageSlot);
        long space = itemLogic.getEffectiveMaxSlotSize(storageSlot) - currentAmount;
        long toTransfer = Math.min(requestedAmount, space);
        if (toTransfer > 0) {
            itemLogic.adjustSlotAmount(storageSlot, toTransfer);
            ItemRecoveryContainer.consumeHeldTransfer(player, held, toTransfer);
            container.sendHeldItemUpdate(player);
            itemLogic.refreshFilterMap();
        }

        return true;
    }

    /**
     * Handle shift-click for item storage slots (export mode only).
     */
    static boolean handleStorageShiftClick(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            EntityPlayerMP player,
            int storageSlot
    ) {
        if (!host.isExport()) return true;

        ItemInterfaceLogic itemLogic = host.getItemLogic();
        IItemHandlerModifiable storage = itemLogic.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack stored = storage.getStackInSlot(storageSlot);
        if (stored.isEmpty()) return true;

        long remainingAmount = itemLogic.getSlotAmount(storageSlot);
        ItemStack template = stored.copy();
        int vanillaMax = template.getMaxStackSize();
        long totalTransferred = 0;

        for (int i = 0; i < player.inventory.mainInventory.size() && remainingAmount > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);

            if (invStack.isEmpty()) {
                int toInsert = (int) Math.min(remainingAmount, vanillaMax);
                ItemStack newStack = template.copy();
                newStack.setCount(toInsert);
                player.inventory.mainInventory.set(i, newStack);
                remainingAmount -= toInsert;
                totalTransferred += toInsert;
            } else if (ItemStack.areItemsEqual(invStack, template) &&
                       ItemStack.areItemStackTagsEqual(invStack, template)) {
                int space = vanillaMax - invStack.getCount();
                int toTransfer = (int) Math.min(remainingAmount, space);
                if (toTransfer > 0) {
                    invStack.grow(toTransfer);
                    remainingAmount -= toTransfer;
                    totalTransferred += toTransfer;
                }
            }
        }

        if (totalTransferred > 0) {
            itemLogic.adjustSlotAmount(storageSlot, -totalTransferred);
            itemLogic.wakeUpIfAdaptive();
        }

        itemLogic.refreshFilterMap();
        container.detectAndSendChanges();

        return true;
    }

    /**
     * Try to add an ItemStack as a filter (from shift-click in player inventory).
     */
    static void tryAddItemFilter(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            ItemStack clickedStack,
            EntityPlayer player
    ) {
        ItemStack filterCopy = QuickAddHelper.getItemFromItemStack(clickedStack);
        if (filterCopy.isEmpty()) return;

        filterCopy.setCount(1);
        IAEItemStack aeStack = AEApi.instance().storage()
            .getStorageChannel(IItemStorageChannel.class)
            .createStack(filterCopy);

        if (aeStack != null) container.quickAddToFilter(aeStack, player);
    }
}
