package com.cells.blocks.fluidimportinterface;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;
import com.cells.gui.CellsGuiHandler;


/**
 * Block for the Fluid Import Interface.
 * A filtered interface that only accepts fluids matching its filter configuration.
 * Fluids are extracted from containers (buckets, etc.) placed in the filter slots.
 */
public class BlockFluidImportInterface extends AbstractResourceInterfaceBlock<TileFluidImportInterface> {

    public BlockFluidImportInterface() {
        super(
            "import_fluid_interface",
            "import_interface.fluid",
            TileFluidImportInterface.class,
            CellsGuiHandler.GUI_FLUID_IMPORT_INTERFACE
        );
    }
}
