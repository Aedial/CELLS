package com.cells.cells.hyperdensity.fluid;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;


/**
 * Cell handler for hyper-density fluid storage cells.
 * Registered with AE2's cell registry to handle hyper-density fluid cell items.
 */
public class FluidHyperDensityCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        Item item = is.getItem();
        return item instanceof IItemFluidHyperDensityCell && ((IItemFluidHyperDensityCell) item).isHyperDensityCell(is);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        if (channel != fluidChannel) return null;

        IItemFluidHyperDensityCell cellType = (IItemFluidHyperDensityCell) is.getItem();

        FluidHyperDensityCellInventory inventory = new FluidHyperDensityCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new FluidHyperDensityCellInventoryHandler(inventory, fluidChannel);
    }
}
