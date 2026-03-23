package com.cells.cells.creative.fluid;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.cells.creative.AbstractCreativeCellInventory;
import com.cells.util.FluidStackKey;


/**
 * Creative Fluid Cell inventory implementation.
 * <p>
 * This cell provides infinite fluids for extraction and voids matching inserts.
 * It reports Long.MAX_VALUE / 2 of each partitioned fluid as available.
 * <p>
 * The cell has no real storage - it's a creative fluid source and sink.
 */
public class CreativeFluidCellInventory
        extends AbstractCreativeCellInventory<IAEFluidStack, FluidStack, FluidStackKey, CreativeFluidCellFilterHandler> {

    public CreativeFluidCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        super(cellStack, saveProvider,
              new CreativeFluidCellFilterHandler(cellStack),
              AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
    }

    @Override
    protected FluidStackKey createKey(IAEFluidStack stack) {
        return FluidStackKey.of(stack.getFluidStack());
    }

    @Override
    protected IAEFluidStack createAEStack(FluidStack nativeStack) {
        return channel.createStack(nativeStack);
    }

    @Override
    protected boolean isNativeStackEmpty(FluidStack nativeStack) {
        return nativeStack == null;
    }

    @Override
    protected FluidStack getStackFromFilter(int slot) {
        return filterHandler.getFluidInSlot(slot);
    }
}
