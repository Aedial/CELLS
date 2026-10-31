package com.cells.items.upgrades;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;

import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.iointerface.IIOInterfaceHost;


public class ItemRedirectUnselectedCard extends AbstractCustomUpgrade {

    private static final String BINDING = "destination";

    public ItemRedirectUnselectedCard() {
        super("redirect_unselected_card");
    }

    @Nullable
    public static BlockPos getDestination(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(BINDING, Constants.NBT.TAG_COMPOUND)) return null;

        return BlockPos.fromLong(tag.getCompoundTag(BINDING).getLong("pos"));
    }

    public static int getDimension(ItemStack stack) {
        return stack.getTagCompound().getCompoundTag(BINDING).getInteger("dimension");
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        BlockPos destination = getDestination(stack);
        if (pos.equals(destination) && getDimension(stack) == world.provider.getDimension()) {
            return EnumActionResult.PASS;
        }

        TileEntity tile = world.getTileEntity(pos);
        Object target = tile;
        if (tile instanceof IPartHost) {
            SelectedPart selected = ((IPartHost) tile).selectPart(new Vec3d(hitX, hitY, hitZ));
            IPart part = selected == null ? null : selected.part;
            target = part;
        }

        if (target instanceof IIOInterfaceHost
                || target instanceof IInterfaceHost && !((IInterfaceHost) target).isExport()) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) tag = new NBTTagCompound();

            NBTTagCompound binding = new NBTTagCompound();
            binding.setLong("pos", pos.toLong());
            binding.setInteger("dimension", world.provider.getDimension());
            tag.setTag(BINDING, binding);
            stack.setTagCompound(tag);
        }

        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.cells.redirect_unselected_card.desc"));
        BlockPos pos = getDestination(stack);
        if (pos == null) {
            tooltip.add(I18n.format("tooltip.cells.redirect_unselected_card.unbound"));
        } else {
            tooltip.add(I18n.format("tooltip.cells.redirect_unselected_card.bound",
                    pos.getX(), pos.getY(), pos.getZ(), getDimension(stack)));
        }

        addCompatibilityTooltip(tooltip, "import_interface");
    }
}
