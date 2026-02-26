package com.cells.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.Platform;
import appeng.util.inv.filter.IAEItemFilter;

import com.cells.Cells;
import com.cells.ItemRegistry;
import com.cells.items.ItemCompressionTierCard;
import com.cells.items.ItemDecompressionTierCard;
import com.cells.items.ItemEqualDistributionCard;
import com.cells.items.ItemOverflowCard;


/**
 * Custom upgrade inventory for Cells mod storage cells.
 * <p>
 * Extends AE2's StackUpgradeInventory but allows our custom upgrade items
 * (OverflowCard, EqualDistributionCard) in addition to standard AE2 upgrades.
 * </p>
 */
public class CustomCellUpgrades extends StackUpgradeInventory {

    public enum CustomUpgrades {
        OVERFLOW,
        EQUAL_DISTRIBUTION,
        COMPRESSION_TIER,
        DECOMPRESSION_TIER
    }

    private static final Map<Item, CustomUpgrades> UPGRADE_ITEM_CLASSES = new HashMap<>();
    static {
        UPGRADE_ITEM_CLASSES.put(ItemRegistry.OVERFLOW_CARD, CustomUpgrades.OVERFLOW);
        UPGRADE_ITEM_CLASSES.put(ItemRegistry.EQUAL_DISTRIBUTION_CARD, CustomUpgrades.EQUAL_DISTRIBUTION);
        UPGRADE_ITEM_CLASSES.put(ItemRegistry.COMPRESSION_TIER_CARD, CustomUpgrades.COMPRESSION_TIER);
        UPGRADE_ITEM_CLASSES.put(ItemRegistry.DECOMPRESSION_TIER_CARD, CustomUpgrades.DECOMPRESSION_TIER);
    }


    private final ItemStack cellStack;
    private final List<CustomUpgrades> allowedCustomUpgrades;

    public CustomCellUpgrades(final ItemStack cellStack, final int slots,
            final List<CustomUpgrades> allowedCustomUpgrades) {
        super(cellStack, null, slots);
        this.cellStack = cellStack;
        this.readFromNBT(Platform.openNbtData(cellStack), "upgrades");
        this.setFilter(new CustomUpgradeFilter());

        if (allowedCustomUpgrades == null) {
            this.allowedCustomUpgrades = Arrays.asList(CustomUpgrades.values());
        } else {
            this.allowedCustomUpgrades = allowedCustomUpgrades;
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        this.writeToNBT(Platform.openNbtData(this.cellStack), "upgrades");
    }

    /**
     * Override to support standard AE2 cell upgrades (FUZZY, INVERTER, STICKY).
     * The parent class checks AE2's upgrade registry, but our custom cells aren't registered there.
     * We explicitly allow the same upgrades that standard AE2 cells support.
     */
    @Override
    public int getMaxInstalled(final Upgrades upgrades) {
        switch (upgrades) {
            case FUZZY:
            case INVERTER:
            case STICKY:
                return 1; // Standard AE2 cells support 1 of each
            default:
                return super.getMaxInstalled(upgrades);
        }
    }

    /**
     * Custom filter that accepts our mod's upgrade items.
     */
    private class CustomUpgradeFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, @Nonnull ItemStack stack) {
            if (stack.isEmpty()) return false;

            CustomUpgrades customUpgrade = UPGRADE_ITEM_CLASSES.get(stack.getItem());
            if (customUpgrade == null) {
                // Accept standard AE2 upgrades if getMaxInstalled allows
                if (stack.getItem() instanceof IUpgradeModule) {
                    Upgrades u = ((IUpgradeModule) stack.getItem()).getType(stack);
                    if (u != null) return getInstalledUpgrades(u) < getMaxInstalled(u);
                }
            } else if (!allowedCustomUpgrades.contains(customUpgrade)) {
                return false;  // not allowed by this cell's configuration
            }

            // Accept our custom upgrade cards
            if (customUpgrade == CustomUpgrades.OVERFLOW) {
                return countInstalled(ItemOverflowCard.class) < 1; // Max 1
            }

            if (customUpgrade == CustomUpgrades.EQUAL_DISTRIBUTION) {
                return countInstalled(ItemEqualDistributionCard.class) < 1; // Max 1
            }

            // Compression and Decompression tier cards are mutually exclusive (max 1 total)
            if (customUpgrade == CustomUpgrades.COMPRESSION_TIER) {
                return countInstalled(ItemCompressionTierCard.class) < 1
                    && countInstalled(ItemDecompressionTierCard.class) < 1;
            }

            if (customUpgrade == CustomUpgrades.DECOMPRESSION_TIER) {
                return countInstalled(ItemDecompressionTierCard.class) < 1
                    && countInstalled(ItemCompressionTierCard.class) < 1;
            }

            Cells.LOGGER.warn("Upgrade item {} is not recognized as a valid upgrade for this cell", stack);
            return false; // Reject anything else
        }

        private int countInstalled(Class<?> itemClass) {
            int count = 0;
            for (int i = 0; i < getSlots(); i++) {
                ItemStack existing = getStackInSlot(i);
                if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) count++;
            }

            return count;
        }
    }
}
