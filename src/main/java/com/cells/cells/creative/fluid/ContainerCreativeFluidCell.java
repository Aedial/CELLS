package com.cells.cells.creative.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;

import com.cells.cells.creative.AbstractCreativeCellSyncContainer;
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

        // Bind player inventory - start at y=159 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 159);
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
    @Nullable
    protected IAEFluidStack copySyncStack(@Nullable IAEFluidStack stack) {
        return stack != null ? stack.copy() : null;
    }

    @Override
    protected boolean syncStacksEqual(@Nullable IAEFluidStack a, @Nullable IAEFluidStack b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    @Override
    protected boolean isSyncStackEmpty(@Nullable IAEFluidStack stack) {
        return stack == null;
    }

    @Override
    protected boolean filterContains(@Nonnull IAEFluidStack stack) {
        return filterHandler.isInFilter(stack.getFluidStack());
    }

    @Override
    @Nullable
    protected IAEFluidStack extractResourceFromItemStack(@Nonnull ItemStack container) {
        FluidStack fluid = FluidUtil.getFluidContained(container);
        return fluid != null ? AEFluidStack.fromFluidStack(fluid) : null;
    }

    // ================================= Filter Operations =================================

    /**
     * Set a fluid filter at a specific slot (called from GUI/packet).
     */
    public void setFluidFilter(int slot, FluidStack fluid) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        filterHandler.setFluidInSlot(slot, fluid);
    }
}
