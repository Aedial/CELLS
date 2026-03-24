package com.cells.cells.creative.gas;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;

import appeng.client.gui.widgets.GuiCustomSlot;

import mekanism.api.gas.GasStack;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
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

    private final CreativeGasCellTankAdapter tankAdapter;

    public GuiCreativeGasCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeGasCell(playerInv, hand));
        this.tankAdapter = new CreativeGasCellTankAdapter(this.container.getFilterHandler());
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.gas.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new GuiCreativeGasFilterSlot(this.tankAdapter, slotIndex, x, y);
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
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(ResourceType.GAS, gas));
            return true;
        }

        QuickAddHelper.sendNoValidError("gas");
        return false;
    }
}
