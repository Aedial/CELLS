package com.cells.blocks.fluidimportinterface;

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
 * Tile entity for the Fluid Import Interface block.
 * Provides filter slots (fluid-based filters) and internal fluid tanks.
 * Only accepts fluids that match the filter in the corresponding slot.
 * Automatically imports stored fluids into the ME network.
 * <p>
 * Business logic is delegated to {@link FluidInterfaceLogic} to avoid code
 * duplication with part and export variants.
 */
public class TileFluidImportInterface extends AbstractInterfaceTile<FluidInterfaceLogic>
        implements IFluidInterfaceHost, FluidInterfaceLogic.Host {

    public TileFluidImportInterface() {
        this.initLogic(new FluidInterfaceLogic(this));
    }

    @Override
    public boolean isExport() {
        return false;
    }

    @Nullable
    @Override
    public FluidStack getFluidInTank(int slot) {
        return this.logic.getFluidInTank(slot);
    }

    @Override
    public int insertFluidIntoTank(int slot, FluidStack fluid) {
        return this.logic.insertFluidIntoTank(slot, fluid);
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_FLUID_IMPORT_INTERFACE;
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
