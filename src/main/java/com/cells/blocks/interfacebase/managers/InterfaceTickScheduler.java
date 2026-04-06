package com.cells.blocks.interfacebase.managers;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;

import com.cells.config.CellsConfig;
import com.cells.util.TickManagerHelper;


/**
 * Manages tick scheduling, polling rate, and sleep/wake state for a resource interface.
 * Handles the dual-timer dispatch when an auto-pull/push card is installed alongside
 * the standard network I/O polling.
 */
public class InterfaceTickScheduler {

    /** Minimum interval between network I/O operations in adaptive mode with card (in ticks) */
    private static final int MIN_IO_INTERVAL = 20;

    public static final int DEFAULT_POLLING_RATE = 0; // 0 = adaptive (AE2-managed tick rates)

    /**
     * Callback interface for the parent logic to provide tick-time work.
     */
    public interface Callbacks {

        /** Get the host's grid proxy. */
        AENetworkProxy getGridProxy();

        /** Get the host's tickable. */
        IGridTickable getTickable();

        /** Mark the host as dirty and save. */
        void markDirtyAndSave();

        /** Perform network I/O (import or export). Returns true if work was done. */
        boolean performNetworkIO();

        /** Perform auto-pull/push operations. Returns true if work was done. */
        boolean performAutoPullPush();

        /** Check if there's work to do (import has resources, export needs resources). */
        boolean hasWorkToDo();
    }

    private final Callbacks callbacks;

    /** Polling rate in ticks (0 = adaptive). */
    private int pollingRate = 0;

    /** Whether we are currently sleeping (not being ticked by AE2). */
    private boolean isSleeping = false;

    /**
     * Cumulative elapsed ticks since the interface started ticking.
     * Used for timing both the card interval and network I/O independently.
     */
    private long totalElapsedTicks = 0;

    /** Elapsed ticks value when the last card operation was performed. */
    private long lastCardOperationTick = 0;

    /** Elapsed ticks value when the last network I/O was performed. */
    private long lastNetworkIOTick = 0;

    public InterfaceTickScheduler(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    // ============================== Getters / Setters ==============================

    /**
     * Get the minimum allowed polling rate from config.
     * @return The configured minimum polling rate (defaults to 0 for adaptive)
     */
    public static int getMinPollingRate() {
        return CellsConfig.interfaceMinPollingRate;
    }

    public static int getDefaultPollingRate() {
        int minRate = getMinPollingRate();
        return Math.max(minRate, DEFAULT_POLLING_RATE);
    }

    public int getPollingRate() {
        return this.pollingRate;
    }

    public int setPollingRate(int ticks) {
        return this.setPollingRate(ticks, null);
    }

    /**
     * Set the polling rate with optional player notification on failure.
     * @param ticks Polling rate in ticks (0 = adaptive, but clamped to config minimum)
     * @param player Player to notify if re-registration fails, or null to skip notification
     */
    public int setPollingRate(int ticks, @Nullable EntityPlayer player) {
        // Enforce config minimum (0 means adaptive, but if config requires higher, enforce it)
        int minRate = getMinPollingRate();
        this.pollingRate = Math.max(minRate, ticks);

        this.callbacks.markDirtyAndSave();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        AENetworkProxy proxy = this.callbacks.getGridProxy();
        if (proxy.isReady()) {
            if (!TickManagerHelper.reRegisterTickable(proxy.getNode(), this.callbacks.getTickable())) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.polling_rate_delayed"));
                }
            }

            // When switching to adaptive mode, wake up the interface immediately
            // to prevent it from sleeping indefinitely waiting for external triggers.
            this.wakeUpIfAdaptive();
        }

        return this.pollingRate;
    }

    // ============================== Tick request ==============================

    /**
     * Create a TickingRequest based on current configuration.
     * <p>
     * When an auto-pull/push card is installed, the tick rate must accommodate
     * both the card interval and the network I/O polling rate. We use the minimum
     * of the two as the tick rate, and time-gate each operation independently.
     * <p>
     * Special case: Adaptive polling + card installed:
     * The node must never sleep, otherwise the card timer won't advance.
     * We cap the max wait to avoid waiting longer than the card interval,
     * but allow going faster when there's work to do.
     *
     * @param hasAutoPullPushUpgrade Whether an auto-pull/push card is installed
     * @param autoPullPushInterval The card's tick interval (only valid if card installed)
     */
    public TickingRequest getTickingRequest(boolean hasAutoPullPushUpgrade, int autoPullPushInterval) {
        if (hasAutoPullPushUpgrade && autoPullPushInterval > 0) {
            // Card installed: determine effective tick rate

            if (this.pollingRate > 0) {
                // Fixed polling + card: use the smaller interval, never sleep
                int effectiveRate = Math.min(this.pollingRate, autoPullPushInterval);
                this.isSleeping = false;

                return new TickingRequest(effectiveRate, effectiveRate, false, true);
            }

            // Adaptive polling + card: use card interval as min, capped max, never sleep
            int min = Math.min(autoPullPushInterval, TickRates.Interface.getMin());
            int max = Math.min(autoPullPushInterval, TickRates.Interface.getMax());

            // Ensure min <= max
            if (min > max) max = min;
            this.isSleeping = false;

            return new TickingRequest(min, max, false, true);
        }

        // No card (original behavior)
        if (this.pollingRate > 0) {
            this.isSleeping = false;

            return new TickingRequest(
                this.pollingRate,
                this.pollingRate,
                false,
                true
            );
        }

        this.isSleeping = !this.callbacks.hasWorkToDo();

        return new TickingRequest(
            TickRates.Interface.getMin(),
            TickRates.Interface.getMax(),
            this.isSleeping,
            true
        );
    }

    // ============================== Tick dispatch ==============================

    /**
     * Handle a tick with elapsed-time tracking for dual-timer dispatch.
     * <p>
     * When a card is installed, two independent timers are maintained:
     * <ul>
     *   <li><b>Card timer:</b> Fires at autoPullPushInterval ticks, runs performAutoPullPush()</li>
     *   <li><b>Network I/O timer:</b> Fires at the polling rate, runs import/export resources</li>
     * </ul>
     * Both timers use >= threshold checks (not ==) to tolerate AE2's imprecise tick scheduling.
     *
     * @param ticksSinceLastCall Number of ticks since this method was last called (from AE2 tick manager)
     * @param hasAutoPullPushUpgrade Whether an auto-pull/push card is installed
     * @param autoPullPushInterval The card's tick interval
     * @return The appropriate tick rate modulation
     */
    public TickRateModulation onTick(int ticksSinceLastCall,
                                     boolean hasAutoPullPushUpgrade, int autoPullPushInterval) {
        if (!this.callbacks.getGridProxy().isActive()) {
            this.isSleeping = true;
            return TickRateModulation.SLEEP;
        }

        this.totalElapsedTicks += ticksSinceLastCall;

        boolean didNetworkWork = false;

        // === Card operation (auto-pull/push) ===
        if (hasAutoPullPushUpgrade && autoPullPushInterval > 0) {
            long ticksSinceLastCard = this.totalElapsedTicks - this.lastCardOperationTick;
            if (ticksSinceLastCard >= autoPullPushInterval) {
                this.callbacks.performAutoPullPush();
                this.lastCardOperationTick = this.totalElapsedTicks;
            }
        }

        // === Network I/O (import/export to ME network) ===
        boolean shouldDoNetworkIO;
        if (this.pollingRate > 0) {
            // Fixed polling rate: check elapsed time
            long ticksSinceLastIO = this.totalElapsedTicks - this.lastNetworkIOTick;
            shouldDoNetworkIO = ticksSinceLastIO >= this.pollingRate;
        } else if (hasAutoPullPushUpgrade && autoPullPushInterval > 0) {
            // Adaptive mode with card: throttle network I/O to a set minimum
            // tick rate. The card can tick very fast (e.g., every 1-5 ticks), but network
            // I/O is more expensive and doesn't benefit from running at that frequency.
            // Use the AE2 default minimum as a virtual polling rate to prevent lag.
            long ticksSinceLastIO = this.totalElapsedTicks - this.lastNetworkIOTick;
            shouldDoNetworkIO = ticksSinceLastIO >= MIN_IO_INTERVAL;
        } else {
            // Adaptive mode without card: always attempt network I/O when ticked
            shouldDoNetworkIO = true;
        }

        if (shouldDoNetworkIO) {
            didNetworkWork = this.callbacks.performNetworkIO();
            this.lastNetworkIOTick = this.totalElapsedTicks;
        }

        // === Determine tick rate modulation ===
        if (hasAutoPullPushUpgrade && autoPullPushInterval > 0) {
            // Both timers are fixed: maintain SAME rate
            if (this.pollingRate > 0) return TickRateModulation.SAME;

            // Adaptive + card: if network I/O did work, go faster; otherwise maintain SAME
            // to keep the card timer advancing. Never sleep with a card installed.
            if (didNetworkWork) return TickRateModulation.FASTER;

            // Check if we should slow down while keeping card timer alive
            // We are hard-capped by the card interval in getTickingRequest(),
            // so we won't wait longer than the card allows.
            boolean hasIOWork = this.callbacks.hasWorkToDo();
            return hasIOWork ? TickRateModulation.SAME : TickRateModulation.SLOWER;
        }

        // No card: original adaptive/fixed behavior
        if (this.pollingRate > 0) return TickRateModulation.SAME;
        if (didNetworkWork) return TickRateModulation.FASTER;

        boolean shouldSleep = !this.callbacks.hasWorkToDo();
        this.isSleeping = shouldSleep;

        return shouldSleep ? TickRateModulation.SLEEP : TickRateModulation.SLOWER;
    }

    // ============================== Sleep/Wake management ==============================

    /**
     * Wake up the tick manager if sleeping and using adaptive polling.
     * Only calls alertDevice() when actually sleeping - tick modulation handles the rest.
     */
    public void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;
        if (!this.isSleeping) return;

        try {
            this.callbacks.getGridProxy().getTick().alertDevice(this.callbacks.getGridProxy().getNode());
            this.isSleeping = false;
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    /**
     * Reset all timing state. Called when auto-pull/push card is removed.
     */
    public void resetTimers() {
        this.totalElapsedTicks = 0;
        this.lastCardOperationTick = 0;
        this.lastNetworkIOTick = 0;
    }

    // ============================== Serialization support ==============================

    /**
     * Read polling rate from NBT.
     */
    public void readFromNBT(NBTTagCompound data, EntityPlayer player) {
        if (data.hasKey("pollingRate")) {
            this.setPollingRate(data.getInteger("pollingRate"), player);
        }
    }

    public void readFromNBT(NBTTagCompound data) {
        this.readFromNBT(data, null);
    }

    /**
     * Write polling rate to NBT.
     */
    public void writeToNBT(NBTTagCompound data) {
        data.setInteger("pollingRate", this.pollingRate);
    }
}
