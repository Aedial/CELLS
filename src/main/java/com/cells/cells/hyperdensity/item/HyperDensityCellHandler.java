package com.cells.cells.hyperdensity.item;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;


/**
 * Cell handler for hyper-density storage cells.
 * Registered with AE2's cell registry to handle hyper-density cell items.
 */
public class HyperDensityCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        return is.getItem() instanceof IItemHyperDensityCell
            && ((IItemHyperDensityCell) is.getItem()).isHyperDensityCell(is);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Hyper-density cells only work with item channel
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        IItemHyperDensityCell cellType = (IItemHyperDensityCell) is.getItem();

        HyperDensityCellInventory inventory = new HyperDensityCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new HyperDensityCellInventoryHandler(inventory, itemChannel);
    }
}
