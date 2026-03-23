package com.cells.cells.creative.essentia;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ISaveProvider;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;

import com.cells.cells.creative.AbstractCreativeCellInventory;
import com.cells.integration.thaumicenergistics.EssentiaStackKey;


/**
 * Creative Essentia Cell inventory implementation.
 * <p>
 * This cell provides infinite essentia for extraction and voids matching inserts.
 * It reports Long.MAX_VALUE / 2 of each partitioned essentia as available.
 * <p>
 * The cell has no real storage - it's a creative essentia source and sink.
 */
public class CreativeEssentiaCellInventory
        extends AbstractCreativeCellInventory<IAEEssentiaStack, EssentiaStack, EssentiaStackKey, CreativeEssentiaCellFilterHandler> {

    public CreativeEssentiaCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        super(cellStack, saveProvider,
              new CreativeEssentiaCellFilterHandler(cellStack),
              AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class));
    }

    @Override
    protected EssentiaStackKey createKey(IAEEssentiaStack stack) {
        return EssentiaStackKey.of(stack.getStack());
    }

    @Override
    protected IAEEssentiaStack createAEStack(EssentiaStack nativeStack) {
        return channel.createStack(nativeStack);
    }

    @Override
    protected boolean isNativeStackEmpty(EssentiaStack nativeStack) {
        return nativeStack == null;
    }

    @Override
    protected EssentiaStack getStackFromFilter(int slot) {
        return filterHandler.getEssentiaInSlot(slot);
    }
}
