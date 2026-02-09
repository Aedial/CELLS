package com.cells.util;

import java.util.concurrent.atomic.AtomicLong;

import com.cells.Cells;


/**
 * Lightweight benchmark utility for cell inventory hot paths.
 * <p>
 * Collects timing data with minimal overhead using atomic counters and nanoTime.
 * Prints accumulated results to the log every 10 seconds and resets.
 * </p>
 * <p>
 * Usage:
 * <pre>
 * long start = CellBenchmark.start();
 * // ... hot path code ...
 * CellBenchmark.COMPACTING_INJECT.record(start);
 * </pre>
 * </p>
 */
public final class CellBenchmark {

    /** Interval between benchmark reports in nanoseconds (10 seconds). */
    private static final long REPORT_INTERVAL_NS = 10_000_000_000L;

    /** Last time a report was printed. */
    private static volatile long lastReportTime = System.nanoTime();

    // =====================
    // Compacting Cell Benchmarks
    // =====================

    public static final Benchmark COMPACTING_INJECT = new Benchmark("CompactingCell.injectItems");
    public static final Benchmark COMPACTING_EXTRACT = new Benchmark("CompactingCell.extractItems");
    public static final Benchmark COMPACTING_GET_AVAILABLE = new Benchmark("CompactingCell.getAvailableItems");

    // Granular sub-benchmarks for CompactingCell hot path analysis
    public static final Benchmark COMPACTING_RELOAD_NBT = new Benchmark("  └─ reloadFromNBTIfNeeded");
    public static final Benchmark COMPACTING_HAS_PARTITION = new Benchmark("  └─ hasPartition");
    public static final Benchmark COMPACTING_UPDATE_CHAIN = new Benchmark("  └─ updateCompressionChainIfNeeded");
    public static final Benchmark COMPACTING_CAN_ACCEPT = new Benchmark("  └─ canAcceptItem");
    public static final Benchmark COMPACTING_GET_SLOT = new Benchmark("  └─ getSlotForItem");
    public static final Benchmark COMPACTING_CAPACITY_CALC = new Benchmark("  └─ capacityCalculations");
    public static final Benchmark COMPACTING_SAVE_CHANGES = new Benchmark("  └─ saveChanges");
    public static final Benchmark COMPACTING_NOTIFY_GRID = new Benchmark("  └─ notifyGridOfAllTierChanges");

    // =====================
    // HyperDensity Cell Benchmarks
    // =====================

    public static final Benchmark HYPER_INJECT = new Benchmark("HyperDensityCell.injectItems");
    public static final Benchmark HYPER_EXTRACT = new Benchmark("HyperDensityCell.extractItems");
    public static final Benchmark HYPER_GET_AVAILABLE = new Benchmark("HyperDensityCell.getAvailableItems");

    // Granular sub-benchmarks for HyperDensityCell hot path analysis
    public static final Benchmark HYPER_CAN_ACCEPT = new Benchmark("  └─ canAcceptItem");
    public static final Benchmark HYPER_GET_STORED_COUNT = new Benchmark("  └─ getStoredCount");
    public static final Benchmark HYPER_CAPACITY_CALC = new Benchmark("  └─ capacityCalculations");
    public static final Benchmark HYPER_SET_STORED_COUNT = new Benchmark("  └─ setStoredCount");
    public static final Benchmark HYPER_SAVE_CHANGES = new Benchmark("  └─ saveChanges");

    private CellBenchmark() {}

    /**
     * Start a benchmark measurement.
     *
     * @return The start time in nanoseconds
     */
    public static long start() {
        return System.nanoTime();
    }

    /**
     * Check if enough time has passed since the last report and print if so.
     * Called automatically by Benchmark.record() to minimize manual calls.
     */
    private static void maybeReport() {
        long now = System.nanoTime();

        if (now - lastReportTime < REPORT_INTERVAL_NS) return;

        // Use compareAndSet pattern to avoid multiple threads printing at once
        // This is a simple approximation - not perfectly atomic, but good enough
        long expected = lastReportTime;
        lastReportTime = now;

        // Only print if we were the one to update the time
        if (now - expected >= REPORT_INTERVAL_NS) {
            printReport();
            resetAll();
        }
    }

    /**
     * Print all benchmarks to the log.
     */
    private static void printReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Cell Benchmark Report (last 10s) ===");

        // Compacting Cell - main methods
        sb.append("\n[CompactingCell]\n");
        appendBenchmark(sb, COMPACTING_INJECT);
        appendBenchmark(sb, COMPACTING_EXTRACT);
        appendBenchmark(sb, COMPACTING_GET_AVAILABLE);

        // Compacting Cell - sub-operations (only if any were called)
        if (hasAnyCalls(COMPACTING_RELOAD_NBT, COMPACTING_HAS_PARTITION, COMPACTING_UPDATE_CHAIN,
                        COMPACTING_CAN_ACCEPT, COMPACTING_GET_SLOT, COMPACTING_CAPACITY_CALC,
                        COMPACTING_SAVE_CHANGES, COMPACTING_NOTIFY_GRID)) {
            sb.append("  Sub-operations breakdown:\n");
            appendBenchmark(sb, COMPACTING_RELOAD_NBT);
            appendBenchmark(sb, COMPACTING_HAS_PARTITION);
            appendBenchmark(sb, COMPACTING_UPDATE_CHAIN);
            appendBenchmark(sb, COMPACTING_CAN_ACCEPT);
            appendBenchmark(sb, COMPACTING_GET_SLOT);
            appendBenchmark(sb, COMPACTING_CAPACITY_CALC);
            appendBenchmark(sb, COMPACTING_SAVE_CHANGES);
            appendBenchmark(sb, COMPACTING_NOTIFY_GRID);
        }

        // HyperDensity Cell
        sb.append("\n[HyperDensityCell]\n");
        appendBenchmark(sb, HYPER_INJECT);
        appendBenchmark(sb, HYPER_EXTRACT);
        appendBenchmark(sb, HYPER_GET_AVAILABLE);

        // HyperDensity Cell - sub-operations (only if any were called)
        if (hasAnyCalls(HYPER_CAN_ACCEPT, HYPER_GET_STORED_COUNT, HYPER_CAPACITY_CALC,
                        HYPER_SET_STORED_COUNT, HYPER_SAVE_CHANGES)) {
            sb.append("  Sub-operations breakdown:\n");
            appendBenchmark(sb, HYPER_CAN_ACCEPT);
            appendBenchmark(sb, HYPER_GET_STORED_COUNT);
            appendBenchmark(sb, HYPER_CAPACITY_CALC);
            appendBenchmark(sb, HYPER_SET_STORED_COUNT);
            appendBenchmark(sb, HYPER_SAVE_CHANGES);
        }

        sb.append("========================================");

        Cells.LOGGER.info(sb.toString());
    }

    private static boolean hasAnyCalls(Benchmark... benchmarks) {
        for (Benchmark b : benchmarks) {
            if (b.callCount.get() > 0) return true;
        }

        return false;
    }

    private static void appendBenchmark(StringBuilder sb, Benchmark b) {
        long calls = b.callCount.get();

        if (calls == 0) {
            sb.append(String.format("  %-40s: no calls\n", b.name));

            return;
        }

        long totalNs = b.totalNanos.get();
        double avgUs = (totalNs / (double) calls) / 1000.0;
        double totalMs = totalNs / 1_000_000.0;

        sb.append(String.format("  %-40s: %8d calls, %10.2f ms total, %8.3f µs/call\n",
            b.name, calls, totalMs, avgUs));
    }

    /**
     * Reset all benchmark counters.
     */
    private static void resetAll() {
        COMPACTING_INJECT.reset();
        COMPACTING_EXTRACT.reset();
        COMPACTING_GET_AVAILABLE.reset();

        // Sub-benchmarks
        COMPACTING_RELOAD_NBT.reset();
        COMPACTING_HAS_PARTITION.reset();
        COMPACTING_UPDATE_CHAIN.reset();
        COMPACTING_CAN_ACCEPT.reset();
        COMPACTING_GET_SLOT.reset();
        COMPACTING_CAPACITY_CALC.reset();
        COMPACTING_SAVE_CHANGES.reset();
        COMPACTING_NOTIFY_GRID.reset();

        HYPER_INJECT.reset();
        HYPER_EXTRACT.reset();
        HYPER_GET_AVAILABLE.reset();

        // HyperDensity sub-benchmarks
        HYPER_CAN_ACCEPT.reset();
        HYPER_GET_STORED_COUNT.reset();
        HYPER_CAPACITY_CALC.reset();
        HYPER_SET_STORED_COUNT.reset();
        HYPER_SAVE_CHANGES.reset();
    }

    /**
     * A single benchmark counter.
     * Uses atomic operations for thread safety without synchronization overhead.
     */
    public static final class Benchmark {

        final String name;
        final AtomicLong callCount = new AtomicLong(0);
        final AtomicLong totalNanos = new AtomicLong(0);

        Benchmark(String name) {
            this.name = name;
        }

        /**
         * Record a completed operation.
         *
         * @param startNanos The start time from {@link CellBenchmark#start()}
         */
        public void record(long startNanos) {
            long elapsed = System.nanoTime() - startNanos;
            callCount.incrementAndGet();
            totalNanos.addAndGet(elapsed);

            // Check if we should print a report
            maybeReport();
        }

        /**
         * Reset this benchmark's counters.
         */
        void reset() {
            callCount.set(0);
            totalNanos.set(0);
        }
    }
}
