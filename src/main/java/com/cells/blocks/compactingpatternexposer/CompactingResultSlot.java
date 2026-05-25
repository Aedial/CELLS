package com.cells.blocks.compactingpatternexposer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.util.ReadableNumberConverter;

import com.cells.gui.ResourceRenderer;


/**
 * Read-only ghost slot that previews the compacting result for a filter slot.
 */
public class CompactingResultSlot extends GuiCustomSlot {

    @FunctionalInterface
    public interface PreviewProvider {
        @Nullable
        TileCompactingPatternExposer.PatternPreview getPreview(int slot);
    }

    private final PreviewProvider provider;

    public CompactingResultSlot(PreviewProvider provider, int slot, int x, int y) {
        super(slot, x, y);
        this.provider = provider;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        TileCompactingPatternExposer.PatternPreview preview = this.provider.getPreview(this.id);
        if (preview == null) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(preview.getOutput(), this.xPos(), this.yPos());
        mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, preview.getOutput(), this.xPos(), this.yPos(), null);
        RenderHelper.disableStandardItemLighting();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        ResourceRenderer.renderStackSize(mc.fontRenderer, preview.getOutputCount(), this.xPos(), this.yPos());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        TileCompactingPatternExposer.PatternPreview preview = this.provider.getPreview(this.id);
        if (preview == null) return null;

        String key = preview.isUpward()
            ? "cells.compacting_pattern_exposer.result.up"
            : "cells.compacting_pattern_exposer.result.down";

        List<String> lines = this.getTooltipLines(preview.getOutput());
        lines.add("");
        lines.add(I18n.format(
            key,
            this.formatCount(preview.getInputCount()),
            this.formatCount(preview.getOutputCount())
        ));
        lines.add(I18n.format(
            "cells.compacting_pattern_exposer.multiplier.tooltip",
            this.formatCount(preview.getMultiplier())
        ));

        return String.join("\n", lines);
    }

    // Use the normal vanilla item tooltip path so mod-added tooltip lines match the interface slots.
    private List<String> getTooltipLines(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return new ArrayList<>(stack.getTooltip(mc.player, currentTooltipFlag()));
        } catch (Throwable ignored) {
            return new ArrayList<>(Collections.singletonList(stack.getDisplayName()));
        }
    }

    private static ITooltipFlag currentTooltipFlag() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.gameSettings.advancedItemTooltips
            ? ITooltipFlag.TooltipFlags.ADVANCED
            : ITooltipFlag.TooltipFlags.NORMAL;
    }

    private String formatCount(long amount) {
        return ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
    }

    @Override
    public boolean isVisible() {
        return true;
    }
}