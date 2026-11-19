package com.cells.profiling;


/**
 * Capture counters for a device, requesting machine, or adaptive interface logic.
 */
public final class ProfileEntry {

    private final ProfileSession session;
    private final long[] operationNanos = new long[ProfileOperation.values().length];
    private final long[] operationCalls = new long[ProfileOperation.values().length];
    private final int[] operationDepth = new int[ProfileOperation.values().length];
    private int depth;
    private long lastTick;
    private long tickNanos;
    private long totalNanos;
    private long maxTickNanos;
    private long calls;
    private long cellCalls;
    private long simulations;
    private long emptyCalls;
    private long polls;
    private long successfulPolls;
    private long tickIoNanos;

    public final String description;

    public ProfileEntry(ProfileSession session, String description) {
        this.session = session;
        this.description = description;
    }

    public long begin(ProfileOperation operation) {
        if (operation == ProfileOperation.TICK && this.operationDepth[operation.ordinal()] == 0) {
            this.tickIoNanos = 0;
        }

        this.depth++;
        this.operationDepth[operation.ordinal()]++;
        return System.nanoTime();
    }

    public void end(ProfileOperation operation, long started) {
        long elapsed = System.nanoTime() - started;
        int index = operation.ordinal();
        this.operationCalls[index]++;

        if (--this.operationDepth[index] == 0) {
            long operationTime = elapsed;
            if (operation == ProfileOperation.TICK) {
                operationTime = Math.max(0L, elapsed - this.tickIoNanos);
            } else if ((operation == ProfileOperation.NETWORK_IO || operation == ProfileOperation.ADJACENT_IO)
                    && this.operationDepth[ProfileOperation.TICK.ordinal()] > 0
                    && this.operationDepth[ProfileOperation.NETWORK_IO.ordinal()] == 0
                    && this.operationDepth[ProfileOperation.ADJACENT_IO.ordinal()] == 0) {
                this.tickIoNanos += elapsed;
            }

            this.operationNanos[index] += operationTime;
        }

        // Count nested calls to the same device or caller in the enclosing elapsed time
        if (--this.depth != 0) return;

        this.calls++;
        this.totalNanos += elapsed;

        if (this.lastTick != this.session.getTicks()) {
            this.lastTick = this.session.getTicks();
            this.tickNanos = 0;
        }

        this.tickNanos += elapsed;
        this.maxTickNanos = Math.max(this.maxTickNanos, this.tickNanos);
    }

    public void recordCellCall(ProfileOperation operation) {
        this.cellCalls++;
        if (operation.simulation) this.simulations++;

        // Count the call as empty until its result confirms a transfer
        this.emptyCalls++;
    }

    public void recordCellResult(boolean worked) {
        if (worked) this.emptyCalls--;
    }

    public void recordPoll(boolean worked) {
        this.polls++;
        if (worked) this.successfulPolls++;
    }

    public long getTotalNanos() { return this.totalNanos; }
    public boolean isMeasuring() { return this.depth > 0; }
    public long getMaxTickNanos() { return this.maxTickNanos; }
    public long getCalls() { return this.calls; }
    public long getCellCalls() { return this.cellCalls; }
    public long getSimulations() { return this.simulations; }
    public long getEmptyCalls() { return this.emptyCalls; }
    public long getPolls() { return this.polls; }
    public long getSuccessfulPolls() { return this.successfulPolls; }
    public long getNanos(ProfileOperation operation) { return this.operationNanos[operation.ordinal()]; }
    public long getCalls(ProfileOperation operation) { return this.operationCalls[operation.ordinal()]; }
}
