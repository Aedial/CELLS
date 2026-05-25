package com.cells.blocks.compactingpatternexposer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.ReadableNumberConverter;

import com.cells.gui.ResourceRenderer;
import com.cells.gui.slots.ItemFilterSlot;


/**
 * Compacting exposer filter slot with a visible pattern multiplier overlay.
 */
public class CompactingFilterSlot extends ItemFilterSlot {

    private final LongSupplier multiplierSupplier;

    public CompactingFilterSlot(ItemProvider provider, int slot, int x, int y, LongSupplier multiplierSupplier) {
        super(provider, slot, x, y);
        this.multiplierSupplier = multiplierSupplier;
    }

    @Override
    public void slotClicked(ItemStack clickStack, int mouseButton) {
        if (mouseButton == 1 && this.getResource() == null) return;

        super.slotClicked(clickStack, mouseButton);
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, IAEItemStack resource) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        super.drawResourceContent(mc, mouseX, mouseY, partialTicks, resource);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceRenderer.renderStackSize(mc.fontRenderer, this.multiplierSupplier.getAsLong(), this.xPos(), this.yPos());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        IAEItemStack resource = this.getResource();
        if (resource == null) return null;

        List<String> lines = new ArrayList<>(this.getResourceTooltipLines(resource));
        lines.add("");
        lines.add("§7" + I18n.format(
            "cells.compacting_pattern_exposer.multiplier.tooltip",
            ReadableNumberConverter.INSTANCE.toWideReadableForm(this.multiplierSupplier.getAsLong())
        ));
        lines.add("");
        lines.add("§b" + I18n.format("cells.filter_slot.hint.left_click_1"));
        lines.add("§b" + I18n.format("cells.filter_slot.hint.left_click_2"));
        lines.add("§b" + I18n.format("cells.compacting_pattern_exposer.filter.hint.right_click"));

        return String.join("\n", lines);
    }

    @Override
    @Nullable
    protected Object getTooltipIngredient(IAEItemStack resource) {
        return super.getTooltipIngredient(resource);
    }
}