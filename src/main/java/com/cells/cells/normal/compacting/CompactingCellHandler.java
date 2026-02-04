package com.cells.cells.normal.compacting;

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
 * Cell handler for compacting storage cells.
 * Registered with AE2's cell registry to handle compacting cell items.
 */
public class CompactingCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;

        return is.getItem() instanceof IInternalCompactingCell;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Compacting cells only work with item channel
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        IInternalCompactingCell cellType = (IInternalCompactingCell) is.getItem();

        CompactingCellInventory inventory = new CompactingCellInventory(cellType, is, container);

        return (ICellInventoryHandler<T>) new CompactingCellInventoryHandler(inventory, itemChannel);
    }
}
