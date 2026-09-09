package com.cells.gui.subnetproxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;
import appeng.util.item.AEItemStack;

import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;


/**
 * Helper utilities for converting resource objects to filter ItemStacks
 * in the Subnet Proxy container.
 * <p>
 * Isolates optional mod-specific conversions (gas, essentia) behind
 * availability checks to prevent class loading errors.
 */
final class SubnetProxyContainerHelper {

    private SubnetProxyContainerHelper() {}

    /**
     * Convert an IAEFluidStack to a FluidDummyItem filter stack.
     */
    @Nonnull
    static ItemStack fluidToFilterStack(IAEFluidStack aeFluid) {
        FluidStack fluid = aeFluid.getFluidStack();
        if (fluid == null) return ItemStack.EMPTY;

        fluid.amount = Fluid.BUCKET_VOLUME;
        ItemStack dummyStack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack(1).orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return ItemStack.EMPTY;

        FluidDummyItem dummyItem = (FluidDummyItem) dummyStack.getItem();
        dummyItem.setFluidStack(dummyStack, fluid);

        return dummyStack;
    }

    /**
     * Convert a mod-specific resource (IAEGasStack, IAEEssentiaStack) to a filter ItemStack.
     * Returns EMPTY if the resource type is not recognized or the mod is not loaded.
     */
    @Nonnull
    static ItemStack modResourceToFilterStack(Object resource) {
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            ItemStack result = SubnetProxyGasHelper.gasResourceToFilterStack(resource);
            if (!result.isEmpty()) return result;
        }

        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            ItemStack result = SubnetProxyEssentiaHelper.essentiaResourceToFilterStack(resource);
            if (!result.isEmpty()) return result;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Convert a FluidStack to an IAEItemStack wrapping a FluidDummyItem.
     * Used by JEI ghost ingredient handling where the ingredient is already
     * known to be a fluid (bypasses filter mode).
     */
    @Nullable
    static IAEItemStack fluidToFilterAEStack(FluidStack fluid) {
        if (fluid == null) return null;
        IAEFluidStack aeFluid = appeng.fluids.util.AEFluidStack.fromFluidStack(fluid);
        ItemStack dummy = fluidToFilterStack(aeFluid);
        if (dummy.isEmpty()) return null;

        return AEItemStack.fromItemStack(dummy);
    }
}
