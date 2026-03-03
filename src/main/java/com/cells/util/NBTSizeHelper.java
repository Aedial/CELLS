package com.cells.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;


/**
 * Utility class for calculating and formatting NBT sizes.
 * <p>
 * NBT size matters because:
 * - Cells store item data in NBT on the ItemStack
 * - Large NBT can cause network issues (packet size limits ~2MB)
 * - Excessive NBT causes lag during serialization/deserialization
 * <p>
 * This helper provides efficient size calculation and human-readable formatting.
 */
public final class NBTSizeHelper {

    // Size thresholds for formatting (in bytes)
    private static final long KB = 1024L;
    private static final long MB = 1024L * 1024L;

    private NBTSizeHelper() {
        // Utility class
    }

    /**
     * Calculate the size of an NBTTagCompound in bytes.
     * <p>
     * Uses uncompressed size as that's what matters for memory and
     * initial serialization. Network packets may compress further.
     *
     * @param nbt The NBT compound to measure
     * @return Size in bytes, or 0 if null/empty
     */
    public static int calculateSize(NBTTagCompound nbt) {
        if (nbt == null || nbt.isEmpty()) return 0;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            CompressedStreamTools.write(nbt, dos);
            dos.flush();

            return baos.size();
        } catch (IOException e) {
            // Should never happen with ByteArrayOutputStream
            return 0;
        }
    }

    /**
     * Calculate the size of an item's NBT data specifically.
     * This is the NBT that would be serialized via ItemStack.writeToNBT().
     *
     * @param itemNbt The item's NBT compound (from writeToNBT)
     * @return Size in bytes
     */
    public static int calculateItemNbtSize(NBTTagCompound itemNbt) {
        return calculateSize(itemNbt);
    }

    /**
     * Format a byte size into a human-readable string.
     * Examples: "1.2 KB", "3.5 MB", "512 B"
     *
     * @param bytes Size in bytes
     * @return Formatted string
     */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "0 B";

        if (bytes >= MB) {
            double mb = bytes / (double) MB;
            return String.format("%.2f MB", mb);
        }

        if (bytes >= KB) {
            double kb = bytes / (double) KB;
            return String.format("%.2f KB", kb);
        }

        return bytes + "B";
    }

    /**
     * Format a byte size with color coding based on threshold.
     * - Green: Below 50% of threshold
     * - Yellow: 50-100% of threshold
     * - Red: Above threshold
     *
     * @param bytes Size in bytes
     * @param warningThreshold Threshold for warning (yellow)
     * @return Colored formatted string
     */
    public static String formatSizeWithColor(long bytes, long warningThreshold) {
        String sizeStr = formatSize(bytes);

        if (bytes >= warningThreshold) return "§c" + sizeStr; // Red - above threshold
        if (bytes >= warningThreshold / 2) return "§e" + sizeStr; // Yellow - approaching threshold

        return "§a" + sizeStr; // Green - safe
    }

    /**
     * Check if the NBT size exceeds the warning threshold.
     *
     * @param bytes Size in bytes
     * @param warningThreshold Threshold in bytes
     * @return true if size exceeds threshold
     */
    public static boolean exceedsThreshold(long bytes, long warningThreshold) {
        return bytes >= warningThreshold;
    }

    /**
     * Get the warning threshold in bytes from config value in MB.
     *
     * @param thresholdMB Threshold in megabytes
     * @return Threshold in bytes
     */
    public static long mbToBytes(double thresholdMB) {
        return (long) (thresholdMB * MB);
    }
}
