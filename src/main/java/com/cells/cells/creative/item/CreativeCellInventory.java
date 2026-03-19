package com.cells.cells.creative.item;

import java.util.Collections;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import com.cells.util.DeferredCellOperations;
import com.cells.util.ItemStackKey;


/**
 * Creative Cell inventory implementation.
 * <p>
 * This cell does NOT accept any items (extractOnly).
 * It reports Long.MAX_VALUE / 2 of each partitioned item as available.
 * <p>
 * The cell has no real storage - it's a creative item source only.
 */
public class CreativeCellInventory implements ICellInventory<IAEItemStack> {

    /** Amount reported for each partitioned item */
    public static final long REPORTED_AMOUNT = Long.MAX_VALUE / 2;

    private final ItemStack cellStack;
    private final ISaveProvider saveProvider;
    private final CreativeCellFilterHandler filterHandler;
    private final IItemStorageChannel channel;

    public CreativeCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.filterHandler = new CreativeCellFilterHandler(cellStack);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    /**
     * Check if the cell has any partitioned items.
     */
    public boolean hasPartitionedItems() {
        return filterHandler.getFilterCount() > 0;
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
        return filterHandler;
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
        return false; // Cannot accept new items
    }

    @Override
    public long getTotalBytes() {
        return 0; // Report massive storage
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
        // Amount is irrelevant for this cell, report *something* ¯\_(ツ)_/¯
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
        return 0; // Cannot store items
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
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, IActionSource src) {
        // Creative cell does NOT accept any items
        return input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;

        // Check if requested item is in the partition list
        ItemStackKey requestKey = ItemStackKey.of(request.getDefinition());
        if (requestKey == null) return null;

        if (!filterHandler.isInFilter(requestKey)) return null;

        // Found in filters - return the requested amount (creative source)
        IAEItemStack result = request.copy();
        result.setStackSize(Math.min(request.getStackSize(), REPORTED_AMOUNT));

        // Cancel out the negative delta that DriveWatcher will post.
        // Since the creative cell has infinite items, the count should never change.
        // We emit a positive delta equal to the extracted amount to counteract the
        // negative delta that AE2's DriveWatcher posts after extraction.
        if (mode == Actionable.MODULATE) {
            IAEItemStack counterDelta = result.copy();
            // Positive delta to cancel the negative delta from DriveWatcher
            DeferredCellOperations.queueCrossTierNotification(
                this, saveProvider, channel,
                Collections.singletonList(counterDelta), src
            );
        }

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (int i = 0; i < filterHandler.getSlots(); i++) {
            ItemStack filterStack = filterHandler.getStackInSlot(i);

            if (!filterStack.isEmpty()) {
                IAEItemStack aeStack = channel.createStack(filterStack);
                if (aeStack != null) {
                    aeStack.setStackSize(REPORTED_AMOUNT);
                    out.add(aeStack);
                }
            }
        }

        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }
}
