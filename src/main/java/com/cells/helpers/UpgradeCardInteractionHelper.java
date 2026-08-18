package com.cells.helpers;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.items.IUpgradeModule;


/**
 * Shared right-click behaviour for devices that accept AE2 upgrade cards.
 * <p>
 * A click inserts at most one card. This mirrors placing a card into a normal
 * one-item upgrade slot while still allowing each device's inventory filter to
 * decide whether that particular card is valid.
 */
public final class UpgradeCardInteractionHelper {

    private UpgradeCardInteractionHelper() {
    }

    /**
     * Whether a held stack is considered an upgrade card.
     * This does not mean the upgrade inventory will accept it, just that it
     * may be handled by it.
     */
    public static boolean isUpgradeCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IUpgradeModule;
    }

    /**
     * Try to insert the held upgrade card into the first accepting slot.
     *
     * @return the remainder that must replace the held stack when insertion
     *         succeeds, or {@code null} when every slot rejects the card
    */
    @Nullable
    public static ItemStack tryInsertOne(@Nonnull ItemStack stack,
                                         @Nonnull IItemHandler upgrades) {
        if (!isUpgradeCard(stack)) return null;

        final int originalCount = stack.getCount();
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (!upgrades.isItemValid(slot, stack)) continue;

            ItemStack remainder = upgrades.insertItem(slot, stack, false);
            if (remainder.isEmpty() || remainder.getCount() < originalCount) return remainder;
        }

        return null;
    }

    /**
     * Try the supplied upgrade inventories in order, returning the held-stack
     * remainder from the first one that accepts a card.
    */
    @Nullable
    public static ItemStack tryInsertOne(@Nonnull final ItemStack stack,
                                         @Nonnull final List<IItemHandler> upgradeInventories) {
        for (IItemHandler upgrades : upgradeInventories) {
            ItemStack remainder = tryInsertOne(stack, upgrades);
            if (remainder != null) return remainder;
        }

        return null;
    }
}
