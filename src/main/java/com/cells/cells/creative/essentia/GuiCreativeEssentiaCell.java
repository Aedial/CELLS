package com.cells.cells.creative.essentia;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;

import appeng.client.gui.widgets.GuiCustomSlot;

import thaumicenergistics.api.EssentiaStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddCreativeEssentiaFilter;


/**
 * GUI screen for the Creative ME Essentia Cell.
 * Displays a 9x7 grid of essentia filter slots with JEI drag-drop support.
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
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddCreativeEssentiaFilter(essentia));
            return true;
        }

        QuickAddHelper.sendNoValidError("essentia");
        return false;
    }

    @Override
    protected List<Target<?>> createTargets(Object ingredient) {
        EssentiaStack essentiaStack = QuickAddHelper.toEssentiaStack(ingredient);
        if (essentiaStack == null) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all essentia filter slots as valid targets
        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiCreativeEssentiaFilterSlot)) continue;

            final GuiCreativeEssentiaFilterSlot filterSlot = (GuiCreativeEssentiaFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    EssentiaStack essentia = QuickAddHelper.toEssentiaStack(ingredient);
                    if (essentia == null) return;

                    // Check for duplicates before adding
                    if (container.getFilterHandler().isInFilter(essentia)) {
                        QuickAddHelper.sendDuplicateError();
                        return;
                    }

                    // Delegate to slot - handles conversion, local state, and server sync
                    filterSlot.acceptResource(essentia);
                }
            };

            targets.add(target);
            mapTargetSlot.put(target, slot);
        }

        return targets;
    }
}
