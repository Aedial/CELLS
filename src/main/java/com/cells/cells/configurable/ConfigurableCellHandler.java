package com.cells.cells.configurable;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;


/**
 * Cell handler for the Configurable Storage Cell.
 * <p>
 * Registered with AE2's cell registry. Determines the storage channel from the
 * installed component and creates the appropriate inventory.
 */
public class ConfigurableCellHandler implements ICellHandler {

    @Override
    public boolean isCell(ItemStack is) {
        if (is.isEmpty()) return false;
        if (!(is.getItem() instanceof ItemConfigurableCell)) return false;

        // Only a valid cell if a component is installed
        return ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(is)) != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider container, IStorageChannel<T> channel) {
        if (is.isEmpty() || !(is.getItem() instanceof ItemConfigurableCell)) return null;

        ComponentInfo info = ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(is));
        if (info == null) return null;

        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);

        if (info.isFluid()) {
            if (channel != fluidChannel) return null;

            ConfigurableCellFluidInventory inventory = new ConfigurableCellFluidInventory(is, container, info);
            return (ICellInventoryHandler<T>) new ConfigurableCellInventoryHandler<>(inventory, fluidChannel);
        } else {
            if (channel != itemChannel) return null;

            ConfigurableCellItemInventory inventory = new ConfigurableCellItemInventory(is, container, info);
            return (ICellInventoryHandler<T>) new ConfigurableCellInventoryHandler<>(inventory, itemChannel);
        }
    }
}
