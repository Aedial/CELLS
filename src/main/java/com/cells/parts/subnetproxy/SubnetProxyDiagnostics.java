package com.cells.parts.subnetproxy;

import com.cells.Cells;
import com.cells.config.CellsConfig;


/**
 * Debug statistics for Subnet Proxy lifecycle and extraction events.
 * The tracking paths are controlled by config with delay settings.
 */
public final class SubnetProxyDiagnostics {

    private static final long TICKS_PER_MINUTE = 1200;

    /** Minimum ticks between extraction mismatch warnings */
    private static final long FAULT_COOLDOWN = 100;

    /** Minimum number of signals required before a churn summary becomes eligible */
    private static final int CHURN_THRESHOLD = 100;

    /** Number of signals since the previous churn summary */
    private long signals;

    /** Number of source passes since the previous churn summary */
    private long sourcePasses;

    /** Number of accepted live baselines since the previous churn summary */
    private long acceptedBaselines;

    /** Server-session tick of the previous churn summary */
    private long lastChurnLog = -TICKS_PER_MINUTE;

    /** Server-session tick of the previous extraction warning */
    private long lastFaultLog = -FAULT_COOLDOWN;

    /** Latest fault caught by inspection */
    private String lastFault = "none";

    public void signal(String reason) {
        if (!CellsConfig.general.subnetProxyReportUpdateChurn) {
            this.signals = 0;
            this.sourcePasses = 0;
            this.acceptedBaselines = 0;

            return;
        }

        this.signals++;
        long cooldown = TICKS_PER_MINUTE * CellsConfig.general.subnetProxyUpdateChurnLogDelay;
        if (this.signals < CHURN_THRESHOLD || SubnetProxyManager.tick() - this.lastChurnLog < cooldown) return;

        Cells.LOGGER.warn("Subnet Proxy lifecycle churn: signals={}, sourcePasses={}, acceptedBaselines={}, reason={}",
            this.signals, this.sourcePasses, this.acceptedBaselines, reason);
        this.lastChurnLog = SubnetProxyManager.tick();
        this.signals = 0;
        this.sourcePasses = 0;
        this.acceptedBaselines = 0;
    }

    public void sourcePass() {
        if (CellsConfig.general.subnetProxyReportUpdateChurn) this.sourcePasses++;
    }

    public void baseline() {
        if (CellsConfig.general.subnetProxyReportUpdateChurn) this.acceptedBaselines++;
    }

    public void fault(String channel, Object resource, long requested, long extracted, long visible) {
        this.lastFault = channel + " resource=" + resource + " requested=" + requested
            + " extracted=" + extracted + " cachedAvailable=" + visible;

        if (SubnetProxyManager.tick() - this.lastFaultLog < FAULT_COOLDOWN) return;

        this.lastFaultLog = SubnetProxyManager.tick();
        Cells.LOGGER.warn("Subnet Proxy extraction mismatch: {}", this.lastFault);
    }

    public String describe() {
        return "signals=" + this.signals + " sourcePasses=" + this.sourcePasses
            + " acceptedBaselines=" + this.acceptedBaselines + " lastFault=" + this.lastFault;
    }
}
