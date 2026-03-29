package com.cells.integration.thaumicenergistics;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;


/**
 * Block for the Essentia Export Interface.
 * A filtered interface that pulls essentia from the ME network based on filter configuration.
 * External machines can extract essentia via the Thaumcraft tube network.
 * <p>
 * Implements {@link thaumcraft.api.aspects.IEssentiaTransport} with HIGH suction,
 * acting as an essentia SINK for the tube network.
 */
public class BlockEssentiaExportInterface extends AbstractResourceInterfaceBlock<TileEssentiaExportInterface> {

    public BlockEssentiaExportInterface() {
        super(
            "export_essentia_interface",
            "export_interface.essentia",
            TileEssentiaExportInterface.class,
            EssentiaInterfaceGuiHandler.GUI_ESSENTIA_EXPORT_INTERFACE
        );
    }

    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos,
                                @Nonnull Block blockIn, @Nonnull BlockPos fromPos) {
        if (world.isRemote) return;

        TileEssentiaExportInterface tile = this.getTileEntity(world, pos);
        if (tile != null) tile.getInterfaceLogic().onNeighborChanged(fromPos);
    }
}
