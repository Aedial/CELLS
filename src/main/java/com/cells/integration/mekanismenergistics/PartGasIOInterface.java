package com.cells.integration.mekanismenergistics;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;

import com.cells.network.sync.ResourceType;
import com.cells.parts.AbstractIOInterfacePart;
import com.cells.parts.PartModelsHelper;


/**
 * Part version of the Gas I/O Interface.
 * Combines gas import and export capabilities in a single cable part
 * with direction-switching tabs.
 */
public class PartGasIOInterface extends AbstractIOInterfacePart<GasInterfaceLogic> {

    private static final String prefix = "part/io_interface/gas/";
    private static final Object[] MODELS = PartModelsHelper.createInterfaceModels(prefix);

    @PartModels
    public static final PartModel MODELS_OFF = (PartModel) MODELS[1];

    @PartModels
    public static final PartModel MODELS_ON = (PartModel) MODELS[2];

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = (PartModel) MODELS[3];

    /**
     * Typed host wrapper for GasInterfaceLogic (marker interface, no extra methods).
     */
    private class GasDirectionHost extends DirectionHost implements GasInterfaceLogic.Host {
        GasDirectionHost(boolean export) { super(export); }
    }

    public PartGasIOInterface(final ItemStack is) {
        super(is);

        GasDirectionHost importHost = new GasDirectionHost(false);
        GasDirectionHost exportHost = new GasDirectionHost(true);

        this.initLogics(
            new GasInterfaceLogic(importHost),
            new GasInterfaceLogic(exportHost)
        );
    }

    @Override
    protected PartModel getModelOff() { return MODELS_OFF; }

    @Override
    protected PartModel getModelOn() { return MODELS_ON; }

    @Override
    protected PartModel getModelHasChannel() { return MODELS_HAS_CHANNEL; }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.GAS;
    }

    @Override
    public int getMainGuiId() {
        return GasInterfaceGuiHandler.GUI_PART_GAS_IO_INTERFACE;
    }

    // ============================== Capabilities ==============================

    @Override
    public boolean hasCapability(Capability<?> capability) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(
                new CompositeGasHandler(
                    this.importLogic.getExternalHandler(),
                    this.exportLogic.getExternalHandler()
                )
            );
        }
        return super.getCapability(capability);
    }

    // ============================== Composite Gas Handler ==============================

    /**
     * Routes receiveGas to the import handler, drawGas to the export handler.
     */
    private static class CompositeGasHandler implements IGasHandler {

        private final IGasHandler importHandler;
        private final IGasHandler exportHandler;

        CompositeGasHandler(IGasHandler importHandler, IGasHandler exportHandler) {
            this.importHandler = importHandler;
            this.exportHandler = exportHandler;
        }

        @Override
        public int receiveGas(net.minecraft.util.EnumFacing side, GasStack stack, boolean doTransfer) {
            return this.importHandler.receiveGas(side, stack, doTransfer);
        }

        @Override
        public GasStack drawGas(net.minecraft.util.EnumFacing side, int amount, boolean doTransfer) {
            return this.exportHandler.drawGas(side, amount, doTransfer);
        }

        @Override
        public boolean canReceiveGas(net.minecraft.util.EnumFacing side, mekanism.api.gas.Gas type) {
            return this.importHandler.canReceiveGas(side, type);
        }

        @Override
        public boolean canDrawGas(net.minecraft.util.EnumFacing side, mekanism.api.gas.Gas type) {
            return this.exportHandler.canDrawGas(side, type);
        }

        @Override
        @Nonnull
        public GasTankInfo[] getTankInfo() {
            GasTankInfo[] importInfo = this.importHandler.getTankInfo();
            GasTankInfo[] exportInfo = this.exportHandler.getTankInfo();
            GasTankInfo[] combined = new GasTankInfo[importInfo.length + exportInfo.length];
            System.arraycopy(importInfo, 0, combined, 0, importInfo.length);
            System.arraycopy(exportInfo, 0, combined, importInfo.length, exportInfo.length);
            return combined;
        }
    }
}
