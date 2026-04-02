package com.cells.integration.thaumicenergistics;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;


/**
 * Block for the Essentia Import Interface.
 * A filtered interface that only accepts essentia matching its filter configuration.
 * Essentia is pushed into the interface by the Thaumcraft tube network and automatically
 * imported into the ME network.
 */
public class BlockEssentiaImportInterface extends AbstractResourceInterfaceBlock<TileEssentiaImportInterface> {

    public BlockEssentiaImportInterface() {
        super(
            "import_essentia_interface",
            "import_interface.essentia",
            TileEssentiaImportInterface.class,
            EssentiaInterfaceGuiHandler.GUI_ESSENTIA_IMPORT_INTERFACE
        );
    }

    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos,
                                @Nonnull Block blockIn, @Nonnull BlockPos fromPos) {
        if (world.isRemote) return;

        TileEssentiaImportInterface tile = this.getTileEntity(world, pos);
        if (tile != null) tile.getInterfaceLogic().onNeighborChanged(fromPos);
    }
}
