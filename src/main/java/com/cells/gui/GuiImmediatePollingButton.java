package com.cells.gui;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;


/**
 * AE2-style refresh button used to trigger the interface's polling cycle.
 */
public class GuiImmediatePollingButton extends GuiButton implements ITooltip {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("appliedenergistics2",
        "textures/guis/states.png");

    private static final ResourceLocation ICON_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/refresh.png");

    private static final int BUTTON_SIZE = 16;
    private static final int BACKGROUND_SHEET_SIZE = 256;
    private static final int ICON_SHEET_SIZE = 32;
    private static final int BACKGROUND_U = BACKGROUND_SHEET_SIZE - BUTTON_SIZE;
    private static final int BACKGROUND_V = BACKGROUND_SHEET_SIZE - BUTTON_SIZE;
    private static final int HOVER_OVERLAY_COLOR = 0x35FFFFFF;

    private final Supplier<String> tooltipSupplier;

    public GuiImmediatePollingButton(int buttonId, int x, int y, Supplier<String> tooltipSupplier) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void drawButton(@Nonnull Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        float tint = this.enabled ? 1.0F : 0.5F;

        GlStateManager.enableBlend();
        GlStateManager.color(tint, tint, tint, 1.0F);

        mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(
            this.x,
            this.y,
            BACKGROUND_U,
            BACKGROUND_V,
            BUTTON_SIZE,
            BUTTON_SIZE,
            BACKGROUND_SHEET_SIZE,
            BACKGROUND_SHEET_SIZE
        );

        mc.getTextureManager().bindTexture(ICON_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, this.enabled ? 1.0F : 0.5F);
        Gui.drawScaledCustomSizeModalRect(
            this.x,
            this.y,
            0,
            0,
            ICON_SHEET_SIZE,
            ICON_SHEET_SIZE,
            BUTTON_SIZE,
            BUTTON_SIZE,
            ICON_SHEET_SIZE,
            ICON_SHEET_SIZE
        );

        if (this.enabled && this.hovered) {
            Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, HOVER_OVERLAY_COLOR);
        }

        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public String getMessage() {
        return this.tooltipSupplier != null ? this.tooltipSupplier.get() : "";
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}