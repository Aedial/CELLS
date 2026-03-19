package com.cells.cells.creative.fluid;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEStack;

import com.cells.ItemRegistry;


/**
 * Cell handler for the Creative ME Fluid Cell.
 * <p>
 * Registered with AE2's cell registry to recognize and create
 * inventory handlers for creative fluid cells.
 */
public class CreativeFluidCellHandler implements ICellHandler {

    public static final CreativeFluidCellHandler INSTANCE = new CreativeFluidCellHandler();

    private CreativeFluidCellHandler() {}

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;
        if (ItemRegistry.CREATIVE_FLUID_CELL == null) return false;

        return is.getItem() == ItemRegistry.CREATIVE_FLUID_CELL;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(
        ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (!isCell(is)) return null;

        // Only support fluid channel
        IFluidStorageChannel fluidChannel = AEApi.instance().storage()
            .getStorageChannel(IFluidStorageChannel.class);
        if (channel != fluidChannel) return null;

        CreativeFluidCellInventory inventory = new CreativeFluidCellInventory(is, container);

        return (ICellInventoryHandler<T>) new CreativeFluidCellInventoryHandler(inventory);
    }
}
