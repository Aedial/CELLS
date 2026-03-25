package com.cells.cells.creative.fluid;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.FluidStack;

import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.fluids.util.AEFluidStack;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.FluidFilterSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * GUI screen for the Creative ME Fluid Cell.
 * <p>
 * Displays a 9x7 grid of fluid filter slots.
 * JEI drag-drop support is handled automatically by the base class.
 */
public class GuiCreativeFluidCell extends AbstractCreativeCellGui<ContainerCreativeFluidCell> {

    public GuiCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeFluidCell(playerInv, hand));
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.fluid.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new FluidFilterSlot(container::getFilter, slotIndex, x, y);
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();

        // Send empty map to clear all filters on server using unified packet
        Map<Integer, Object> emptyMap = new HashMap<>();
        for (int i = 0; i < AbstractCreativeCellContainer.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketResourceSlot(ResourceType.FLUID, emptyMap));
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

        if (fluid != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(
                new PacketQuickAddFilter(ResourceType.FLUID, AEFluidStack.fromFluidStack(fluid))
            );
            return true;
        }

        QuickAddHelper.sendNoValidError("fluid");
        return false;
    }
}
