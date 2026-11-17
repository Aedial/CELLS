package com.cells.gui.scanner.widget;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;


/**
 * Base implementation of {@link IWidget} providing common fields and behavior.
 * <p>
 * Subclasses must implement {@link #draw(int, int)} and {@link #handleClick(int, int, int)}.
 * Hover detection is provided by default based on the widget's bounding rectangle.
 * <p>
 * Also provides shared utility methods for common rendering tasks used
 * across the widget hierarchy (line, header, tab, etc.).
 */
public abstract class AbstractWidget implements IWidget {

    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;

    protected AbstractWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (!visible) return false;

        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // ---- Shared utilities ----

    /**
     * Truncate a string to fit within a pixel width, appending "..." if needed.
     * Correctly handles Minecraft formatting codes via fontRenderer.getStringWidth.
     * <p>
     * Shared across both line and header widget hierarchies
     * to avoid duplicating the truncation logic.
     */
    public static String trimTextToWidth(FontRenderer fontRenderer, String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;

        String ellipsis = "...";
        int ellipWidth = fontRenderer.getStringWidth(ellipsis);

        for (int i = text.length() - 1; i > 0; i--) {
            String trimmed = text.substring(0, i);
            if (fontRenderer.getStringWidth(trimmed) + ellipWidth <= maxWidth) {
                return trimmed + ellipsis;
            }
        }

        return ellipsis;
    }

    /**
     * Render an item stack at the given position with standard GUI lighting.
     * Disables depth and lighting and enables blending after rendering.
     * <p>
     * Shared across both line and header widget hierarchies
     * to avoid duplicating the item rendering boilerplate.
     */
    public static void renderItemStack(RenderItem itemRender, ItemStack stack, int renderX, int renderY) {
        if (stack.isEmpty()) return;

        float previousZLevel = itemRender.zLevel;
        itemRender.zLevel = 100.0F;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        itemRender.renderItemIntoGUI(stack, renderX, renderY);
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        itemRender.zLevel = previousZLevel;
        GlStateManager.disableLighting();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
