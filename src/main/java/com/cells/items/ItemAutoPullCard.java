package com.cells.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import com.cells.Cells;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.pullpush.ContainerPullPushCard;
import com.cells.util.PollingRateUtils;


/**
 * Auto-Pull Card - an upgrade card for Import Interfaces.
 * <p>
 * When installed in an Import Interface's upgrade slot, the interface will
 * automatically pull items/fluids/etc. from adjacent inventories at the
 * configured interval.
 * <p>
 * Right-click to configure the pull interval. Each card can have a different
 * interval stored in its NBT.
 * <p>
 * <b>Compatibility:</b> Only works with Import Interfaces (all types: item,
 * fluid, gas, essentia).
 */
public class ItemAutoPullCard extends AbstractCustomUpgrade {

    private static final String NBT_KEY_INTERVAL = "PullInterval";
    private static final String NBT_KEY_QUANTITY = "PullQuantity";
    private static final String NBT_KEY_KEEP = "PullKeepQuantity";
    private static final int DEFAULT_INTERVAL = ContainerPullPushCard.DEFAULT_INTERVAL;
    private static final int DEFAULT_QUANTITY = ContainerPullPushCard.MINIMUM_QUANTITY;
    private static final int DEFAULT_KEEP = ContainerPullPushCard.MINIMUM_KEEP_QUANTITY;

    public ItemAutoPullCard() {
        super("pull_card");
    }

    /**
     * Get the pull interval from the card's NBT.
     *
     * @param stack The card ItemStack
     * @return The pull interval in ticks
     */
    public static int getInterval(ItemStack stack) {
        return AbstractCustomUpgrade.getIntKey(stack, NBT_KEY_INTERVAL, DEFAULT_INTERVAL);
    }

    /**
     * Set the pull interval in the card's NBT.
     *
     * @param stack The card ItemStack
     * @param interval The pull interval in ticks
     */
    public static void setInterval(ItemStack stack, int interval) {
        AbstractCustomUpgrade.setIntKey(stack, NBT_KEY_INTERVAL, interval, 1);
    }

    /**
     * Get the pull quantity from the card's NBT.
     *
     * @param stack The card ItemStack
     * @return The pull quantity
     */
    public static int getQuantity(ItemStack stack) {
        return AbstractCustomUpgrade.getIntKey(stack, NBT_KEY_QUANTITY, DEFAULT_QUANTITY);
    }

    /**
     * Set the pull quantity in the card's NBT.
     *
     * @param stack The card ItemStack
     * @param quantity The pull quantity
     */
    public static void setQuantity(ItemStack stack, int quantity) {
        AbstractCustomUpgrade.setIntKey(stack, NBT_KEY_QUANTITY, quantity, 0);
    }

    /**
     * Get the keep quantity from the card's NBT.
     *
     * @param stack The card ItemStack
     * @return The keep quantity
     */
    public static int getKeepQuantity(ItemStack stack) {
        return AbstractCustomUpgrade.getIntKey(stack, NBT_KEY_KEEP, DEFAULT_KEEP);
    }

    /**
     * Set the keep quantity in the card's NBT.
     *
     * @param stack The card ItemStack
     * @param keepQuantity The keep quantity
     */
    public static void setKeepQuantity(ItemStack stack, int keepQuantity) {
        AbstractCustomUpgrade.setIntKey(stack, NBT_KEY_KEEP, keepQuantity, 0);
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // Open configuration GUI
        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_PULL_PUSH_CARD,
                world, hand.ordinal(), 0, 0);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        // Show current interval
        int interval = getInterval(stack);
        String timeStr = PollingRateUtils.format(interval);

        int quantity = getQuantity(stack);
        String quantityStr = String.format("%,d", quantity);

        int keep = getKeepQuantity(stack);
        String keepStr = String.format("%,d", keep);

        tooltip.add(I18n.format("tooltip.cells.pull_card.desc", quantityStr, timeStr));

        if (keep > 0) {
            tooltip.add(I18n.format("tooltip.cells.push_pull_card.limit.desc", keepStr));
        }

        tooltip.add("§b" + I18n.format("tooltip.cells.click_to_configure"));

        if (interval < DEFAULT_INTERVAL) {
            tooltip.add("");
            tooltip.add(I18n.format("tooltip.cells.push_pull_card.interval_warning"));
        }

        tooltip.add("");
        tooltip.add(I18n.format("tooltip.cells.useatyourownrisk"));

        addCompatibilityTooltip(tooltip, "import_interface");
    }
}
