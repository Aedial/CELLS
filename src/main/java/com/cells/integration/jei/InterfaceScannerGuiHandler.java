package com.cells.integration.jei;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.fluids.items.FluidDummyItem;

import mezz.jei.api.gui.IAdvancedGuiHandler;

import com.cells.gui.scanner.GuiInterfaceScanner;
import com.cells.network.sync.ResourceType;


/**
 * JEI ingredient lookup for the scanner's resource slots and interface icons.
 */
public final class InterfaceScannerGuiHandler implements IAdvancedGuiHandler<GuiInterfaceScanner> {

    @Override
    public Class<GuiInterfaceScanner> getGuiContainerClass() {
        return GuiInterfaceScanner.class;
    }

    @Override
    @Nullable
    public Object getIngredientUnderMouse(GuiInterfaceScanner gui, int mouseX, int mouseY) {
        ItemStack stack = gui.getHoveredItemStack(mouseX, mouseY);
        if (stack.isEmpty()) return null;

        if (stack.getItem() instanceof FluidDummyItem) {
            return ((FluidDummyItem) stack.getItem()).getFluidStack(stack);
        }

        if (ResourceType.GAS.isAvailable()) {
            Object gas = getGas(stack);
            if (gas != null) return gas;
        }

        if (ResourceType.ESSENTIA.isAvailable()) {
            Object aspect = getAspect(stack);
            if (aspect != null) return aspect;
        }

        return stack;
    }

    @Optional.Method(modid = "mekeng")
    @Nullable
    private Object getGas(ItemStack stack) {
        if (!(stack.getItem() instanceof com.mekeng.github.common.item.ItemDummyGas)) return null;

        return ((com.mekeng.github.common.item.ItemDummyGas) stack.getItem()).getGasStack(stack);
    }

    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    private Object getAspect(ItemStack stack) {
        if (!(stack.getItem() instanceof thaumicenergistics.item.ItemDummyAspect)) return null;

        return ((thaumicenergistics.item.ItemDummyAspect) stack.getItem()).getAspect(stack);
    }
}
