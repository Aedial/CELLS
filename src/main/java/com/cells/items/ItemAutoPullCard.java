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
    private static final int DEFAULT_INTERVAL = ContainerPullPushCard.MINIMUM_INTERVAL;
    private static final long DEFAULT_QUANTITY = ContainerPullPushCard.MINIMUM_QUANTITY;

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
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_KEY_INTERVAL)) return DEFAULT_INTERVAL;

        return tag.getInteger(NBT_KEY_INTERVAL);
    }

    /**
     * Set the pull interval in the card's NBT.
     *
     * @param stack The card ItemStack
     * @param interval The pull interval in ticks
     */
    public static void setInterval(ItemStack stack, int interval) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        tag.setInteger(NBT_KEY_INTERVAL, Math.max(1, interval));
    }

    /**
     * Get the pull quantity from the card's NBT.
     *
     * @param stack The card ItemStack
     * @return The pull quantity
     */
    public static long getQuantity(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_KEY_QUANTITY)) return DEFAULT_QUANTITY;

        return tag.getLong(NBT_KEY_QUANTITY);
    }

    /**
     * Set the pull quantity in the card's NBT.
     *
     * @param stack The card ItemStack
     * @param quantity The pull quantity
     */
    public static void setQuantity(ItemStack stack, long quantity) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        tag.setLong(NBT_KEY_QUANTITY, Math.max(0, quantity));
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

        long quantity = getQuantity(stack);
        String quantityStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(quantity);

        tooltip.add(I18n.format("tooltip.cells.pull_card.desc", quantityStr, timeStr));
        tooltip.add("§b" + I18n.format("tooltip.cells.click_to_configure"));
        tooltip.add(I18n.format("tooltip.cells.wip_do_not_use"));

        addCompatibilityTooltip(tooltip, "import_interface");
    }
}
