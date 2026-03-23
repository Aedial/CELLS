package com.cells.cells.creative.item;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.storage.data.IAEItemStack;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.cells.creative.AbstractCreativeCellGui;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketQuickAddCreativeItemFilter;


/**
 * GUI screen for the Creative ME Cell.
 * Displays a 9x7 grid of item filter slots with JEI drag-drop support.
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
    protected void createFilterSlots() {
        // Item cell uses SlotFake added by the container, no need to add GUI slots here
    }

    @Override
    protected void doClearFilters() {
        container.clearAllFilters();
        // No need to send a packet - filter handler writes directly to cell NBT
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);

        if (!item.isEmpty()) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddCreativeItemFilter(item));
            return true;
        }

        return false;
    }

    @Override
    protected List<Target<?>> createTargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) return Collections.emptyList();

        ItemStack itemStack = (ItemStack) ingredient;
        if (itemStack.isEmpty()) return Collections.emptyList();

        List<Target<?>> targets = new ArrayList<>();

        // Add all filter slots (SlotFake) as valid targets
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (!(slot instanceof SlotFake)) continue;

            SlotFake fakeSlot = (SlotFake) slot;
            if (!fakeSlot.isSlotEnabled()) continue;

            Target<Object> target = new Target<Object>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + fakeSlot.xPos, getGuiTop() + fakeSlot.yPos, 16, 16);
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    if (!(ingredient instanceof ItemStack)) return;

                    ItemStack stack = (ItemStack) ingredient;
                    if (stack.isEmpty()) return;

                    // Check for duplicates before adding
                    if (container.getFilterHandler().isInFilter(stack)) {
                        QuickAddHelper.sendDuplicateError();
                        return;
                    }

                    try {
                        IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                        PacketInventoryAction packet = new PacketInventoryAction(
                            InventoryAction.PLACE_JEI_GHOST_ITEM,
                            fakeSlot,
                            aeStack
                        );
                        NetworkHandler.instance().sendToServer(packet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            targets.add(target);
            mapTargetSlot.put(target, slot);
        }

        return targets;
    }
}
