package com.cells.gui;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;


/**
 * Button to clear all filters in Import/Export interfaces.
 * Uses a custom 16x32 texture with two states (normal, pressed).
 * Position: placed at the right of the hotbar, at (186, 232) relative to GUI top-left.
 */
public class GuiClearFiltersButton extends GuiButton implements ITooltip {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/clear_button.png");
    private static final int BUTTON_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 16;

    private final Supplier<String> tooltipSupplier;

    /**
     * Create a clear filters button.
     *
     * @param buttonId Button ID (for event handling)
     * @param x X position (screen coordinates)
     * @param y Y position (screen coordinates)
     * @param tooltipSupplier Supplier that provides the tooltip text
     */
    public GuiClearFiltersButton(int buttonId, int x, int y, Supplier<String> tooltipSupplier) {
        super(buttonId, x, y, BUTTON_WIDTH, BUTTON_HEIGHT, "");
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        boolean hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Texture is 16 wide, 32 tall: top 16px = normal, bottom 16px = hovered
        int textureY = hovered ? BUTTON_HEIGHT : 0;
        Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, 0f, (float) textureY, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT * 2);

        GlStateManager.disableBlend();
    }

    // ITooltip implementation

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
