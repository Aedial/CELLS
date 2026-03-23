package com.cells.cells.creative.item;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.creative.AbstractCreativeCellInventory;
import com.cells.util.ItemStackKey;


/**
 * Creative Cell inventory implementation for items.
 * <p>
 * This cell provides infinite items for extraction and voids matching inserts.
 * It reports Long.MAX_VALUE / 2 of each partitioned item as available.
 * <p>
 * The cell has no real storage - it's a creative item source and sink.
 */
public class CreativeCellInventory
        extends AbstractCreativeCellInventory<IAEItemStack, ItemStack, ItemStackKey, CreativeCellFilterHandler> {

    public CreativeCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        super(cellStack, saveProvider,
              new CreativeCellFilterHandler(cellStack),
              AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }

    @Override
    protected ItemStackKey createKey(IAEItemStack stack) {
        return ItemStackKey.of(stack.getDefinition());
    }

    @Override
    protected IAEItemStack createAEStack(ItemStack nativeStack) {
        return channel.createStack(nativeStack);
    }

    @Override
    protected boolean isNativeStackEmpty(ItemStack nativeStack) {
        return nativeStack == null || nativeStack.isEmpty();
    }

    @Override
    protected ItemStack getStackFromFilter(int slot) {
        return filterHandler.getStackInSlot(slot);
    }

    /**
     * Item cells use the filter handler as config inventory (for partition card compatibility).
     */
    @Override
    protected IItemHandler getConfigInventoryImpl() {
        return filterHandler;
    }
}
