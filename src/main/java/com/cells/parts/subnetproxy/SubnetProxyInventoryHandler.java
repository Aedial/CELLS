package com.cells.parts.subnetproxy;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.IStorageChannel;

import com.cells.config.CellsConfig;


/**
 * back -> Front extraction handler for the Subnet Proxy. It manages requests and records extraction faults,
 * if they are enabled.
 */
public final class SubnetProxyInventoryHandler<T extends IAEStack<T>> extends SubnetProxyAdapter<T> {

    /** Captured Subnet Proxy settings */
    private SubnetProxySnapshot<T> snapshot;

    public SubnetProxyInventoryHandler(IStorageChannel<T> channel, int priority) {
        super(channel, priority);
    }

    public void setSnapshot(SubnetProxySnapshot<T> snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public T extractItems(T request, Actionable mode, IActionSource source) {
        T extracted = SubnetProxyOperation.extract(this.snapshot, request, mode, source);

        if (request == null || this.snapshot == null || mode != Actionable.MODULATE
            || !CellsConfig.general.subnetProxyReportExtractionFaults) return extracted;

        long actual = extracted == null ? 0 : extracted.getStackSize();
        if (actual >= request.getStackSize()) return extracted;

        long visible = SubnetProxyOperation.cachedAvailable(this.snapshot, request);
        if (visible > 0) this.snapshot.front.diagnostics().fault(this.snapshot.channel.getType().name(),
            request.asItemStackRepresentation(), request.getStackSize(), actual, visible);

        return extracted;
    }

    @Override
    public T injectItems(T input, Actionable mode, IActionSource source) {
        // Read-only: reject all injections by returning the full input
        return input;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ;
    }

    @Override
    public boolean isPrioritized(T input) {
        return false;
    }

    @Override
    public boolean canAccept(T input) {
        return false;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }
}
