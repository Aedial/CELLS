package com.cells.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import com.cells.items.ItemEqualDistributionCard;
import com.cells.items.ItemOverflowCard;


/**
 * Utility class for handling cell upgrades.
 * <p>
 * Provides common methods for detecting installed upgrades and
 * generating tooltip information about them.
 * </p>
 */
public final class CellUpgradeHelper {

    private CellUpgradeHelper() {
        // Utility class
    }

    /**
     * Checks if an Overflow Card is installed in the given upgrade inventory.
     *
     * @param upgrades The upgrade inventory to check
     * @return true if an Overflow Card is found
     */
    public static boolean hasOverflowCard(IItemHandler upgrades) {
        if (upgrades == null) return false;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ItemOverflowCard) return true;
        }

        return false;
    }

    /**
     * Gets the Equal Distribution Card tier value, or 0 if not installed.
     * <p>
     * Returns the type limit value (1, 2, 4, 8, 16, 32, or 63) from the
     * installed Equal Distribution Card, or 0 if none is installed.
     * </p>
     *
     * @param upgrades The upgrade inventory to check
     * @return The type limit, or 0 if no card installed
     */
    public static int getEqualDistributionLimit(IItemHandler upgrades) {
        if (upgrades == null) return 0;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ItemEqualDistributionCard) {
                return ItemEqualDistributionCard.getTierValue(stack);
            }
        }

        return 0;
    }

    /**
     * Adds upgrade information to a tooltip list.
     * <p>
     * Lists all custom upgrades installed in the cell.
     * </p>
     *
     * @param upgrades The upgrade inventory to check
     * @param tooltip  The tooltip list to add to
     */
    @SideOnly(Side.CLIENT)
    public static void addUpgradeTooltips(IItemHandler upgrades, List<String> tooltip) {
        if (upgrades == null) return;

        List<String> upgradeInfo = new ArrayList<>();

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ItemOverflowCard) {
                tooltip.add("\u00a7c" + I18n.format("tooltip.cells.upgrade.overflow_active"));
            }

            if (stack.getItem() instanceof ItemEqualDistributionCard) {
                int limit = ItemEqualDistributionCard.getTierValue(stack);
                tooltip.add("\u00a7b" + I18n.format("tooltip.cells.upgrade.equal_distribution_active", limit));
            }
        }
    }
}
