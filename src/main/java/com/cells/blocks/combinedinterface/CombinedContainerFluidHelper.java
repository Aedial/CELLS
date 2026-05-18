package com.cells.blocks.combinedinterface;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.FluidTankProperties;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.Cells;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.gui.QuickAddHelper;
import com.cells.items.ItemRecoveryContainer;


/**
 * Helper class for fluid-specific storage interactions in the combined interface container.
 * <p>
 * Extracted from {@link com.cells.blocks.interfacebase.fluid.ContainerFluidInterface} to be
 * invoked by {@link ContainerCombinedInterface} when the fluid tab is active.
 */
final class CombinedContainerFluidHelper {

    private CombinedContainerFluidHelper() {}

    /**
     * Handle pouring fluid from held item into tank (import only).
     */
    static boolean handleEmptyItemAction(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            EntityPlayerMP player,
            int slot
    ) {
        FluidInterfaceLogic fluidLogic = host.getFluidLogic();

        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        if (ItemRecoveryContainer.isType(held, ItemRecoveryContainer.TYPE_FLUID)) {
            return handleRecoveryContainerPouring(container, host, player, slot, held, fluidLogic);
        }

        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) return false;

        IAEFluidStack filterFluid = fluidLogic.getFilter(slot);

        FluidStack drainable = fh.drain(Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return false;

        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(drainable)) return false;

        int capacity = (int) Math.min(fluidLogic.getEffectiveMaxSlotSize(slot), Integer.MAX_VALUE);
        FluidStack currentTankFluid = fluidLogic.getFluidInTank(slot);

        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(drainable)) return false;

        int currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            currentTankFluid = fluidLogic.getFluidInTank(slot);
            currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
            spaceAvailable = capacity - currentAmount;
            if (spaceAvailable <= 0) break;

            ItemStack copiedContainer = held.copy();
            copiedContainer.setCount(1);
            fh = FluidUtil.getFluidHandler(copiedContainer);
            if (fh == null) break;

            drainable = fh.drain(spaceAvailable, false);
            if (drainable == null || drainable.amount <= 0) break;

            int toInsert = Math.min(drainable.amount, spaceAvailable);
            FluidStack drained = fh.drain(toInsert, true);
            if (drained == null || drained.amount <= 0) break;

            int actuallyInserted = fluidLogic.insertFluidIntoTank(slot, drained);
            if (actuallyInserted < drained.amount) {
                Cells.LOGGER.warn("Could not insert all drained fluid. Inserted: {}, Drained: {}",
                    actuallyInserted, drained.amount);
            }

            if (held.getCount() == 1) {
                player.inventory.setItemStack(fh.getContainer());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                    player.dropItem(fh.getContainer(), false);
                }
            }
        }

        container.sendHeldItemUpdate(player);
        return true;
    }

    private static boolean handleRecoveryContainerPouring(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            EntityPlayerMP player,
            int slot,
            ItemStack held,
            FluidInterfaceLogic fluidLogic
    ) {
        FluidStack recoveryFluid = ItemRecoveryContainer.getFluidStack(held);
        if (recoveryFluid == null || recoveryFluid.getFluid() == null) return false;

        IAEFluidStack filterFluid = fluidLogic.getFilter(slot);
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(recoveryFluid)) return false;

        FluidStack currentTankFluid = fluidLogic.getFluidInTank(slot);
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(recoveryFluid)) return false;

        long remainingToInsert = Math.min(
            ItemRecoveryContainer.getAmount(held),
            fluidLogic.getEffectiveMaxSlotSize(slot) - fluidLogic.getSlotAmount(slot)
        );
        if (remainingToInsert <= 0) return false;

        long transferred = fluidLogic.insertFluidIntoTankLong(slot, recoveryFluid, remainingToInsert);

        if (transferred <= 0) return false;

        ItemRecoveryContainer.consumeHeldTransfer(player, held, transferred);
        container.sendHeldItemUpdate(player);
        return true;
    }

    /**
     * Handle filling held item from tank (export only).
     */
    static boolean handleFillItemAction(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            EntityPlayerMP player,
            int slot
    ) {
        FluidInterfaceLogic fluidLogic = host.getFluidLogic();

        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        FluidStack tankFluid = fluidLogic.getFluidInTank(slot);
        if (tankFluid == null || tankFluid.amount <= 0) return false;

        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            tankFluid = fluidLogic.getFluidInTank(slot);
            if (tankFluid == null || tankFluid.amount <= 0) break;

            ItemStack singleContainer = held.copy();
            singleContainer.setCount(1);

            FluidActionResult result = FluidUtil.tryFillContainer(
                singleContainer,
                new TankWrapper(fluidLogic, host, slot, tankFluid),
                Integer.MAX_VALUE,
                player,
                true
            );

            if (!result.isSuccess()) break;

            if (held.getCount() == 1) {
                player.inventory.setItemStack(result.getResult());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(result.getResult())) {
                    player.dropItem(result.getResult(), false);
                }
            }
        }

        container.sendHeldItemUpdate(player);
        return true;
    }

    /**
     * Try to add a fluid filter from an ItemStack (shift-click from player inventory).
     */
    static void tryAddFluidFilter(
            ContainerCombinedInterface container,
            ICombinedInterfaceHost host,
            ItemStack clickedStack,
            EntityPlayer player
    ) {
        FluidStack fluid = QuickAddHelper.getFluidFromItemStack(clickedStack);
        if (fluid == null || fluid.amount <= 0) return;

        IAEFluidStack aeFluid = AEApi.instance().storage()
            .getStorageChannel(IFluidStorageChannel.class)
            .createStack(fluid);

        if (aeFluid != null) {
            container.quickAddToFilter(aeFluid, player);
        }
    }

    /**
     * IFluidHandler wrapper for tank slot to work with FluidUtil.
     */
    private static class TankWrapper implements IFluidHandler {
        private final FluidInterfaceLogic fluidLogic;
        private final ICombinedInterfaceHost host;
        private final int slot;
        private FluidStack fluid;

        TankWrapper(FluidInterfaceLogic fluidLogic, ICombinedInterfaceHost host, int slot, FluidStack fluid) {
            this.fluidLogic = fluidLogic;
            this.host = host;
            this.slot = slot;
            this.fluid = fluid;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            int maxTankSize = (int) Math.min(fluidLogic.getEffectiveMaxSlotSize(slot), Integer.MAX_VALUE);
            return new IFluidTankProperties[] {
                new FluidTankProperties(fluid, maxTankSize)
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0; // Drain-only wrapper
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || !resource.isFluidEqual(fluid)) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = fluidLogic.drainFluidFromTank(slot, maxDrain, doDrain);
            if (doDrain && drained != null) {
                fluid = fluidLogic.getFluidInTank(slot);
            }
            return drained;
        }
    }
}
