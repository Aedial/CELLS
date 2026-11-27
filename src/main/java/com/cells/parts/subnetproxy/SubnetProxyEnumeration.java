package com.cells.parts.subnetproxy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;


/**
 * Enumeration adapter exposing the deduplicated view of all connected Subnet Proxies.
 * Individual proxies are not exposed directly, as they cannot reliably deduplicate their contents
 * <i>while</i> controlling the deltas on each network.
 * This lets networks maintain a high level of separation.
 */
public final class SubnetProxyEnumeration<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    /** View of the deduplicated Subnet Proxy contents for the network */
    private final SubnetProxyView<T> view;

    public SubnetProxyEnumeration(SubnetProxyView<T> view) {
        this.view = view;
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out) {
        this.view.appendImports(out);

        return out;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource source) {
        return null;
    }

    @Override
    public T injectItems(T input, Actionable mode, IActionSource source) {
        return input;
    }

    @Override
    public boolean canAccept(T input) {
        return false;
    }

    @Override
    public IStorageChannel<T> getChannel() {
        return this.view.channel().getChannel();
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.NO_ACCESS;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return false;
    }
}
