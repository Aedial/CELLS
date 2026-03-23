package com.cells.cells.creative.gas;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;

import appeng.client.gui.widgets.GuiCustomSlot;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.network.packet.CGasSlotSync;
import com.mekeng.github.MekEng;

import mekanism.api.gas.GasStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddCreativeGasFilter;


/**
 * GUI screen for the Creative ME Gas Cell.
 * Displays a 9x7 grid of gas filter slots with JEI drag-drop support.
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

        // Send empty gas map to clear all filters on server
        Map<Integer, IAEGasStack> emptyMap = new HashMap<>();
        for (int i = 0; i < AbstractCreativeCellContainer.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        MekEng.proxy.netHandler.sendToServer(new CGasSlotSync(emptyMap));
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        GasStack gas = QuickAddHelper.getGasUnderCursor(hoveredSlot);

        if (gas != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddCreativeGasFilter(gas));
            return true;
        }

        QuickAddHelper.sendNoValidError("gas");
        return false;
    }

    @Override
    protected List<Target<?>> createTargets(Object ingredient) {
        GasStack gasStack = QuickAddHelper.toGasStack(ingredient);
        if (gasStack == null) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all gas filter slots as valid targets
        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiCreativeGasFilterSlot)) continue;

            final GuiCreativeGasFilterSlot filterSlot = (GuiCreativeGasFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    // Use unified slot logic
                    GasStack gas = QuickAddHelper.toGasStack(ingredient);
                    if (gas == null) return;

                    // Check for duplicates before adding
                    if (container.getFilterHandler().isInFilter(gas)) {
                        QuickAddHelper.sendDuplicateError();
                        return;
                    }

                    // Delegate to slot - handles conversion and server sync
                    filterSlot.acceptResource(gas);
                }
            };

            targets.add(target);
            mapTargetSlot.put(target, slot);
        }

        return targets;
    }
}
