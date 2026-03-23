package com.cells.blocks.fluidexportinterface;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;
import com.cells.gui.CellsGuiHandler;


/**
 * Block for the Fluid Export Interface.
 * A filtered interface that requests fluids from the network and exposes them for extraction.
 */
public class BlockFluidExportInterface extends AbstractResourceInterfaceBlock<TileFluidExportInterface> {

    public BlockFluidExportInterface() {
        super(
            "export_fluid_interface",
            "export_interface.fluid",
            TileFluidExportInterface.class,
            CellsGuiHandler.GUI_FLUID_EXPORT_INTERFACE
        );
    }
}
