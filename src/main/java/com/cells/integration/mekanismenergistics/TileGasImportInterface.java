package com.cells.integration.mekanismenergistics;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import mekanism.api.gas.GasStack;
import mekanism.common.capabilities.Capabilities;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;


/**
 * Tile entity for the Gas Import Interface block.
 * Provides filter slots (gas-based filters) and internal gas tanks.
 * Only accepts gases that match the filter in the corresponding slot.
 * Automatically imports stored gases into the ME network.
 * <p>
 * Business logic is delegated to {@link GasInterfaceLogic} to avoid code
 * duplication with part and export variants.
 */
public class TileGasImportInterface extends AbstractInterfaceTile<GasInterfaceLogic>
        implements IGasInterfaceHost, GasInterfaceLogic.Host {

    public TileGasImportInterface() {
        this.initLogic(new GasInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return false;
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
    public int getMainGuiId() {
        return GasInterfaceGuiHandler.GUI_GAS_IMPORT_INTERFACE;
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
