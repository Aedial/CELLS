package com.cells.integration.nae2.pmt;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import net.minecraft.client.util.ITooltipFlag;


/**
 * Renderer for the Pattern Multi-Tool panel beside the Pattern Card inventory.
 * <p>
 * Features:
 * - Draws the PMT panel background texture
 * - Draws additional columns based on installed capacity upgrades
 * - Draws pattern slot backgrounds (pattern icon for empty slots)
 * - Draws output count overlay in bottom-right corner (AE2 handles item rendering)
 * - Draws pattern tooltips
 */
@SideOnly(Side.CLIENT)
public class PMTRenderer {

    // Panel dimensions
    public static final int PMT_WIDTH = 86;
    public static final int PMT_HEIGHT = 198;

    // Slot layout
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_MARGIN = 8;  // Margin from panel edge to first slot
    private static final int ROWS = 9;
    private static final int ICON_SIZE = 16;
    private static final int COLUMN_TEXTURE_X = 8;
    private static final int COLUMN_TEXTURE_Y = 8;

    // Textures
    // NAE2's PMT panel texture
    private static final ResourceLocation PMT_TEXTURE = new ResourceLocation(
            "nae2", "textures/gui/pattern_multiplier_toolbox.png");
    // AE2's states.png for pattern icon
    private static final ResourceLocation AE2_STATES = new ResourceLocation(
            "appliedenergistics2", "textures/guis/states.png");

    // Pattern icon position in states.png: BACKGROUND_PATTERN = row 9, col 16
    private static final int PATTERN_ICON_U = 15 * ICON_SIZE;
    private static final int PATTERN_ICON_V = 8 * ICON_SIZE;

    // Opacity for empty slot icons (matches AE2 style)
    private static final float SLOT_ICON_OPACITY = 0.4f;

    private PMTRenderer() {}

    /**
     * Draws the PMT panel background.
     *
     * @param gui The parent GUI
     * @param pmtManager The PMT manager (null if PMT not available)
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     * @param offsetX X offset for PMT panel relative to guiLeft
     * @param offsetY Y offset for PMT panel relative to guiTop
     */
    public static void drawBackground(GuiContainer gui, PMTManager pmtManager,
                                      int guiLeft, int guiTop, int offsetX, int offsetY) {
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        gui.mc.getTextureManager().bindTexture(PMT_TEXTURE);

        int drawX = guiLeft + offsetX;
        int drawY = guiTop + offsetY;

        // Draw main panel background
        gui.drawTexturedModalRect(drawX, drawY, 0, 0, PMT_WIDTH, PMT_HEIGHT);

        // Draw additional column backgrounds based on capacity upgrades
        int columns = pmtManager.getInstalledCapacityUpgrades();
        for (int i = 1; i <= columns; i++) {
            gui.drawTexturedModalRect(
                    drawX + SLOT_MARGIN + (i * SLOT_SIZE),
                    drawY + SLOT_MARGIN,
                    COLUMN_TEXTURE_X, COLUMN_TEXTURE_Y, ICON_SIZE, SLOT_SIZE * ROWS);
        }
    }

    /**
     * Draws pattern icons for empty PMT slots.
     *
     * @param gui The parent GUI
     * @param pmtManager The PMT manager
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     */
    public static void drawSlotBackgrounds(GuiContainer gui, PMTManager pmtManager, int guiLeft, int guiTop) {
        if (pmtManager == null || !pmtManager.hasPMT()) return;

        // Draw pattern icons for empty enabled slots
        gui.mc.getTextureManager().bindTexture(AE2_STATES);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, SLOT_ICON_OPACITY);

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (!(slot instanceof PMTSlot)) continue;

            PMTSlot pmtSlot = (PMTSlot) slot;

            // Only draw background icon for enabled, empty slots
            if (!pmtSlot.isSlotEnabled()) continue;

            ItemStack stack = pmtSlot.getItemHandler().getStackInSlot(pmtSlot.getSlotIndex());
            if (!stack.isEmpty()) continue;

            int x = guiLeft + slot.xPos;
            int y = guiTop + slot.yPos;
            gui.drawTexturedModalRect(x, y, PATTERN_ICON_U, PATTERN_ICON_V, ICON_SIZE, ICON_SIZE);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
    }

    /**
     * Draws the tooltip for a hovered PMT slot.
     * Shows the full pattern details (input -> output).
     *
     * @param gui The parent GUI
     * @param slot The hovered PMT slot
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     */
    public static void drawSlotTooltip(GuiContainer gui, PMTSlot slot, int mouseX, int mouseY,
                                       int screenWidth, int screenHeight) {
        if (!slot.getHasStack()) return;

        // Get the actual pattern stack for the tooltip (not the display stack)
        ItemStack patternStack = slot.getStack();
        if (patternStack.isEmpty()) return;

        List<String> tooltip = patternStack.getTooltip(
                gui.mc.player,
                gui.mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL);

        GuiUtils.drawHoveringText(tooltip, mouseX, mouseY, screenWidth, screenHeight, -1, gui.mc.fontRenderer);
    }

    /**
     * Checks if the mouse is inside the PMT panel area.
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param guiLeft Left position of main GUI
     * @param guiTop Top position of main GUI
     * @param offsetX X offset for PMT panel
     * @param offsetY Y offset for PMT panel
     * @return true if mouse is inside PMT panel
     */
    public static boolean isMouseInsidePanel(int mouseX, int mouseY, int guiLeft, int guiTop,
                                             int offsetX, int offsetY) {
        int panelX = guiLeft + offsetX;
        int panelY = guiTop + offsetY;
        return mouseX >= panelX && mouseX < panelX + PMT_WIDTH &&
               mouseY >= panelY && mouseY < panelY + PMT_HEIGHT;
    }
}
