package com.cells.cells.creative.fluid;

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
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.cells.creative.AbstractCreativeCellContainer;
import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddCreativeFluidFilter;


/**
 * GUI screen for the Creative ME Fluid Cell.
 * Displays a 9x7 grid of fluid filter slots with JEI drag-drop support.
 */
public class GuiCreativeFluidCell extends AbstractCreativeCellGui<ContainerCreativeFluidCell> {

    private final CreativeFluidCellTankAdapter tankAdapter;

    public GuiCreativeFluidCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerCreativeFluidCell(playerInv, hand));
        this.tankAdapter = new CreativeFluidCellTankAdapter(this.container.getFilterHandler());
    }

    @Override
    protected String getTitleKey() {
        return "cells.creative_cell.fluid.title";
    }

    @Override
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return new GuiCreativeFluidFilterSlot(this.tankAdapter, slotIndex, x, y);
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();

        // Send empty fluid map to clear all filters on server
        Map<Integer, IAEFluidStack> emptyMap = new HashMap<>();
        for (int i = 0; i < AbstractCreativeCellContainer.FILTER_SLOTS; i++) {
            emptyMap.put(i, null);
        }
        NetworkHandler.instance().sendToServer(new PacketFluidSlot(emptyMap));
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

        if (fluid != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddCreativeFluidFilter(fluid));
            return true;
        }

        QuickAddHelper.sendNoValidError("fluid");
        return false;
    }

    @Override
    protected List<Target<?>> createTargets(Object ingredient) {
        FluidStack fluidStack = QuickAddHelper.toFluidStack(ingredient);
        if (fluidStack == null) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all fluid filter slots as valid targets
        for (GuiCustomSlot slot : this.guiSlots) {
            if (!(slot instanceof GuiCreativeFluidFilterSlot)) continue;

            final GuiCreativeFluidFilterSlot filterSlot = (GuiCreativeFluidFilterSlot) slot;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + filterSlot.xPos(), getGuiTop() + filterSlot.yPos(), 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    // Use unified slot logic
                    FluidStack fluid = QuickAddHelper.toFluidStack(ingredient);
                    if (fluid == null) return;

                    // Check for duplicates before adding
                    if (container.getFilterHandler().isInFilter(fluid)) {
                        QuickAddHelper.sendDuplicateError();
                        return;
                    }

                    // Delegate to slot - handles conversion and server sync
                    filterSlot.acceptResource(fluid);
                }
            };

            targets.add(target);
            mapTargetSlot.put(target, slot);
        }

        return targets;
    }
}
