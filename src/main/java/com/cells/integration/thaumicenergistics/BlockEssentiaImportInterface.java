package com.cells.integration.thaumicenergistics;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceBlock;


/**
 * Block for the Essentia Import Interface.
 * A filtered interface that only accepts essentia matching its filter configuration.
 * Essentia is pushed into the interface by the Thaumcraft tube network and automatically
 * imported into the ME network.
 * <p>
 * Implements {@link thaumcraft.api.aspects.IEssentiaTransport} with LOW suction,
 * acting as an essentia SOURCE for the tube network.
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
}
