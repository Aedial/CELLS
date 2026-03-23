package com.cells.integration.mekanismenergistics;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;


/**
 * Block for the Gas Import Interface.
 * A filtered interface that only accepts gases matching its filter configuration.
 * Gases are pushed into the interface by adjacent gas handlers and automatically
 * imported into the ME network.
 */
public class BlockGasImportInterface extends AbstractResourceInterfaceBlock<TileGasImportInterface> {

    public BlockGasImportInterface() {
        super(
            "import_gas_interface",
            "import_interface.gas",
            TileGasImportInterface.class,
            GasInterfaceGuiHandler.GUI_GAS_IMPORT_INTERFACE
        );
    }
}
