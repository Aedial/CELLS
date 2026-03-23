package com.cells.cells.creative.fluid;

import javax.annotation.Nullable;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.IAEFluidTank;


/**
 * Adapter to expose CreativeFluidCellFilterHandler as IAEFluidTank.
 * <p>
 * Used for fluid sync between client and server via FluidSyncHelper.
 */
public class CreativeFluidCellTankAdapter implements IAEFluidTank {

    private final CreativeFluidCellFilterHandler filterHandler;

    public CreativeFluidCellTankAdapter(CreativeFluidCellFilterHandler filterHandler) {
        this.filterHandler = filterHandler;
    }

    @Override
    public int getSlots() {
        return filterHandler.getSlots();
    }

    @Override
    public IAEFluidStack getFluidInSlot(int slot) {
        FluidStack fluid = filterHandler.getFluidInSlot(slot);
        return fluid != null ? AEFluidStack.fromFluidStack(fluid) : null;
    }

    @Override
    public void setFluidInSlot(int slot, IAEFluidStack fluid) {
        FluidStack fluidStack = fluid != null ? fluid.getFluidStack() : null;
        filterHandler.setFluidInSlot(slot, fluidStack);
    }

    // =====================
    // IFluidHandler implementation (not used for filters, but required by interface)
    // =====================

    @Override
    public IFluidTankProperties[] getTankProperties() {
        IFluidTankProperties[] props = new IFluidTankProperties[filterHandler.getSlots()];

        for (int i = 0; i < props.length; i++) {
            final FluidStack fluid = filterHandler.getFluidInSlot(i);

            props[i] = new IFluidTankProperties() {
                @Override
                @Nullable
                public FluidStack getContents() {
                    return fluid;
                }

                @Override
                public int getCapacity() {
                    return 1; // Ghost slot - only 1 mB
                }

                @Override
                public boolean canFill() {
                    return true; // Can set filter
                }

                @Override
                public boolean canDrain() {
                    return false; // Cannot extract from ghost slot
                }

                @Override
                public boolean canFillFluidType(FluidStack fluidStack) {
                    return true; // Any fluid can be set as filter
                }

                @Override
                public boolean canDrainFluidType(FluidStack fluidStack) {
                    return false;
                }
            };
        }

        return props;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        // Ghost slots don't actually fill
        return 0;
    }

    @Override
    @Nullable
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        // Ghost slots don't drain
        return null;
    }

    @Override
    @Nullable
    public FluidStack drain(int maxDrain, boolean doDrain) {
        // Ghost slots don't drain
        return null;
    }
}
