package com.cells.util;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.util.Platform;
import appeng.util.inv.filter.IAEItemFilter;

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

    private final ItemStack cellStack;

    public CustomCellUpgrades(final ItemStack cellStack, final int slots) {
        super(cellStack, null, slots);
        this.cellStack = cellStack;
        this.readFromNBT(Platform.openNbtData(cellStack), "upgrades");
        this.setFilter(new CustomUpgradeFilter());
    }

    @Override
    public int getMaxInstalled(final Upgrades upgrades) {
        return 4;
    }

    @Override
    protected void onContentsChanged(int slot) {
        this.writeToNBT(Platform.openNbtData(this.cellStack), "upgrades");
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

            // Accept our custom upgrade cards
            if (stack.getItem() instanceof ItemOverflowCard) {
                return countInstalled(ItemOverflowCard.class) < 1; // Max 1
            }

            if (stack.getItem() instanceof ItemEqualDistributionCard) {
                return countInstalled(ItemEqualDistributionCard.class) < 1; // Max 1
            }

            // Also accept standard AE2 upgrades if getMaxInstalled allows
            if (stack.getItem() instanceof IUpgradeModule) {
                Upgrades u = ((IUpgradeModule) stack.getItem()).getType(stack);
                if (u != null) return getInstalledUpgrades(u) < getMaxInstalled(u);
            }

            return false;
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
