package com.cells.parts.subnetproxy;

import appeng.api.storage.data.IAEStack;


/**
 * Captured settings and grid owners of a physical proxy connection for a resource channel. Physical
 * parts and grid owners invalidate this access when their availability, bindings, or settings change.
 * Graph traversal and storage operations consume the resulting validity and filter.
 */
public final class SubnetProxySnapshot<T extends IAEStack<T>> {

    /** Node representing the accessed source grid */
    public final SubnetProxyNode back;

    /** Node representing the destination (and original entry) grid */
    public final SubnetProxyNode front;

    /** Resource channel for this connection */
    public final SubnetProxyChannel<T> channel;

    /** Filtering rules captured from the Front's current settings */
    public final SubnetProxyChannel.Filter<T> filter;

    /** Insertion/Extraction priority of the Front */
    public final int priority;

    /** Whether this connection has an Insertion Card */
    public final boolean insertion;

    /** Whether the whitelist prioritizes accepted insertions */
    public final boolean prioritized;

    /** Whether both physical endpoints still permit this captured connection */
    private boolean valid = true;

    public SubnetProxySnapshot(SubnetProxyNode back, SubnetProxyNode front,
            SubnetProxyChannel<T> channel, SubnetProxyChannel.Filter<T> filter,
            int priority, boolean insertion) {
        this.back = back;
        this.front = front;
        this.channel = channel;
        this.filter = filter;
        this.prioritized = filter.prioritized;
        this.priority = priority;
        this.insertion = insertion;
    }

    public boolean available() {
        return this.valid;
    }

    public boolean invalidate() {
        if (!this.valid) return false;

        this.valid = false;
        return true;
    }

    public boolean accepts(T stack) {
        return stack != null && this.available() && this.filter.test(stack);
    }
}
