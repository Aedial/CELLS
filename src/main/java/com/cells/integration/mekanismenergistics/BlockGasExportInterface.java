package com.cells.integration.mekanismenergistics;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;


/**
 * Block for the Gas Export Interface.
 * Pulls gases from the ME network based on filter configuration and provides
 * them to adjacent gas handlers for extraction.
 */
public class BlockGasExportInterface extends AbstractResourceInterfaceBlock<TileGasExportInterface> {

    public BlockGasExportInterface() {
        super(
            "export_gas_interface",
            "export_interface.gas",
            TileGasExportInterface.class,
            GasInterfaceGuiHandler.GUI_GAS_EXPORT_INTERFACE
        );
    }
}
