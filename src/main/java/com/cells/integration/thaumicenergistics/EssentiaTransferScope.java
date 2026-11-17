package com.cells.integration.thaumicenergistics;

import javax.annotation.Nullable;

import thaumcraft.api.aspects.IAspectContainer;

import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.iointerface.IIOInterfaceHost;


/**
 * Scoped simulation state for Storage Bus transfers through CELLS essentia buffers.
 */
public final class EssentiaTransferScope implements AutoCloseable {

    private final EssentiaInterfaceLogic logic;
    private final EssentiaInterfaceLogic exportLogic;
    private final boolean previousSimulation;
    private final boolean previousExportSimulation;

    private EssentiaTransferScope(EssentiaInterfaceLogic logic,
                                 @Nullable EssentiaInterfaceLogic exportLogic, boolean simulate) {
        this.logic = logic;
        this.exportLogic = exportLogic;
        this.previousSimulation = logic.setTransferSimulation(simulate);
        this.previousExportSimulation = exportLogic != null && exportLogic.setTransferSimulation(simulate);
    }

    @Nullable
    public static EssentiaTransferScope prepare(IAspectContainer container, boolean simulate) {
        if (container instanceof IIOInterfaceHost) {
            IIOInterfaceHost host = (IIOInterfaceHost) container;
            if (!(host.getImportLogic() instanceof EssentiaInterfaceLogic)
                    || !(host.getExportLogic() instanceof EssentiaInterfaceLogic)) return null;

            return new EssentiaTransferScope((EssentiaInterfaceLogic) host.getImportLogic(),
                (EssentiaInterfaceLogic) host.getExportLogic(), simulate);
        }

        Object logic;
        if (container instanceof IEssentiaInterfaceHost) {
            logic = ((IEssentiaInterfaceHost) container).getInterfaceLogic();
        } else if (container instanceof ICombinedInterfaceHost) {
            logic = ((ICombinedInterfaceHost) container).getEssentiaLogic();
        } else {
            return null;
        }

        if (!(logic instanceof EssentiaInterfaceLogic)) return null;

        return new EssentiaTransferScope((EssentiaInterfaceLogic) logic, null, simulate);
    }

    @Override
    public void close() {
        this.logic.setTransferSimulation(this.previousSimulation);
        if (this.exportLogic != null) this.exportLogic.setTransferSimulation(this.previousExportSimulation);
    }
}
