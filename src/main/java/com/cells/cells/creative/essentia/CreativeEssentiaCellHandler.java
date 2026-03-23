package com.cells.cells.creative.essentia;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;

import thaumicenergistics.api.storage.IEssentiaStorageChannel;

import com.cells.ItemRegistry;


/**
 * Cell handler for the Creative ME Essentia Cell.
 * <p>
 * Registered with AE2's cell registry to recognize and create
 * inventory handlers for creative essentia cells.
 */
public class CreativeEssentiaCellHandler implements ICellHandler {

    public static final CreativeEssentiaCellHandler INSTANCE = new CreativeEssentiaCellHandler();

    private CreativeEssentiaCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;
        if (ItemRegistry.CREATIVE_ESSENTIA_CELL == null) return false;

        return is.getItem() == ItemRegistry.CREATIVE_ESSENTIA_CELL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
        ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Only support essentia channel
        IEssentiaStorageChannel essentiaChannel = AEApi.instance().storage()
            .getStorageChannel(IEssentiaStorageChannel.class);
        if (channel != essentiaChannel) return null;

        CreativeEssentiaCellInventory inventory = new CreativeEssentiaCellInventory(is, container);

        return (ICellInventoryHandler<T>) new CreativeEssentiaCellInventoryHandler(inventory);
    }
}
