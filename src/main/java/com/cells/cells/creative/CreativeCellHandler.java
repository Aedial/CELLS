package com.cells.cells.creative;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

import com.cells.ItemRegistry;


/**
 * Cell handler for the Creative ME Cell.
 * <p>
 * Registered with AE2's cell registry to recognize and create
 * inventory handlers for creative cells.
 */
public class CreativeCellHandler implements ICellHandler {

    public static final CreativeCellHandler INSTANCE = new CreativeCellHandler();

    private CreativeCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;
        if (ItemRegistry.CREATIVE_CELL == null) return false;

        return is.getItem() == ItemRegistry.CREATIVE_CELL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
        ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Only support item channel
        IItemStorageChannel itemChannel = AEApi.instance().storage()
            .getStorageChannel(IItemStorageChannel.class);
        if (channel != itemChannel) return null;

        CreativeCellInventory inventory = new CreativeCellInventory(is, container);

        return (ICellInventoryHandler<T>) new CreativeCellInventoryHandler(inventory);
    }
}
