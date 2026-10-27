package com.cells.items.upgrades;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import com.cells.config.CellsConfig;


/**
 * Stocking thresholds for Export Interfaces, selected through card metadata.
 */
public class ItemStockingThresholdCard extends AbstractCustomUpgrade {

    private static final int TIERS = 8;

    public ItemStockingThresholdCard() {
        super("stocking_threshold_card", CellsConfig.getStockingThresholdMultipliers(), buildTierNames());
    }

    private static String[] buildTierNames() {
        int tierCount = CellsConfig.getStockingThresholdMultipliers().length;
        String[] names = new String[tierCount];
        for (int tier = 0; tier < tierCount; tier++) names[tier] = "tier" + Math.min(tier + 1, TIERS);

        return names;
    }

    @Override
    public int getTierValue(ItemStack stack) {
        int[] multipliers = CellsConfig.interfaces.stockingThresholdMultipliers;
        int tier = stack.getMetadata();
        return tier >= 0 && tier < multipliers.length ? multipliers[tier] : 1;
    }

    @Override
    public int getDisassemblyMetadata(ItemStack stack) {
        return 0;
    }

    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
            getTranslationKey() + ".name", getTierValue(stack));
    }

    @Override
    @Nonnull
    public String getTranslationKey(@Nonnull ItemStack stack) {
        return getTranslationKey();
    }

    public static int getInstalledMultiplier(@Nullable IItemHandler upgrades) {
        if (upgrades == null) return 0;

        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack stack = upgrades.getStackInSlot(slot);
            if (stack.getItem() instanceof ItemStockingThresholdCard) {
                return ((ItemStockingThresholdCard) stack.getItem()).getTierValue(stack);
            }
        }

        return 0;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
                                                    @Nonnull EnumHand hand) {
        if (player.isSneaking()) return super.onItemRightClick(world, player, hand);

        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            int tier = stack.getMetadata();
            int tierCount = Math.min(this.tierValues.length, CellsConfig.interfaces.stockingThresholdMultipliers.length);
            int nextTier = tier >= 0 && tier < tierCount - 1 ? tier + 1 : 0;
            stack.setItemDamage(nextTier);
            player.inventoryContainer.detectAndSendChanges();
            player.sendStatusMessage(new TextComponentTranslation(
                getTranslationKey() + ".name", getTierValue(stack)), true);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.cells.stocking_threshold_card.desc", getTierValue(stack)));
        tooltip.add(I18n.format("tooltip.cells.stocking_threshold_card.cycle"));
        addCompatibilityTooltip(tooltip, "export_interface");
    }
}
