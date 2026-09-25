package com.cells.items.upgrades;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Upgrades;

import com.cells.cells.emc.EmcCellUpgradeInventory;
import com.cells.config.CellsConfig;


/**
 * Tiered upgrade card that unlocks additional EMC cell partition slots.
 */
public class ItemEmcCapacityCard extends AbstractCustomUpgrade {

    public ItemEmcCapacityCard() {
        super("emc_capacity_card", buildTierSlots(), buildTierNames());
    }

    private static int[] buildTierSlots() {
        int[] configuredSlots = CellsConfig.getEmcCellPartitionSlots();
        int upgradeCount = Math.max(0, configuredSlots.length - 1);
        int[] tierSlots = new int[upgradeCount];

        System.arraycopy(configuredSlots, 1, tierSlots, 0, upgradeCount);

        return tierSlots;
    }

    private static String[] buildTierNames() {
        int tierCount = CellsConfig.getEmcCellUpgradeTierCount();
        String[] tierNames = new String[tierCount];

        for (int i = 0; i < tierCount; i++) tierNames[i] = "tier" + (i + 1);

        return tierNames;
    }

    @Override
    public Upgrades getType(ItemStack itemstack) {
        return Upgrades.CAPACITY;
    }

    @Override
    @Nonnull
    @SideOnly(Side.CLIENT)
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return I18n.format(getTranslationKey(stack) + ".name");
    }

    public int getUnlockedSlots(ItemStack stack) {
        if (stack.isEmpty()) return CellsConfig.getEmcCellUnlockedSlots(0);

        if (stack.getItem() == this) return getUnlockedSlotsForUpgradeTier(stack.getMetadata());

        ItemStack installed = new EmcCellUpgradeInventory(stack).getStackInSlot(0);
        if (!installed.isEmpty() && installed.getItem() == this) {
            return getUnlockedSlotsForUpgradeTier(installed.getMetadata());
        }

        return CellsConfig.getEmcCellUnlockedSlots(0);
    }

    public int getUnlockedSlotsForUpgradeTier(int tier) {
        return CellsConfig.getEmcCellUnlockedSlots(getClampedTierIndex(tier) + 1);
    }

    public int getUpgradeTierNumber(ItemStack stack) {
        return getClampedTierIndex(stack.getMetadata()) + 1;
    }

    private int getClampedTierIndex(int tier) {
        int maxTier = Math.max(0, CellsConfig.getEmcCellUpgradeTierCount() - 1);
        return Math.max(0, Math.min(tier, maxTier));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.cells.emc_capacity_card.desc", getUnlockedSlotsForUpgradeTier(stack.getMetadata())));

        addCompatibilityTooltip(tooltip, "emc");
    }
}