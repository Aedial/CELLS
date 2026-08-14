package com.cells.gui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;

import appeng.client.gui.widgets.GuiTabButton;


/**
 * A GuiTabButton with a dynamic tooltip that updates based on a supplier.
 * Used to show current setting values in button tooltips.
 */
public class DynamicTooltipTabButton extends GuiTabButton {

    private final Supplier<String> messageSupplier;

    /**
     * Create a tab button with a dynamic tooltip.
     *
     * @param x X position
     * @param y Y position
     * @param iconIndex Icon index in AE2's states.png
     * @param messageSupplier Supplier that provides the current tooltip text
     * @param itemRenderer Item renderer
     */
    public DynamicTooltipTabButton(int x, int y, int iconIndex, Supplier<String> messageSupplier, RenderItem itemRenderer) {
        super(x, y, iconIndex, "", itemRenderer);
        this.messageSupplier = messageSupplier;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        // GuiTabButton's states atlas contains transparent pixels, so we set the
        // GL blend state because we cannot ensure it is properly set by preceding widgets.
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        super.drawButton(minecraft, mouseX, mouseY, partialTicks);
    }

    @Override
    public String getMessage() {
        return this.messageSupplier.get();
    }
}
