package com.cells.gui.subnetproxy;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;

import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Static utility for converting an ItemStack based on the current filter mode (ResourceType).
 * <p>
 * For example, when the filter mode is FLUID and a water bucket is placed,
 * it converts to a FluidDummyItem representing water.
 * Gas and Essentia modes similarly convert to their respective dummy items.
 * <p>
 * In ITEM mode, normal items pass through unchanged while Recovery Containers
 * unwrap to their stored item content instead of encoding the orb item itself.
 */
public final class SlotFakeConvertingFilter {

    private SlotFakeConvertingFilter() {}

    /**
     * Test whether an ItemStack can be converted for the given filter mode,
     * without actually modifying any slot.
     * <p>
     * Used by the filter widget and container for pre-validation and error
     * reporting before the slot interaction takes place.
     *
     * @param stack The raw ItemStack to test
     * @param mode  The current filter mode
     * @return The converted stack, or null if the item is not valid for this mode
     */
    @Nullable
    static ItemStack testConvertForMode(ItemStack stack, ResourceType mode) {
        return convertForMode(stack, mode);
    }

    /**
     * Convert an ItemStack to the appropriate dummy item for the given filter mode.
     *
     * @param stack The raw ItemStack being placed
     * @param mode  The current filter mode
     * @return The converted stack, or null if the item is not valid for this mode
     */
    @Nullable
    private static ItemStack convertForMode(ItemStack stack, ResourceType mode) {
        switch (mode) {
            case ITEM:
                return QuickAddHelper.getItemFromItemStack(stack);

            case FLUID:
                // If already a FluidDummyItem, pass through without re-conversion.
                // This prevents buckets placed as items from being re-converted
                // to fluids when the filter mode changes or items are re-validated.
                if (stack.getItem() instanceof FluidDummyItem) return stack;
                return convertToFluidDummy(stack);

            case GAS:
                if (Loader.isModLoaded("mekeng")) {
                    return SubnetProxyGasHelper.convertToGasDummy(stack);
                }
                return null;

            case ESSENTIA:
                if (Loader.isModLoaded("thaumicenergistics")) {
                    return SubnetProxyEssentiaHelper.convertToEssentiaDummy(stack);
                }
                return null;

            default:
                return stack;
        }
    }

    /**
     * Convert an ItemStack to a FluidDummyItem if it contains a fluid.
     * If the stack is already a FluidDummyItem, return it as-is.
     */
    @Nullable
    private static ItemStack convertToFluidDummy(ItemStack stack) {
        // Already a fluid dummy? Keep it.
        if (stack.getItem() instanceof FluidDummyItem) return stack;

        // Try to extract fluid from the item (buckets, tanks, etc.)
        FluidStack fluid = QuickAddHelper.getFluidFromItemStack(stack);
        if (fluid == null) return null;

        // Create a FluidDummyItem with this fluid
        fluid.amount = Fluid.BUCKET_VOLUME;
        ItemStack dummyStack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack(1).orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return null;

        FluidDummyItem dummyItem = (FluidDummyItem) dummyStack.getItem();
        dummyItem.setFluidStack(dummyStack, fluid);

        return dummyStack;
    }
}
