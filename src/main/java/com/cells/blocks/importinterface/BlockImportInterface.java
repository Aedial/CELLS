package com.cells.blocks.importinterface;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;
import com.cells.gui.CellsGuiHandler;


/**
 * Block for the Import Interface.
 * A filtered interface that only accepts items matching its filter configuration.
 */
public class BlockImportInterface extends AbstractResourceInterfaceBlock<TileImportInterface> {

    public BlockImportInterface() {
        super(
            "import_interface",
            "import_interface",
            TileImportInterface.class,
            CellsGuiHandler.GUI_IMPORT_INTERFACE
        );
    }
}
