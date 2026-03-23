package com.cells.cells.creative.gas;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ISaveProvider;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;

import com.cells.cells.creative.AbstractCreativeCellInventory;
import com.cells.integration.mekanismenergistics.GasStackKey;


/**
 * Creative Gas Cell inventory implementation.
 * <p>
 * This cell provides infinite gases for extraction and voids matching inserts.
 * It reports Long.MAX_VALUE / 2 of each partitioned gas as available.
 * <p>
 * The cell has no real storage - it's a creative gas source and sink.
 */
public class CreativeGasCellInventory
        extends AbstractCreativeCellInventory<IAEGasStack, GasStack, GasStackKey, CreativeGasCellFilterHandler> {

    public CreativeGasCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        super(cellStack, saveProvider,
              new CreativeGasCellFilterHandler(cellStack),
              AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
    }

    @Override
    protected GasStackKey createKey(IAEGasStack stack) {
        return GasStackKey.of(stack.getGasStack());
    }

    @Override
    protected IAEGasStack createAEStack(GasStack nativeStack) {
        return channel.createStack(nativeStack);
    }

    @Override
    protected boolean isNativeStackEmpty(GasStack nativeStack) {
        return nativeStack == null;
    }

    @Override
    protected GasStack getStackFromFilter(int slot) {
        return filterHandler.getGasInSlot(slot);
    }
}
