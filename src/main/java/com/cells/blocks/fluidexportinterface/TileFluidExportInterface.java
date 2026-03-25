package com.cells.blocks.fluidexportinterface;

import javax.annotation.Nullable;

import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import com.cells.blocks.interfacebase.AbstractInterfaceTile;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.IFluidInterfaceHost;
import com.cells.gui.CellsGuiHandler;


/**
 * Tile entity for the Fluid Export Interface block.
 * Provides filter slots (fluid-based filters) and internal fluid tanks.
 * Requests fluids from the ME network that match the filter configuration.
 * Exposes stored fluids for external extraction.
 * <p>
 * Business logic is delegated to {@link FluidInterfaceLogic} to avoid code
 * duplication with part and import variants.
 */
public class TileFluidExportInterface extends AbstractInterfaceTile<FluidInterfaceLogic>
        implements IFluidInterfaceHost, FluidInterfaceLogic.Host {

    public TileFluidExportInterface() {
        this.initLogic(new FluidInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Nullable
    @Override
    public FluidStack getFluidInTank(int slot) {
        return this.logic.getFluidInTank(slot);
    }

    @Override
    public FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        return this.logic.drainFluidFromTank(slot, maxDrain, doDrain);
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_FLUID_EXPORT_INTERFACE;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability, facing);
    }
}
