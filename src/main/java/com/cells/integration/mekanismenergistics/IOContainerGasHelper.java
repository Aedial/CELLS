package com.cells.integration.mekanismenergistics;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IGasItem;
import mekanism.common.capabilities.Capabilities;

import com.mekeng.github.common.me.data.IAEGasStack;

import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.gui.QuickAddHelper;
import com.cells.items.ItemRecoveryContainer;


/**
 * Container helper for gas empty/fill item actions in the I/O Interface.
 * <p>
 * Isolates all MekanismEnergistics type references so that
 * {@link com.cells.blocks.iointerface.ContainerIOInterface} never directly references
 * gas classes, preventing ClassNotFoundException when MekanismEnergistics is not installed.
 * <p>
 * Only call methods on this class after verifying MekanismEnergistics is loaded.
 */
public final class IOContainerGasHelper {

    private IOContainerGasHelper() {}

    /**
     * Handle pouring gas from held item into tank (import only).
     * Supports IGasItem (Mekanism gas tanks) and GAS_HANDLER_CAPABILITY items.
     *
     * @return true if the action was handled
     */
    public static boolean handleGasEmptyItem(
            EntityPlayerMP player, int tankSlot, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        IInterfaceLogic logic = host.getActiveLogic();
        if (!(logic instanceof GasInterfaceLogic)) return false;

        GasInterfaceLogic gasLogic = (GasInterfaceLogic) logic;
        if (tankSlot < 0 || tankSlot >= GasInterfaceLogic.STORAGE_SLOTS) return false;

        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        if (ItemRecoveryContainer.isType(held, ItemRecoveryContainer.TYPE_GAS)) {
            return handleRecoveryContainerPouring(player, tankSlot, held, gasLogic, updateHeld, detectChanges);
        }

        // Check for IGasItem (Mekanism gas tanks)
        if (held.getItem() instanceof IGasItem) {
            return handleIGasItemPouring(player, tankSlot, held, gasLogic, host, updateHeld, detectChanges);
        }

        // Check for gas handler capability
        IGasHandler handler = held.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
        if (handler != null) {
            return handleGasCapabilityPouring(player, tankSlot, held, handler, gasLogic, host, updateHeld, detectChanges);
        }

        return false;
    }

    /**
     * Handle filling held item from gas tank (export only).
     *
     * @return true if the action was handled
     */
    public static boolean handleGasFillItem(
            EntityPlayerMP player, int tankSlot, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        IInterfaceLogic logic = host.getActiveLogic();
        if (!(logic instanceof GasInterfaceLogic)) return false;

        GasInterfaceLogic gasLogic = (GasInterfaceLogic) logic;
        if (tankSlot < 0 || tankSlot >= GasInterfaceLogic.STORAGE_SLOTS) return false;

        GasStack tankGas = gasLogic.getGasInTank(tankSlot);
        if (tankGas == null || tankGas.amount <= 0) return false;

        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            tankGas = gasLogic.getGasInTank(tankSlot);
            if (tankGas == null || tankGas.amount <= 0) break;

            ItemStack singleContainer = held.copy();
            singleContainer.setCount(1);

            ItemStack filledContainer = fillContainerWithGas(singleContainer, tankSlot, gasLogic);
            if (filledContainer.isEmpty()) break;

            if (held.getCount() == 1) {
                player.inventory.setItemStack(filledContainer);
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(filledContainer)) {
                    player.dropItem(filledContainer, false);
                }
            }
        }

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    private static boolean handleRecoveryContainerPouring(
            EntityPlayerMP player, int tankSlot, ItemStack held,
            GasInterfaceLogic gasLogic,
            Runnable updateHeld, Runnable detectChanges) {

        GasStack contained = QuickAddHelper.getGasFromItemStack(held);
        if (contained == null || contained.amount <= 0 || contained.getGas() == null) return false;

        IAEGasStack filterGas = gasLogic.getFilter(tankSlot);
        if (filterGas != null && !filterGas.getGasStack().isGasEqual(contained)) return false;

        GasStack currentTankGas = gasLogic.getGasInTank(tankSlot);
        if (currentTankGas != null && !currentTankGas.isGasEqual(contained)) return false;

        long toTransfer = Math.min(
            ItemRecoveryContainer.getAmount(held),
            gasLogic.getEffectiveMaxSlotSize(tankSlot) - gasLogic.getSlotAmount(tankSlot)
        );
        if (toTransfer <= 0) return false;

        long transferred = gasLogic.insertGasIntoTankLong(tankSlot, contained, toTransfer);

        if (transferred <= 0) return false;

        ItemRecoveryContainer.consumeHeldTransfer(player, held, transferred);

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    // ================================= Internal Helpers =================================

    private static boolean handleIGasItemPouring(
            EntityPlayerMP player, int tankSlot, ItemStack held,
            GasInterfaceLogic gasLogic, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        IGasItem gasItem = (IGasItem) held.getItem();
        GasStack drainable = gasItem.getGas(held);
        if (drainable == null || drainable.amount <= 0) return false;

        IAEGasStack filterGas = gasLogic.getFilter(tankSlot);
        if (filterGas != null && !filterGas.getGasStack().isGasEqual(drainable)) return false;

        int capacity = (int) Math.min(gasLogic.getEffectiveMaxSlotSize(tankSlot), Integer.MAX_VALUE);
        GasStack currentTankGas = gasLogic.getGasInTank(tankSlot);
        if (currentTankGas != null && !currentTankGas.isGasEqual(drainable)) return false;

        int currentAmount = currentTankGas != null ? currentTankGas.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        int toTransfer = Math.min(drainable.amount, spaceAvailable);
        GasStack removed = gasItem.removeGas(held, toTransfer);
        if (removed == null || removed.amount <= 0) return false;

        GasStack newGas;
        if (currentTankGas == null) {
            newGas = new GasStack(removed.getGas(), removed.amount);
        } else {
            newGas = currentTankGas.copy();
            newGas.amount += removed.amount;
        }
        gasLogic.setGasInTank(tankSlot, newGas);

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    private static boolean handleGasCapabilityPouring(
            EntityPlayerMP player, int tankSlot, ItemStack held, IGasHandler handler,
            GasInterfaceLogic gasLogic, IIOInterfaceHost host,
            Runnable updateHeld, Runnable detectChanges) {

        GasStack drainable = handler.drawGas(null, Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return false;

        IAEGasStack filterGas = gasLogic.getFilter(tankSlot);
        if (filterGas != null && !filterGas.getGasStack().isGasEqual(drainable)) return false;

        int capacity = (int) Math.min(gasLogic.getEffectiveMaxSlotSize(tankSlot), Integer.MAX_VALUE);
        GasStack currentTankGas = gasLogic.getGasInTank(tankSlot);
        if (currentTankGas != null && !currentTankGas.isGasEqual(drainable)) return false;

        int currentAmount = currentTankGas != null ? currentTankGas.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        int toTransfer = Math.min(drainable.amount, spaceAvailable);
        GasStack drawn = handler.drawGas(null, toTransfer, true);
        if (drawn == null || drawn.amount <= 0) return false;

        GasStack newGas;
        if (currentTankGas == null) {
            newGas = new GasStack(drawn.getGas(), drawn.amount);
        } else {
            newGas = currentTankGas.copy();
            newGas.amount += drawn.amount;
        }
        gasLogic.setGasInTank(tankSlot, newGas);

        updateHeld.run();
        detectChanges.run();
        return true;
    }

    private static ItemStack fillContainerWithGas(ItemStack container, int tankSlot, GasInterfaceLogic gasLogic) {
        GasStack tankGas = gasLogic.getGasInTank(tankSlot);
        if (tankGas == null || tankGas.amount <= 0) return ItemStack.EMPTY;

        // Try IGasItem first
        if (container.getItem() instanceof IGasItem) {
            IGasItem gasItem = (IGasItem) container.getItem();
            GasStack existing = gasItem.getGas(container);
            if (existing != null && existing.amount > 0 && !existing.isGasEqual(tankGas)) {
                return ItemStack.EMPTY;
            }

            int capacity = gasItem.getMaxGas(container);
            int currentAmount = existing != null ? existing.amount : 0;
            int spaceAvailable = capacity - currentAmount;
            if (spaceAvailable <= 0) return ItemStack.EMPTY;

            int toFill = Math.min(tankGas.amount, spaceAvailable);
            gasItem.addGas(container, new GasStack(tankGas.getGas(), toFill));
            gasLogic.drainGasFromTank(tankSlot, toFill, true);
            return container;
        }

        // Try gas handler capability
        IGasHandler handler = container.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
        if (handler != null) {
            int accepted = handler.receiveGas(null, new GasStack(tankGas.getGas(), tankGas.amount), false);
            if (accepted <= 0) return ItemStack.EMPTY;

            handler.receiveGas(null, new GasStack(tankGas.getGas(), accepted), true);
            gasLogic.drainGasFromTank(tankSlot, accepted, true);
            return container;
        }

        return ItemStack.EMPTY;
    }
}
