package com.cells.integration.mekanismenergistics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;

import com.cells.blocks.iointerface.AbstractIOInterfaceTile;
import com.cells.network.sync.ResourceType;


/**
 * Tile entity for the Gas I/O Interface block.
 * Combines a Gas Import Interface and a Gas Export Interface in a single block
 * with direction-switching tabs.
 * <p>
 * The gas capability is exposed as a composite handler that routes
 * receiveGas to the import logic and drawGas to the export logic.
 */
public class TileGasIOInterface extends AbstractIOInterfaceTile<GasInterfaceLogic> {

    /**
     * Typed host wrapper for GasInterfaceLogic (marker interface, no extra methods).
     */
    private class GasDirectionHost extends DirectionHost implements GasInterfaceLogic.Host {
        GasDirectionHost(boolean export) { super(export); }
    }

    public TileGasIOInterface() {
        GasDirectionHost importHost = new GasDirectionHost(false);
        GasDirectionHost exportHost = new GasDirectionHost(true);

        GasInterfaceLogic importLogic = new GasInterfaceLogic(importHost);
        GasInterfaceLogic exportLogic = new GasInterfaceLogic(exportHost);

        this.initLogics(importLogic, exportLogic);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.GAS;
    }

    @Override
    public int getMainGuiId() {
        return GasInterfaceGuiHandler.GUI_GAS_IO_INTERFACE;
    }

    // ============================== Capabilities ==============================

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(
                new CompositeGasHandler(
                    this.importLogic.getExternalHandler(),
                    this.exportLogic.getExternalHandler()
                )
            );
        }
        return super.getCapability(capability, facing);
    }

    // ============================== Composite Gas Handler ==============================

    /**
     * Composite handler that routes receiveGas to the import handler
     * and drawGas to the export handler.
     */
    private static class CompositeGasHandler implements IGasHandler {

        private final IGasHandler importHandler;
        private final IGasHandler exportHandler;

        CompositeGasHandler(IGasHandler importHandler, IGasHandler exportHandler) {
            this.importHandler = importHandler;
            this.exportHandler = exportHandler;
        }

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            return this.importHandler.receiveGas(side, stack, doTransfer);
        }

        @Override
        public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
            return this.exportHandler.drawGas(side, amount, doTransfer);
        }

        @Override
        public boolean canReceiveGas(EnumFacing side, mekanism.api.gas.Gas type) {
            return this.importHandler.canReceiveGas(side, type);
        }

        @Override
        public boolean canDrawGas(EnumFacing side, mekanism.api.gas.Gas type) {
            return this.exportHandler.canDrawGas(side, type);
        }

        @Override
        @Nonnull
        public GasTankInfo[] getTankInfo() {
            // Combine tank info from both handlers
            GasTankInfo[] importInfo = this.importHandler.getTankInfo();
            GasTankInfo[] exportInfo = this.exportHandler.getTankInfo();
            GasTankInfo[] combined = new GasTankInfo[importInfo.length + exportInfo.length];
            System.arraycopy(importInfo, 0, combined, 0, importInfo.length);
            System.arraycopy(exportInfo, 0, combined, importInfo.length, exportInfo.length);
            return combined;
        }
    }
}
