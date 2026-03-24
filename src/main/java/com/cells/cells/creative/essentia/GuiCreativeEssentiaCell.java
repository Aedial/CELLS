package com.cells.cells.creative.essentia;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;

import appeng.client.gui.widgets.GuiCustomSlot;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI screen for the Creative ME Essentia Cell.
 * <p>
 * Displays a 9x7 grid of essentia filter slots.
 * JEI drag-drop support is handled automatically by the base class.
 */
public class GuiCreativeEssentiaCell extends AbstractCreativeCellGui<ContainerCreativeEssentiaCell> {

    public GuiCreativeEssentiaCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeEssentiaCell(playerInv, hand));
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.essentia.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new GuiCreativeEssentiaFilterSlot(this.container.getFilterHandler(), slotIndex, x, y);
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();

        // No special sync packet needed - the container already persists directly
        // to the ItemStack NBT. If we want to sync to server for safety:
        // CellsNetworkHandler.INSTANCE.sendToServer(new PacketClearEssentiaFilters());
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        EssentiaStack essentia = QuickAddHelper.getEssentiaUnderCursor(hoveredSlot);

        if (essentia != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(ResourceType.ESSENTIA, essentia));
            return true;
        }

        QuickAddHelper.sendNoValidError("essentia");
        return false;
    }
}
