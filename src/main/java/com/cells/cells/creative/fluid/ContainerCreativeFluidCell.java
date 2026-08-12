package com.cells.cells.creative.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for the Creative ME Fluid Cell GUI.
 * <p>
 * Provides a 9x7 grid of fluid filter slots for setting filter fluids.
 * Uses unified PacketResourceSlot for sync. Only accessible in creative mode.
 */
public class ContainerCreativeFluidCell extends AbstractCreativeCellSyncContainer<CreativeFluidCellFilterHandler, IAEFluidStack> {

    public ContainerCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, hand, new CreativeFluidCellFilterHandler(playerInv.player.getHeldItem(hand)));
    }

    @Override
    protected Class<? extends Item> getCellItemClass() {
        return ItemCreativeFluidCell.class;
    }

    // ================================= Sync Methods =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.FLUID;
    }

    @Override
    @Nullable
    protected IAEFluidStack getSyncStack(int slot) {
        FluidStack fluid = filterHandler.getFluidInSlot(slot);
        return fluid != null ? AEFluidStack.fromFluidStack(fluid) : null;
    }

    @Override
    protected void setSyncStack(int slot, @Nullable IAEFluidStack stack) {
        filterHandler.setFluidInSlot(slot, stack != null ? stack.getFluidStack() : null);
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEFluidStack a, @Nullable IAEFluidStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean filterContains(@Nonnull IAEFluidStack stack) {
        return filterHandler.isInFilter(stack.getFluidStack());
    }

    @Override
    @Nullable
    protected IAEFluidStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        FluidStack fluid = QuickAddHelper.getFluidFromItemStack(container);
        return fluid != null ? AEFluidStack.fromFluidStack(fluid) : null;
    }
}
