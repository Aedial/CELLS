package com.cells.parts.subnetproxy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.IStorageChannel;


/**
 * Front -> Back insertion handler for the Subnet Proxy. It manages item injections back into
 * the subnet, following the configured filters, if it has an Insertion Card.
 */
public final class SubnetProxyInsertionHandler<T extends IAEStack<T>> extends SubnetProxyAdapter<T> {

    /** Captured Subnet Proxy settings */
    private SubnetProxySnapshot<T> snapshot;

    public SubnetProxyInsertionHandler(IStorageChannel<T> channel, int priority) {
        super(channel, priority);
    }

    public void setSnapshot(SubnetProxySnapshot<T> snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public T injectItems(T input, Actionable mode, IActionSource source) {
        return SubnetProxyOperation.insert(this.snapshot, input, mode, source);
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource source) {
        // Write-only: no extraction allowed
        return null;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.WRITE;
    }

    @Override
    public boolean isPrioritized(T input) {
        return this.snapshot != null && this.snapshot.prioritized && this.canAccept(input);
    }

    @Override
    public boolean canAccept(T input) {
        return input != null && this.snapshot != null && this.snapshot.insertion
            && this.snapshot.available() && this.snapshot.filter.test(input);
    }

    @Override
    public boolean validForPass(int pass) {
        return this.snapshot != null && this.snapshot.prioritized ? pass == 1 : pass == 2;
    }
}
