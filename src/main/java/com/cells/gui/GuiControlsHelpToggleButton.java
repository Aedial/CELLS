package com.cells.gui;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Toggle button that shows/hides the controls help panel in Interface GUIs.
 * <p>
 * Renders a small directional arrow from arrows.png:
 * <ul>
 *   <li>Right arrow when the panel is visible - clicking collapses it.</li>
 *   <li>Left arrow when the panel is hidden - clicking expands it.</li>
 * </ul>
 * <p>
 * The texture (arrows.png, 32x32) is laid out as 16x16 sprites:
 * <ul>
 *   <li>x column 0: pointing left (pixels 0-15)</li>
 *   <li>x column 1: pointing right (pixels 16-31)</li>
 *   <li>y row 0: normal state (pixels 0-15)</li>
 *   <li>y row 1: hovered state (pixels 16-31)</li>
 * </ul>
 * Each sprite is rendered at 8x8 by translating to the draw position and applying
 * a 0.5x GL scale, same technique as GuiIOInterface.
 * <p>
 * The visibility state is persisted across sessions via
 * {@link CellsConfig#showControlsHelp} (hidden config category).
 */
public class GuiControlsHelpToggleButton extends GuiButton implements ITooltip {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/info.png");

    // Each sprite in info.png is 16x16 within the 32x32 texture.
    // The button hitbox is 8x8, so we scale the sprite down by 0.5 via the GL matrix.
    private static final int INFO_SIZE = 8;
    private static final int SPRITE_SIZE = 16;

    private final Supplier<String> tooltipSupplier;

    /**
     * Create a controls help toggle button.
     *
     * @param buttonId       Button ID (for event handling in actionPerformed)
     * @param x              X position in screen coordinates
     * @param y              Y position in screen coordinates
     * @param tooltipSupplier Supplier for the tooltip text (may include newlines)
     */
    public GuiControlsHelpToggleButton(int buttonId, int x, int y, Supplier<String> tooltipSupplier) {
        super(buttonId, x, y, INFO_SIZE, INFO_SIZE, "");
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void drawButton(@Nonnull Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        boolean hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        // Right arrow (u=SPRITE_SIZE) when panel is visible (click to collapse);
        // left arrow (u=0) when panel is hidden (click to expand).
        int u = CellsConfig.showControlsHelp ? SPRITE_SIZE : 0;
        int v = hovered ? SPRITE_SIZE : 0;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw the SPRITE_SIZE x SPRITE_SIZE source region from the SPRITE_SIZE*2 x
        // SPRITE_SIZE*2 atlas, scaled down to the INFO_SIZE x INFO_SIZE button hitbox.
        Gui.drawScaledCustomSizeModalRect(
            this.x, this.y,
            (float) u, (float) v,
            SPRITE_SIZE, SPRITE_SIZE,
            INFO_SIZE, INFO_SIZE,
            SPRITE_SIZE * 2, SPRITE_SIZE * 2);
    }

    @Override
    public String getMessage() {
        return this.tooltipSupplier != null ? this.tooltipSupplier.get() : "";
    }

    @Override
    public int xPos() { return this.x; }

    @Override
    public int yPos() { return this.y; }

    @Override
    public int getWidth() { return this.width; }

    @Override
    public int getHeight() { return this.height; }

    @Override
    public boolean isVisible() { return this.visible; }
}
