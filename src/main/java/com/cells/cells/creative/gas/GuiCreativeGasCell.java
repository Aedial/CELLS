package com.cells.cells.creative.gas;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;

import appeng.client.gui.widgets.GuiCustomSlot;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.GasFilterSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * GUI screen for the Creative ME Gas Cell.
 * <p>
 * Displays a 9x7 grid of gas filter slots.
 * JEI drag-drop support is handled automatically by the base class.
 */
public class GuiCreativeGasCell extends AbstractCreativeCellGui<ContainerCreativeGasCell> {

    public GuiCreativeGasCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeGasCell(playerInv, hand));
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.gas.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new GasFilterSlot(container::getFilter, slotIndex, x, y);
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();

        // Send empty map to clear all filters on server using unified packet
        Map<Integer, Object> emptyMap = new HashMap<>();
        for (int i = 0; i < AbstractCreativeCellContainer.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketResourceSlot(ResourceType.GAS, emptyMap));
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        GasStack gas = QuickAddHelper.getGasUnderCursor(hoveredSlot);

        if (gas != null) {
            IAEGasStack iaeGas = AEGasStack.of(gas);
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(ResourceType.GAS, iaeGas));
            return true;
        }

        // Only show error if there was actually something under cursor that wasn't a gas
        // If hoveredSlot is null or empty, don't show an error - user just pressed keybind over nothing
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            QuickAddHelper.sendNoValidError("gas");
        }

        return true;
    }
}
