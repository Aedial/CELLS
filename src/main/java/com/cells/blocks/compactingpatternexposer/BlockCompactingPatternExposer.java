package com.cells.blocks.compactingpatternexposer;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.block.AEBaseTileBlock;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;
import com.cells.gui.CellsGuiHandler;


/**
 * Block that exposes compacting and decompacting recipes as AE2 processing patterns.
 */
public class BlockCompactingPatternExposer extends AEBaseTileBlock {

    private static final String TOOLTIP_KEY = "tooltip.cells.compacting_pattern_exposer.info";

    public BlockCompactingPatternExposer() {
        super(Material.IRON);
        this.setRegistryName(Tags.MODID, "compacting_pattern_exposer");
        this.setTranslationKey(Tags.MODID + ".compacting_pattern_exposer");
        this.setCreativeTab(CellsCreativeTab.instance);
        this.setHardness(2.2F);
        this.setResistance(6.0F);
        this.setTileEntity(TileCompactingPatternExposer.class);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add("§7" + I18n.format(TOOLTIP_KEY));
    }

    @Override
    public boolean onActivated(final World world, final BlockPos pos, final EntityPlayer player,
                               final EnumHand hand, final @Nullable ItemStack heldItem,
                               final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
        if (player.isSneaking()) return false;

        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_COMPACTING_PATTERN_EXPOSER,
                world, pos.getX(), pos.getY(), pos.getZ());
        }

        return true;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        return this.onActivated(world, pos, player, hand, player.getHeldItem(hand), facing, hitX, hitY, hitZ);
    }
}