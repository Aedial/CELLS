package com.cells.items;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEPartLocation;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;
import com.cells.gui.CellsGuiHandler;
import com.cells.scanner.ScannerTraversal;
import com.cells.scanner.InterfaceScannerCache;


/**
 * Handheld access to a static view of empty filtered interface buffers.
 */
public class ItemInterfaceScanner extends Item {

    public ItemInterfaceScanner() {
        setRegistryName(Tags.MODID, "interface_scanner");
        setTranslationKey(Tags.MODID + ".interface_scanner");
        setMaxStackSize(1);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;

        TileEntity tile = world.getTileEntity(pos);
        AEPartLocation location = AEPartLocation.fromFacing(side);
        if (tile instanceof IPartHost) {
            SelectedPart selected = ((IPartHost) tile).selectPart(new Vec3d(hitX, hitY, hitZ));
            if (selected.part == null) return openPrevious(player, world, hand);

            location = selected.side;
        }

        if (ScannerTraversal.getNode(world, pos, location) == null) {
            return openPrevious(player, world, hand);
        }

        InterfaceScannerCache.getId(player.getHeldItem(hand));
        int guiId = (CellsGuiHandler.GUI_INTERFACE_SCANNER << 4) | (hand.ordinal() << 3) | location.ordinal();
        player.openGui(Cells.instance, guiId, world, pos.getX(), pos.getY(), pos.getZ());

        return EnumActionResult.SUCCESS;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand) {
        EnumActionResult result = world.isRemote ? EnumActionResult.SUCCESS : openPrevious(player, world, hand);
        return new ActionResult<>(result, player.getHeldItem(hand));
    }

    private EnumActionResult openPrevious(EntityPlayer player, World world, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (InterfaceScannerCache.get(player, InterfaceScannerCache.getId(stack)) == null) {
            player.sendMessage(new TextComponentTranslation("cells.interface_scanner.no_scan"));
            return EnumActionResult.FAIL;
        }

        int guiId = (CellsGuiHandler.GUI_INTERFACE_SCANNER_RESULTS << 4) | (hand.ordinal() << 3);
        player.openGui(Cells.instance, guiId, world, 0, 0, 0);
        return EnumActionResult.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                                @Nonnull ITooltipFlag flag) {
        tooltip.add(I18n.format("cells.interface_scanner.tooltip"));
    }
}
