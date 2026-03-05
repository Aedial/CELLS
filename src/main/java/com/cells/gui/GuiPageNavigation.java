package com.cells.gui;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;


/**
 * Page navigation control for paginated GUIs.
 * Renders as: "< page >" where page is the current page number (1-indexed).
 * The arrows are clickable buttons; clicking near the left arrow goes back,
 * clicking near the right arrow goes forward.
 * <p>
 * Uses a custom texture for the arrows with hover states.
 */
public class GuiPageNavigation extends GuiButton implements ITooltip {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/page_nav.png");

    // Texture layout: 32x32, left half = left arrow (normal/hover), right half = right arrow (normal/hover)
    // Each arrow is rendered to a 8x8 area

    private static final int ARROW_TEXTURE_SIZE = 16;
    private static final int ARROW_SIZE = 8;
    private static final int TOTAL_WIDTH = 26;
    private static final int TOTAL_HEIGHT = 10;

    private final IntSupplier currentPageSupplier;
    private final IntSupplier totalPagesSupplier;
    private final Runnable onPrevPage;
    private final Runnable onNextPage;

    /**
     * State tracking for which arrow is being hovered/clicked.
     * -1 = left arrow, 0 = none, 1 = right arrow
     */
    private int hoveredArrow = 0;

    /**
     * Create page navigation control.
     *
     * @param buttonId Button ID for event handling
     * @param x X position (screen coordinates)
     * @param y Y position (screen coordinates)
     * @param currentPageSupplier Supplier for the current page (0-indexed)
     * @param totalPagesSupplier Supplier for the total number of pages
     * @param onPrevPage Callback when left arrow is clicked
     * @param onNextPage Callback when right arrow is clicked
     */
    public GuiPageNavigation(int buttonId, int x, int y,
                             IntSupplier currentPageSupplier, IntSupplier totalPagesSupplier,
                             Runnable onPrevPage, Runnable onNextPage) {
        super(buttonId, x, y, TOTAL_WIDTH, TOTAL_HEIGHT, "");
        this.currentPageSupplier = currentPageSupplier;
        this.totalPagesSupplier = totalPagesSupplier;
        this.onPrevPage = onPrevPage;
        this.onNextPage = onNextPage;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        int totalPages = this.totalPagesSupplier.getAsInt();

        FontRenderer fontRenderer = mc.fontRenderer;
        int currentPage = this.currentPageSupplier.getAsInt();

        // Determine hover state
        this.hoveredArrow = 0;
        if (mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height) {
            int relX = mouseX - this.x;
            if (relX < ARROW_SIZE + 1) {
                this.hoveredArrow = -1; // Left arrow
            } else if (relX >= this.width - ARROW_SIZE - 1) {
                this.hoveredArrow = 1; // Right arrow
            }
        }

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();

        // Draw left arrow (disabled if on first page)
        boolean leftEnabled = currentPage > 0;
        boolean leftHovered = this.hoveredArrow == -1 && leftEnabled;
        int leftTexY = leftHovered ? ARROW_SIZE : 0;
        float leftAlpha = leftEnabled ? 1.0F : 0.3F;
        GlStateManager.color(1.0F, 1.0F, 1.0F, leftAlpha);
        Gui.drawModalRectWithCustomSizedTexture(
            this.x, this.y + 1, 0f, (float) leftTexY,
            ARROW_SIZE, ARROW_SIZE, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE);

        // Draw right arrow (disabled if on last page)
        boolean rightEnabled = currentPage < totalPages - 1;
        boolean rightHovered = this.hoveredArrow == 1 && rightEnabled;
        int rightTexY = rightHovered ? ARROW_SIZE : 0;
        float rightAlpha = rightEnabled ? 1.0F : 0.3F;
        GlStateManager.color(1.0F, 1.0F, 1.0F, rightAlpha);
        Gui.drawModalRectWithCustomSizedTexture(
            this.x + this.width - ARROW_SIZE, this.y + 1, (float) ARROW_SIZE, (float) rightTexY,
            ARROW_SIZE, ARROW_SIZE, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();

        // Draw page number in the center (1-indexed for display)
        String pageText = String.valueOf(currentPage + 1);
        int textWidth = fontRenderer.getStringWidth(pageText);
        int textX = this.x + (this.width - textWidth) / 2;
        int textY = this.y + (this.height - fontRenderer.FONT_HEIGHT) / 2 + 1;
        fontRenderer.drawString(pageText, textX, textY, 0x404040);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        int totalPages = this.totalPagesSupplier.getAsInt();
        if (!this.visible) return false;

        if (!super.mousePressed(mc, mouseX, mouseY)) return false;

        int currentPage = this.currentPageSupplier.getAsInt();
        int relX = mouseX - this.x;

        // Left arrow click
        if (relX < ARROW_SIZE + 1 && currentPage > 0) {
            if (this.onPrevPage != null) this.onPrevPage.run();
            return true;
        }

        // Right arrow click
        if (relX >= this.width - ARROW_SIZE - 1 && currentPage < totalPages - 1) {
            if (this.onNextPage != null) this.onNextPage.run();
            return true;
        }

        return false;
    }

    // ITooltip implementation

    @Override
    public String getMessage() {
        int currentPage = this.currentPageSupplier.getAsInt();
        int totalPages = this.totalPagesSupplier.getAsInt();
        if (hoveredArrow == -1 && currentPage > 0) {
            return I18n.format("tooltip.cells.previous_page");
        } else if (hoveredArrow == 1 && currentPage < totalPages - 1) {
            return I18n.format("tooltip.cells.next_page");
        }

        return null;
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
