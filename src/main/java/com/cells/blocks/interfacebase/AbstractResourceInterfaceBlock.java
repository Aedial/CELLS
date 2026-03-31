package com.cells.blocks.interfacebase;

import java.util.List;

import javax.annotation.Nonnull;
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
import appeng.tile.AEBaseTile;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Abstract base class for resource interface blocks (fluid, gas, etc.).
 * <p>
 * Subclasses only need to specify registry name, tile class, and GUI ID.
 *
 * @param <T> The tile entity class for this block
 */
public abstract class AbstractResourceInterfaceBlock<T extends AEBaseTile> extends AEBaseTileBlock {

    private final int guiId;
    private final String tooltipKey;
    private final Object guiHandler;

    /**
     * Construct a resource interface block.
     *
     * @param registryName The registry name (without mod ID prefix)
     * @param translationKey The translation key (without mod ID prefix)
     * @param tileClass The tile entity class to associate with this block
     * @param guiId The GUI ID to open when activated
     * @param guiHandler The mod instance or handler to use for openGui (Cells.instance or similar)
     */
    protected AbstractResourceInterfaceBlock(
            String registryName,
            String translationKey,
            Class<T> tileClass,
            int guiId,
            Object guiHandler
    ) {
        super(Material.IRON);
        this.guiId = guiId;
        this.guiHandler = guiHandler;
        this.tooltipKey = "tooltip." + Tags.MODID + "." + translationKey + ".info";

        this.setRegistryName(Tags.MODID, registryName);
        this.setTranslationKey(Tags.MODID + "." + translationKey);
        this.setCreativeTab(CellsCreativeTab.instance);
        this.setHardness(2.2F);
        this.setResistance(6.0F);
        this.setTileEntity(tileClass);
    }

    /**
     * Convenience constructor that uses Cells.instance as the GUI handler.
     */
    protected AbstractResourceInterfaceBlock(
            String registryName,
            String translationKey,
            Class<T> tileClass,
            int guiId
    ) {
        this(registryName, translationKey, tileClass, guiId, Cells.instance);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add("§7" + I18n.format(this.tooltipKey));
    }

    @Override
    public boolean onActivated(final World w, final BlockPos pos, final EntityPlayer p,
                               final EnumHand hand, final @Nullable ItemStack heldItem,
                               final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
        if (p.isSneaking()) return false;

        final T tile = this.getTileEntity(w, pos);
        if (tile == null) return false;

        if (!w.isRemote) {
            p.openGui(this.guiHandler, this.guiId, w, pos.getX(), pos.getY(), pos.getZ());
        }

        return true;
    }

    /**
     * Handle neighbor block changes to invalidate capability caches for auto-pull/push cards.
     * Delegates to the tile entity's logic to update the specific facing direction.
     */
    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos,
                                @Nonnull Block blockIn, @Nonnull BlockPos fromPos) {
        if (world.isRemote) return;

        final T tile = this.getTileEntity(world, pos);
        if (tile instanceof AbstractInterfaceTile) {
            AbstractInterfaceTile<?> interfaceTile = (AbstractInterfaceTile<?>) tile;
            IInterfaceLogic logic = interfaceTile.getInterfaceLogic();
            if (logic instanceof AbstractResourceInterfaceLogic) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) logic).onNeighborChanged(fromPos);
            }
        }
    }
}
