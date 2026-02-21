package com.cells.blocks.importinterface;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
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
 * Block for the Import Interface.
 * A filtered interface that only accepts items matching its filter configuration.
 */
public class BlockImportInterface extends AEBaseTileBlock {

    public BlockImportInterface() {
        super(Material.IRON);
        this.setRegistryName(Tags.MODID, "import_interface");
        this.setTranslationKey(Tags.MODID + ".import_interface");
        this.setCreativeTab(CellsCreativeTab.instance);
        this.setHardness(2.2F);
        this.setResistance(6.0F);
        this.setTileEntity(TileImportInterface.class);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add("§7" + I18n.format("tooltip.cells.import_interface.info"));
    }

    @Override
    public boolean onActivated(final World w, final BlockPos pos, final EntityPlayer p,
                               final EnumHand hand, final @Nullable ItemStack heldItem,
                               final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) return false;

        final TileImportInterface tile = this.getTileEntity(w, pos);
        if (tile != null) {
            if (!w.isRemote) {
                p.openGui(Cells.instance, CellsGuiHandler.GUI_IMPORT_INTERFACE, w, pos.getX(), pos.getY(), pos.getZ());
            }
            return true;
        }

        return false;
    }

    @Override
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
        // Nothing special needed for redstone handling
    }
}
