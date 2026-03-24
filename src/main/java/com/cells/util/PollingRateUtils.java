package com.cells.util;


/**
 * Utility class for formatting polling rate values for display.
 * Used by interface GUIs, tooltips, and related components.
 */
public final class PollingRateUtils {

    // Polling rate constants (in ticks, 20 ticks = 1 second)
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    public static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    public static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private PollingRateUtils() {
    }

    /**
     * Format a polling rate value for display.
     *
     * @param ticks The polling rate in game ticks (0 = adaptive)
     * @return Human-readable string (e.g., "5t", "2s 10t", "1m 30s")
     */
    public static String format(long ticks) {
        if (ticks <= 0) return "0";

        StringBuilder sb = new StringBuilder();

        if (ticks >= TICKS_PER_DAY) {
            long days = ticks / TICKS_PER_DAY;
            sb.append(days).append("d ");
            ticks %= TICKS_PER_DAY;
        }
        if (ticks >= TICKS_PER_HOUR) {
            long hours = ticks / TICKS_PER_HOUR;
            sb.append(hours).append("h ");
            ticks %= TICKS_PER_HOUR;
        }
        if (ticks >= TICKS_PER_MINUTE) {
            long minutes = ticks / TICKS_PER_MINUTE;
            sb.append(minutes).append("m ");
            ticks %= TICKS_PER_MINUTE;
        }
        if (ticks >= TICKS_PER_SECOND) {
            long seconds = ticks / TICKS_PER_SECOND;
            sb.append(seconds).append("s ");
            ticks %= TICKS_PER_SECOND;
        }
        if (ticks > 0) {
            sb.append(ticks).append("t");
        }

        return sb.toString().trim();
    }
}
