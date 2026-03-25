package com.cells.integration.thaumicenergistics;

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
}
