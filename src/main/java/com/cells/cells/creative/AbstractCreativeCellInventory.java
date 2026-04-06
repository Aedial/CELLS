package com.cells.cells.creative;

import java.util.Collections;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import com.cells.util.DeferredCellOperations;
import com.cells.util.EmptyItemHandler;


/**
 * Abstract base class for all Creative Cell inventory implementations.
 * <p>
 * This class provides common functionality for creative cells that:
 * - Provide infinite items/fluids/gases/essentia for extraction
 * - Void matching inserts (acting as a sink)
 * - Report Long.MAX_VALUE / 2 of each partitioned item as available
 * <p>
 * Subclasses only need to implement:
 * - Stack/key extraction (createKey, extractNativeStack)
 * - Available items population (getAvailableItemsFromFilter)
 *
 * @param <T> The AE stack type (IAEItemStack, IAEFluidStack, IAEGasStack, IAEEssentiaStack)
 * @param <S> The native stack type (ItemStack, FluidStack, GasStack, EssentiaStack)
 * @param <K> The key type for filter matching (ItemStackKey, FluidStackKey, etc.)
 * @param <H> The filter handler type (CreativeCellFilterHandler, etc.)
 */
public abstract class AbstractCreativeCellInventory<T extends IAEStack<T>, S, K, H extends AbstractCreativeCellFilterHandler<S, K>>
        implements ICellInventory<T> {

    /** Amount reported for each partitioned item */
    public static final long REPORTED_AMOUNT = Long.MAX_VALUE / 2;

    protected final ItemStack cellStack;
    protected final ISaveProvider saveProvider;
    protected final H filterHandler;
    protected final IStorageChannel<T> channel;

    protected AbstractCreativeCellInventory(@Nonnull ItemStack cellStack, ISaveProvider saveProvider,
                                            H filterHandler, IStorageChannel<T> channel) {
        this.cellStack = cellStack;
        this.saveProvider = saveProvider;
        this.filterHandler = filterHandler;
        this.channel = channel;
    }

    /**
     * Check if the cell has any partitioned items/fluids/etc.
     */
    public boolean hasPartitionedContent() {
        return filterHandler.getFilterCount() > 0;
    }

    // =====================
    // Abstract methods to be implemented by subclasses
    // =====================

    /**
     * Create a key from an AE stack for filter matching.
     * @param stack The AE stack to create a key from
     * @return The key, or null if the stack is invalid
     */
    protected abstract K createKey(T stack);

    /**
     * Create an AE stack from the native stack for the available items list.
     * @param nativeStack The native stack (ItemStack, FluidStack, etc.)
     * @return The AE stack, or null if conversion fails
     */
    protected abstract T createAEStack(S nativeStack);

    /**
     * Check if the native stack is empty/null.
     * @param nativeStack The native stack to check
     * @return true if empty/null
     */
    protected abstract boolean isNativeStackEmpty(S nativeStack);

    /**
     * Get the native stack from a specific filter slot.
     * @param slot The slot index
     * @return The native stack at that slot
     */
    protected abstract S getStackFromFilter(int slot);

    /**
     * Get the IItemHandler for config inventory (only used for item cells).
     * Override in item cell to return filterHandler.
     * @return The config inventory
     */
    protected IItemHandler getConfigInventoryImpl() {
        return EmptyItemHandler.INSTANCE;
    }

    /**
     * Get the maximum allowed count for a single item type.
     * This is used to cap the reported amount for each partitioned item,
     * as well as the delta notifications to avoid overflowing AE2's internal counters
     * and causing incorrect behavior in the terminal and drive watcher.
     * @return The maximum allowed count
     */
    protected long getMaxAllowed() {
        return Long.MAX_VALUE;
    }

    /**
     * Get the amount to report for each partitioned item in getAvailableItems.
     * This can be overridden to return a different value than REPORTED_AMOUNT if needed.
     * @return The amount to report for each partitioned item
     */
    protected long getReportedAmount() {
        return REPORTED_AMOUNT;
    }

    // =====================
    // ICellInventory implementation (all identical across cell types)
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
        return getConfigInventoryImpl();
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return EmptyItemHandler.INSTANCE;
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
        return filterHandler.getFilterCount() > 0 ? this.getReportedAmount() : 0;
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
    // IMEInventory implementation (common logic)
    // =====================

    @Override
    public T injectItems(T input, Actionable mode, IActionSource src) {
        // Creative cell acts as a sink for filtered items - voids them
        if (input == null) return null;

        // Check if input matches a filter
        K inputKey = createKey(input);
        if (inputKey == null) return input;

        if (!filterHandler.isInFilter(inputKey)) return input;

        // Cancel the deltas (see extractItems for explanation)
        if (mode == Actionable.MODULATE) {
            // Overflows in positive deltas are lost, so we take this overflow into account.
            // We should never count more than the non-overflowing part.
            long counterSizeMax = this.getMaxAllowed() - this.getReportedAmount();
            if (counterSizeMax <= 0) return null;

            T counterDelta = input.copy();
            counterDelta.setStackSize(-Math.min(counterDelta.getStackSize(), counterSizeMax));

            // Negative delta to cancel the negative delta from DriveWatcher
            DeferredCellOperations.queueCrossTierNotification(
                this, saveProvider, channel,
                Collections.singletonList(counterDelta), src
            );
        }

        // Item matches filter - void it entirely (accept all, store nothing)
        return null;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource src) {
        if (request == null) return null;

        // Check if requested item is in the partition list
        K requestKey = createKey(request);
        if (requestKey == null) return null;

        if (!filterHandler.isInFilter(requestKey)) return null;

        // Found in filters - return the requested amount (creative source)
        T result = request.copy();
        result.setStackSize(Math.min(request.getStackSize(), this.getReportedAmount()));

        // Cancel out the negative delta that DriveWatcher will post.
        // Since the creative cell has infinite items, the count should never change.
        // We emit a positive delta equal to the extracted amount to counteract the
        // negative delta that AE2's DriveWatcher posts after extraction.
        if (mode == Actionable.MODULATE) {
            T counterDelta = result.copy();
            // Positive delta to cancel the negative delta from DriveWatcher
            DeferredCellOperations.queueCrossTierNotification(
                this, saveProvider, channel,
                Collections.singletonList(counterDelta), src
            );
        }

        return result;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        for (int i = 0; i < filterHandler.getSlots(); i++) {
            S filterStack = getStackFromFilter(i);

            if (!isNativeStackEmpty(filterStack)) {
                T aeStack = createAEStack(filterStack);
                if (aeStack != null) {
                    aeStack.setStackSize(this.getReportedAmount());
                    out.add(aeStack);
                }
            }
        }

        return out;
    }

    @Override
    public IStorageChannel<T> getChannel() {
        return channel;
    }
}
