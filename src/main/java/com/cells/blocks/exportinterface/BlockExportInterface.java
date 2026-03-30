package com.cells.blocks.exportinterface;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;
import com.cells.gui.CellsGuiHandler;


/**
 * Block for the Export Interface.
 * A filtered interface that requests items from the network and exposes them for extraction.
 */
public class BlockExportInterface extends AbstractResourceInterfaceBlock<TileExportInterface> {

    public BlockExportInterface() {
        super(
            "export_interface",
            "export_interface",
            TileExportInterface.class,
            CellsGuiHandler.GUI_EXPORT_INTERFACE
        );
    }
}
