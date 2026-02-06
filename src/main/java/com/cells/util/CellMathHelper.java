package com.cells.util;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ISaveProvider;


/**
 * Utility class for common math operations and helpers used across cell inventories.
 * <p>
 * Contains overflow-protected arithmetic, NBT utilities, and action source helpers.
 * </p>
 */
public final class CellMathHelper {

    private CellMathHelper() {
        // Utility class
    }

    /**
     * Multiplies two longs with overflow protection.
     * <p>
     * If the multiplication would overflow, returns {@code Long.MAX_VALUE} instead
     * of wrapping around to a negative number.
     * </p>
     *
     * @param a First operand (must be non-negative)
     * @param b Second operand (must be non-negative)
     * @return The product, or Long.MAX_VALUE if overflow would occur
     */
    public static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }

    /**
     * Adds two longs with overflow protection.
     * <p>
     * If the addition would overflow, returns {@code Long.MAX_VALUE} instead
     * of wrapping around to a negative number.
     * </p>
     *
     * @param a First operand
     * @param b Second operand
     * @return The sum, or Long.MAX_VALUE if overflow would occur
     */
    public static long addWithOverflowProtection(long a, long b) {
        if (a < 0 || b < 0) return Math.max(a, b);
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE;

        return a + b;
    }

    /**
     * Subtracts two longs with underflow protection.
     * <p>
     * If the result would be negative, returns 0 instead.
     * </p>
     *
     * @param a Minuend (must be non-negative)
     * @param b Subtrahend (must be non-negative)
     * @return The difference, clamped to 0 if negative
     */
    public static long subtractWithUnderflowProtection(long a, long b) {
        if (a <= 0) return 0;
        if (b <= 0) return a;
        if (b >= a) return 0;

        return a - b;
    }

    /**
     * Computes (a * b * c) / d with overflow protection.
     * <p>
     * This method handles the case where a * b * c would overflow by using
     * strategic reordering of operations. It tries to perform divisions early
     * when possible to keep intermediate values small.
     * </p>
     * <p>
     * If the result would still overflow after all optimizations, returns
     * {@code Long.MAX_VALUE}.
     * </p>
     *
     * @param a First multiplicand (must be non-negative)
     * @param b Second multiplicand (must be non-negative)
     * @param c Third multiplicand (must be non-negative)
     * @param d Divisor (must be positive)
     * @return The result (a * b * c) / d, or Long.MAX_VALUE if overflow
     */
    public static long multiplyThenDivide(long a, long b, long c, long d) {
        if (a <= 0 || b <= 0 || c <= 0) return 0;
        if (d <= 0) return Long.MAX_VALUE;

        // Try to find a factor that d divides evenly into, to reduce early
        // This helps avoid overflow while maintaining precision

        // Check if d divides a evenly
        if (a % d == 0) {
            long aDiv = a / d;
            long result = multiplyWithOverflowProtection(aDiv, b);
            return multiplyWithOverflowProtection(result, c);
        }

        // Check if d divides b evenly
        if (b % d == 0) {
            long bDiv = b / d;
            long result = multiplyWithOverflowProtection(a, bDiv);
            return multiplyWithOverflowProtection(result, c);
        }

        // Check if d divides c evenly
        if (c % d == 0) {
            long cDiv = c / d;
            long result = multiplyWithOverflowProtection(a, b);
            return multiplyWithOverflowProtection(result, cDiv);
        }

        // No exact division possible. Try partial division with remainder handling.
        // Split a into quotient and remainder: a = q*d + r
        long q = a / d;
        long r = a % d;

        // (a * b * c) / d = (q*d + r) * b * c / d = q*b*c + (r*b*c)/d
        long term1 = multiplyWithOverflowProtection(q, b);
        term1 = multiplyWithOverflowProtection(term1, c);

        // For the remainder term, r < d, so r*b might not overflow
        long rTimesB = multiplyWithOverflowProtection(r, b);
        long remainderProduct;
        if (rTimesB == Long.MAX_VALUE) {
            // Overflow in remainder term - use approximation
            remainderProduct = multiplyWithOverflowProtection(rTimesB / d, c);
        } else {
            long rTimesBTimesC = multiplyWithOverflowProtection(rTimesB, c);
            if (rTimesBTimesC == Long.MAX_VALUE) {
                remainderProduct = multiplyWithOverflowProtection(rTimesB / d, c);
            } else {
                remainderProduct = rTimesBTimesC / d;
            }
        }

        return addWithOverflowProtection(term1, remainderProduct);
    }

    /**
     * Compares two ItemStacks for equality (item, metadata, and NBT).
     *
     * @param a First stack
     * @param b Second stack
     * @return true if stacks are equal (ignoring count)
     */
    public static boolean areItemsEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;

        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata()
            && ItemStack.areItemStackTagsEqual(a, b);
    }

    /**
     * Loads a long value from NBT using native getLong.
     * Falls back to _hi/_lo pair for backward compatibility.
     *
     * @param tag The NBT compound to read from
     * @param key The key name
     * @return The long value
     */
    public static long loadLong(NBTTagCompound tag, String key) {
        // Try native long first (4 = TAG_Long)
        if (tag.hasKey(key, 4)) return tag.getLong(key);

        // Fall back to _hi/_lo pair for backward compatibility
        if (tag.hasKey(key + "_hi")) {
            int hi = tag.getInteger(key + "_hi");
            int lo = tag.getInteger(key + "_lo");

            return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
        }

        return 0;
    }

    /**
     * Saves a long value to NBT using native setLong.
     *
     * @param tag   The NBT compound to write to
     * @param key   The key name
     * @param value The long value to save
     */
    public static void saveLong(NBTTagCompound tag, String key, long value) {
        tag.setLong(key, value);

        // Clean up legacy _hi/_lo keys if present
        tag.removeTag(key + "_hi");
        tag.removeTag(key + "_lo");
    }

    /**
     * Extracts the World from an action source, used for recipe lookups.
     *
     * @param src The action source (player or machine)
     * @return The world, or null if unavailable
     */
    @Nullable
    public static World getWorldFromSource(@Nullable IActionSource src) {
        if (src == null) return null;
        if (src.player().isPresent()) return src.player().get().world;

        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getWorld();
        }

        return null;
    }

    /**
     * Extracts the ME Grid from an action source, used for posting storage changes.
     *
     * @param src The action source (typically a machine)
     * @return The grid, or null if unavailable
     */
    @Nullable
    public static IGrid getGridFromSource(@Nullable IActionSource src) {
        if (src == null) return null;

        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getGrid();
        }

        return null;
    }

    /**
     * Extracts the ME Grid from an ISaveProvider (typically a drive or chest).
     * <p>
     * Used as a fallback when the action source doesn't provide a valid grid.
     * </p>
     *
     * @param container The save provider (typically TileDrive or TileChest)
     * @return The grid, or null if unavailable
     */
    @Nullable
    public static IGrid getGridFromContainer(@Nullable ISaveProvider container) {
        if (container == null) return null;

        if (container instanceof IActionHost) {
            IActionHost host = (IActionHost) container;
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getGrid();
        }

        return null;
    }

    /**
     * Extracts the ME Grid from either the action source or the container.
     * <p>
     * Tries the action source first (preferred), then falls back to the container.
     * This ensures grid notifications work even when the action source doesn't
     * have a valid machine reference (e.g., certain crafting scenarios).
     * </p>
     *
     * @param src       The action source (may be null or missing machine)
     * @param container The save provider (typically TileDrive or TileChest)
     * @return The grid, or null if unavailable from both sources
     */
    @Nullable
    public static IGrid getGridFromSourceOrContainer(@Nullable IActionSource src, @Nullable ISaveProvider container) {
        IGrid grid = getGridFromSource(src);
        if (grid != null) return grid;

        return getGridFromContainer(container);
    }
}
