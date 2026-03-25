package com.cells.parts;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.cells.Tags;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.IFluidInterfaceHost;
import com.cells.gui.CellsGuiHandler;


/**
 * Part version of the Fluid Export Interface.
 * Can be placed on cables and behaves identically to the block version.
 * <p>
 * Business logic is delegated to {@link FluidInterfaceLogic} to avoid code
 * duplication with tile and import variants.
 */
public class PartFluidExportInterface extends AbstractInterfacePart<FluidInterfaceLogic>
        implements IFluidInterfaceHost, FluidInterfaceLogic.Host {

    private static final String prefix = "part/export_interface/fluid/";

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, prefix + "base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, new ResourceLocation(Tags.MODID, prefix + "has_channel"));

    public PartFluidExportInterface(final ItemStack is) {
        super(is);
        setLogic(new FluidInterfaceLogic(this));
    }

    @Override
    protected PartModel getModelOff() {
        return MODELS_OFF;
    }

    @Override
    protected PartModel getModelOn() {
        return MODELS_ON;
    }

    @Override
    protected PartModel getModelHasChannel() {
        return MODELS_HAS_CHANNEL;
    }

    @Override
    protected String getMemoryCardName() {
        return "tile.cells.export_interface.fluid";
    }

    @Override
    public boolean isExport() {
        return true;
    }

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_PART_FLUID_EXPORT_INTERFACE;
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
    public boolean hasCapability(Capability<?> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return true;
        return super.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(this.logic.getExternalHandler());
        }
        return super.getCapability(capability);
    }
}
