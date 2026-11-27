package com.cells.parts.subnetproxy;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;


/**
 * Abstract adapter for Subnet Proxy inventory handling, exposing channel and priority settings.
 */
public abstract class SubnetProxyAdapter<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    /** AE2 channel exposed by this adapter */
    private final IStorageChannel<T> channel;

    /** Physical Subnet Proxy priority, for insert/extract operations */
    private int priority;

    protected SubnetProxyAdapter(IStorageChannel<T> channel, int priority) {
        this.channel = channel;
        this.priority = priority;
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public final IStorageChannel<T> getChannel() {
        return this.channel;
    }

    @Override
    public final IItemList<T> getAvailableItems(IItemList<T> out) {
        return out;
    }

    @Override
    public final int getPriority() {
        return this.priority;
    }

    @Override
    public final int getSlot() {
        return 0;
    }
}
