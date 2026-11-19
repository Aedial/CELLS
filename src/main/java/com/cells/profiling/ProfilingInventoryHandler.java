package com.cells.profiling;

import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;


/**
 * Shared profiling logic around CELLS cell handlers and their partition checks.
 */
public class ProfilingInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    private final IMEInventory<T> cellInventory;
    private final Profiler.Call3<T, Actionable, IActionSource, T> injectCall = super::injectItems;
    private final Profiler.Call3<T, Actionable, IActionSource, T> extractCall = super::extractItems;
    private final Function<IItemList<T>, IItemList<T>> enumerateCall = super::getAvailableItems;
    private final Predicate<T> acceptCall = super::canAccept;
    private final Predicate<T> prioritizeCall = super::isPrioritized;

    public ProfilingInventoryHandler(IMEInventory<T> inventory, IStorageChannel<T> channel) {
        super(inventory, channel);
        this.cellInventory = inventory;
    }

    @Nullable
    private ProfileEntry getEntry(@Nullable IActionSource source) {
        ProfileSession session = CellsProfiler.getSession();
        return session != null ? session.getCellEntry(this.cellInventory, source) : null;
    }

    @Override
    public T injectItems(T input, Actionable mode, IActionSource source) {
        ProfileEntry entry = this.getEntry(source);
        ProfileOperation operation = mode == Actionable.SIMULATE
            ? ProfileOperation.SIMULATE_INSERT : ProfileOperation.INSERT;
        long requested = entry != null ? input.getStackSize() : 0;
        if (entry != null) entry.recordCellCall(operation);

        T remainder = Profiler.wrap(this.injectCall, operation, entry, input, mode, source);
        if (entry != null) {
            entry.recordCellResult(remainder == null || remainder.getStackSize() < requested);
        }

        return remainder;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource source) {
        ProfileEntry entry = this.getEntry(source);
        ProfileOperation operation = mode == Actionable.SIMULATE
            ? ProfileOperation.SIMULATE_EXTRACT : ProfileOperation.EXTRACT;
        if (entry != null) entry.recordCellCall(operation);

        T extracted = Profiler.wrap(this.extractCall, operation, entry, request, mode, source);
        if (entry != null) entry.recordCellResult(extracted != null && extracted.getStackSize() > 0);

        return extracted;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        return Profiler.wrap(this.enumerateCall, ProfileOperation.ENUMERATE, this.getEntry(null), out);
    }

    @Override
    public boolean canAccept(T input) {
        ProfileEntry entry = this.getEntry(null);
        if (entry != null && entry.isMeasuring()) entry = null;

        return Profiler.wrapPredicate(this.acceptCall, ProfileOperation.QUERY, entry, input);
    }

    @Override
    public boolean isPrioritized(T input) {
        ProfileEntry entry = this.getEntry(null);
        if (entry != null && entry.isMeasuring()) entry = null;

        return Profiler.wrapPredicate(this.prioritizeCall, ProfileOperation.QUERY, entry, input);
    }
}
