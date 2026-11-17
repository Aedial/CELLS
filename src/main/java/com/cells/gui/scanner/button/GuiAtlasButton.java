package com.cells.gui.scanner.button;

import java.util.List;

import com.cells.gui.scanner.ScannerGuiConstants.Texture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;


/**
 * Base class for all buttons that use atlas.png for background rendering.
 * <p>
 * Provides shared logic for:
 * <ul>
 *   <li>Visibility check and early return</li>
 *   <li>Hover detection from mouse coordinates</li>
 *   <li>Binding the atlas texture and setting up blend state</li>
 *   <li>Drawing the selected atlas texture and its hover variant</li>
 * </ul>
 *
 * Subclasses implement:
 * <ul>
 *   <li>{@link #drawForeground(Minecraft)}: any overlay on top of the background</li>
 *   <li>{@link #getTooltip()}: tooltip lines for the button</li>
 * </ul>
 */
public abstract class GuiAtlasButton extends GuiButton {

    private Texture texture;

    protected GuiAtlasButton(int buttonId, int x, int y, Texture texture) {
        super(buttonId, x, y, texture.width, texture.height, "");
        this.texture = texture;
    }

    /**
     * Get the selected background texture.
     */
    protected Texture getTexture() {
        return this.texture;
    }

    /**
     * Set the background texture and button dimensions.
     */
    protected void setTexture(Texture texture) {
        this.texture = texture;
        this.width = texture.width;
        this.height = texture.height;
    }

    protected void drawItem(Minecraft mc, ItemStack item, int xOffset, int yOffset, float scale) {
        RenderItem itemRender = mc.getRenderItem();
        float previousZLevel = itemRender.zLevel;
        itemRender.zLevel = 100.0F;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.x, this.y, 0);
        GlStateManager.scale(scale, scale, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        itemRender.renderItemAndEffectIntoGUI(item, xOffset, yOffset);
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
        itemRender.zLevel = previousZLevel;
    }

    /**
     * Draw any content on top of the atlas background.
     * Called after the background is drawn with the atlas still bound.
     * Default implementation does nothing.
     */
    protected void drawForeground(Minecraft mc) {
        // Override in subclasses for icons, text, state indicators, etc.
    }

    /**
     * Get tooltip lines for this button.
     */
    public abstract List<String> getTooltip();

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        (this.hovered ? this.texture.hovered() : this.texture).draw(this.x, this.y);

        drawForeground(mc);
    }
}
