package com.cells.integration.mekanismenergistics;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import mekanism.api.gas.GasStack;
import mekanism.common.capabilities.Capabilities;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;


/**
 * Tile entity for the Gas Export Interface block.
 * Provides filter slots (gas-based filters) and internal gas tanks.
 * Pulls gases from the ME network to fill tanks based on filter configuration.
 * Adjacent machines can extract gases from the tanks.
 * <p>
 * Business logic is delegated to {@link GasInterfaceLogic} to avoid code
 * duplication with part and import variants.
 */
public class TileGasExportInterface extends AbstractInterfaceTile<GasInterfaceLogic>
        implements IGasInterfaceHost, GasInterfaceLogic.Host {

    public TileGasExportInterface() {
        this.initLogic(new GasInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Nullable
    @Override
    public GasStack getGasInTank(int slot) {
        return this.logic.getGasInTank(slot);
    }

    @Override
    public void setGasInTank(int slot, @Nullable GasStack gas) {
        this.logic.setGasInTank(slot, gas);
    }

    @Override
    @Nullable
    public GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        return this.logic.drainGasFromTank(slot, maxDrain, doDrain);
    }

    @Override
    public int getMainGuiId() {
        return GasInterfaceGuiHandler.GUI_GAS_EXPORT_INTERFACE;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability, facing);
    }
}
