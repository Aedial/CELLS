package com.cells.cells.creative.fluid;

import java.util.Collections;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;

import com.cells.util.DeferredCellOperations;
import com.cells.util.FluidStackKey;


/**
 * Creative Fluid Cell inventory implementation.
 * <p>
 * This cell does NOT accept any fluids (extractOnly).
 * It reports Long.MAX_VALUE / 2 of each partitioned fluid as available.
 * <p>
 * The cell has no real storage - it's a creative fluid source only.
 */
public class CreativeFluidCellInventory implements ICellInventory<IAEFluidStack> {

    /** Amount reported for each partitioned fluid (in mB) */
    public static final long REPORTED_AMOUNT = Long.MAX_VALUE / 2;

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final CreativeFluidCellFilterHandler filterHandler;
    private final IFluidStorageChannel channel;

    public CreativeFluidCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.filterHandler = new CreativeFluidCellFilterHandler(cellStack);
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    /**
     * Check if the cell has any partitioned fluids.
     */
    public boolean hasPartitionedFluids() {
        return filterHandler.getFilterCount() > 0;
    }

    /**
     * Get the filter handler for external access.
     */
    public CreativeFluidCellFilterHandler getFilterHandler() {
        return filterHandler;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public ItemStack getItemStack() {
        return cellStack;
    }

    @Override
    public double getIdleDrain() {
        return 0; // Creative cells have no power drain
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public IItemHandler getConfigInventory() {
        // Fluid cells don't use IItemHandler for config
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 0;
            }

            @Override
            @Nonnull
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            @Nonnull
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 0;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        // No upgrade slots
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 0;
            }

            @Override
            @Nonnull
            public ItemStack getStackInSlot(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            @Nonnull
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 0;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    public int getBytesPerType() {
        return 0; // No byte overhead
    }

    @Override
    public boolean canHoldNewItem() {
        return false; // Cannot accept new fluids
    }

    @Override
    public long getTotalBytes() {
        return 0; // Report no capacity
    }

    @Override
    public long getFreeBytes() {
        return 0; // Always "free"
    }

    @Override
    public long getUsedBytes() {
        return 0; // Nothing actually stored
    }

    @Override
    public long getTotalItemTypes() {
        return filterHandler.getFilterCount();
    }

    @Override
    public long getStoredItemCount() {
        // Amount is irrelevant for this cell, report *something* for visual indication
        return filterHandler.getFilterCount() > 0 ? REPORTED_AMOUNT : 0;
    }

    @Override
    public long getStoredItemTypes() {
        return filterHandler.getFilterCount();
    }

    @Override
    public long getRemainingItemTypes() {
        return 0; // Cannot store new types
    }

    @Override
    public long getRemainingItemCount() {
        return 0; // Cannot store fluids
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public int getStatusForCell() {
        // 0=empty, 1=has content, 2=types full, 3=bytes full
        return filterHandler.getFilterCount() > 0 ? 1 : 0;
    }

    @Override
    public void persist() {
        // NBT is saved directly to the cell ItemStack
        if (saveProvider != null) saveProvider.saveChanges(this);
    }

    // =====================
    // IMEInventory implementation
    // =====================

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, IActionSource src) {
        // Creative cell does NOT accept any fluids
        return input;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;

        // Check if requested fluid is in the partition list
        FluidStack requestFluid = request.getFluidStack();
        FluidStackKey requestKey = FluidStackKey.of(requestFluid);
        if (requestKey == null) return null;

        if (!filterHandler.isInFilter(requestKey)) return null;

        // Found in filters - return the requested amount (creative source)
        IAEFluidStack result = request.copy();
        result.setStackSize(Math.min(request.getStackSize(), REPORTED_AMOUNT));

        // Cancel out the negative delta that DriveWatcher will post.
        // Since the creative cell has infinite fluids, the count should never change.
        // We emit a positive delta equal to the extracted amount to counteract the
        // negative delta that AE2's DriveWatcher posts after extraction.
        if (mode == Actionable.MODULATE) {
            IAEFluidStack counterDelta = result.copy();
            // Positive delta to cancel the negative delta from DriveWatcher
            DeferredCellOperations.queueCrossTierNotification(
                this, saveProvider, channel,
                Collections.singletonList(counterDelta), src
            );
        }

        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (int i = 0; i < filterHandler.getSlots(); i++) {
            FluidStack filterFluid = filterHandler.getFluidInSlot(i);

            if (filterFluid != null) {
                IAEFluidStack aeFluid = channel.createStack(filterFluid);
                if (aeFluid != null) {
                    aeFluid.setStackSize(REPORTED_AMOUNT);
                    out.add(aeFluid);
                }
            }
        }

        return out;
    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return channel;
    }
}
