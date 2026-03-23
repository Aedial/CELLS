package com.cells.cells.creative.gas;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import com.mekeng.github.common.me.storage.IGasStorageChannel;

import com.cells.ItemRegistry;


/**
 * Cell handler for the Creative ME Gas Cell.
 * <p>
 * Registered with AE2's cell registry to recognize and create
 * inventory handlers for creative gas cells.
 */
public class CreativeGasCellHandler implements ICellHandler {

    public static final CreativeGasCellHandler INSTANCE = new CreativeGasCellHandler();

    private CreativeGasCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;
        if (ItemRegistry.CREATIVE_GAS_CELL == null) return false;

        return is.getItem() == ItemRegistry.CREATIVE_GAS_CELL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
        ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Only support gas channel
        IGasStorageChannel gasChannel = AEApi.instance().storage()
            .getStorageChannel(IGasStorageChannel.class);
        if (channel != gasChannel) return null;

        CreativeGasCellInventory inventory = new CreativeGasCellInventory(is, container);

        return (ICellInventoryHandler<T>) new CreativeGasCellInventoryHandler(inventory);
    }
}
