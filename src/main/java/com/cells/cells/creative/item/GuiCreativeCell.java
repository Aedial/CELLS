package com.cells.cells.creative.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiCustomSlot;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.ItemFilterSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * GUI screen for the Creative ME Cell.
 * <p>
 * Displays a 9x7 grid of item filter slots using unified {@link ItemFilterSlot}.
 * JEI drag-drop support is handled automatically by the base class.
 */
public class GuiCreativeCell extends AbstractCreativeCellGui<ContainerCreativeCell> {

    public GuiCreativeCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeCell(playerInv, hand));
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.item.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new ItemFilterSlot(container::getFilter, slotIndex, x, y);
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();

        // Send empty map to clear all filters on server using unified packet
        Map<Integer, Object> emptyMap = new HashMap<>();
        for (int i = 0; i < AbstractCreativeCellContainer.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketResourceSlot(ResourceType.ITEM, emptyMap));
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);

        if (!item.isEmpty()) {
            IAEItemStack iaeItem = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(item);
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(ResourceType.ITEM, iaeItem));
            return true;
        }

        return false;
    }
}
