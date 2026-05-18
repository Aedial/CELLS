package com.cells.api;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;


/**
 * Immutable preview entry for a CELLS interface view.
 */
public final class ResourcePreviewEntry {

    private final ResourceType resourceType;
    private final ItemStack displayStack;
    private final long amount;

    public ResourcePreviewEntry(@Nonnull ResourceType resourceType,
                                @Nonnull ItemStack displayStack,
                                long amount) {
        this.resourceType = resourceType != null ? resourceType : ResourceType.ITEM;

        if (displayStack.isEmpty()) {
            this.displayStack = ItemStack.EMPTY;
        } else {
            this.displayStack = displayStack.copy();
            this.displayStack.setCount(1);
        }

        this.amount = Math.max(0, amount);
    }

    @Nonnull
    public ResourceType getResourceType() {
        return this.resourceType;
    }

    @Nonnull
    public ItemStack getDisplayStack() {
        return this.displayStack;
    }

    public long getAmount() {
        return this.amount;
    }
}