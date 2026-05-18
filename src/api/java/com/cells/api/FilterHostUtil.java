package com.cells.api;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;


/**
 * Utility methods for working with {@link IFilterHost} instances.
 */
public final class FilterHostUtil {

    private FilterHostUtil() {}

    /**
     * Snapshot all filter slots while preserving empty positions.
     */
    @Nonnull
    public static List<ItemStack> snapshotFilters(@Nonnull IFilterHost host) {
        List<ItemStack> filters = new ArrayList<>();
        int slotCount = Math.max(0, host.getFilterSlots());

        for (int slot = 0; slot < slotCount; slot++) {
            filters.add(normalizeFilter(host.getFilter(slot)));
        }

        return filters;
    }

    /**
     * Find the first slot containing a matching filter entry.
     */
    public static int findFilterSlot(@Nonnull IFilterHost host, @Nonnull ItemStack stack) {
        ItemStack normalized = normalizeFilter(stack);
        if (normalized.isEmpty()) return -1;

        int slotCount = Math.max(0, host.getFilterSlots());
        for (int slot = 0; slot < slotCount; slot++) {
            if (matchesFilter(host.getFilter(slot), normalized)) return slot;
        }

        return -1;
    }

    /**
     * Add a filter to the first empty slot, skipping duplicates.
     */
    public static boolean addFilter(@Nonnull IFilterHost host, @Nonnull ItemStack stack) {
        ItemStack normalized = normalizeFilter(stack);
        if (normalized.isEmpty()) return false;

        int slotCount = Math.max(0, host.getFilterSlots());
        int emptySlot = -1;

        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack existing = normalizeFilter(host.getFilter(slot));
            if (matchesFilter(existing, normalized)) return false;
            if (emptySlot < 0 && existing.isEmpty()) emptySlot = slot;
        }

        if (emptySlot < 0) return false;

        host.setFilter(emptySlot, normalized);
        return true;
    }

    /**
     * Remove the first matching filter entry.
     */
    public static boolean removeFilter(@Nonnull IFilterHost host, @Nonnull ItemStack stack) {
        int slot = findFilterSlot(host, stack);
        if (slot < 0) return false;

        host.setFilter(slot, ItemStack.EMPTY);
        return true;
    }

    /**
     * Toggle a filter entry by exact item plus NBT identity.
     */
    public static boolean toggleFilter(@Nonnull IFilterHost host, @Nonnull ItemStack stack) {
        if (removeFilter(host, stack)) return true;
        return addFilter(host, stack);
    }

    /**
     * Compare two filter stacks by item and NBT only, ignoring count.
     */
    public static boolean matchesFilter(@Nonnull ItemStack left, @Nonnull ItemStack right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return ItemStack.areItemsEqual(left, right)
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    /**
     * Normalize a filter stack to a single-item identity stack.
     */
    @Nonnull
    public static ItemStack normalizeFilter(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return normalized;
    }
}