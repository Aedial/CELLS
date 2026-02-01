package com.cells.cells.hyperdensity;

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
 * Cell handler for hyper-density compacting storage cells.
 * Registered with AE2's cell registry.
 */
public class HyperDensityCompactingCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        return is.getItem() instanceof IItemHyperDensityCompactingCell;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        IItemHyperDensityCompactingCell cellType = (IItemHyperDensityCompactingCell) is.getItem();
        HyperDensityCompactingCellInventory inventory = new HyperDensityCompactingCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new HyperDensityCompactingCellInventoryHandler(inventory, itemChannel);
    }
}
