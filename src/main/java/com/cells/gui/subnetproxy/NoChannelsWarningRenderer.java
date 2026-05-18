package com.cells.gui.subnetproxy;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Renders the "no channels enabled" warning panel for the Subnet Proxy GUI.
 * <p>
 * Hooks into {@link GuiScreenEvent.DrawScreenEvent.Post} so the panel paints
 * AFTER everything else - including the JEI overlay - guaranteeing the warning
 * is never hidden behind ingredient lists or bookmark grids. The panel is
 * placed BELOW the GUI (under the player inventory) so it never covers the
 * filter slot grid the user needs to interact with.
 * <p>
 * Text wraps to fit horizontally when the available screen width is narrow
 * (small GUI scale, ultra-wide-but-short windows, etc.) by using
 * {@link FontRenderer#listFormattedStringToWidth}. The wrap width is capped at
 * the GUI width so the panel never extends past the GUI's horizontal footprint.
 */
@SideOnly(Side.CLIENT)
public final class NoChannelsWarningRenderer {

    /** Padding around the text inside the panel. */
    private static final int PADDING = 6;

    /** Vertical gap between the bottom of the GUI and the top of the panel. */
    private static final int VERTICAL_GAP = 4;

    /** Border thickness in pixels. */
    private static final int BORDER_WIDTH = 1;

    /** Horizontal external padding around the panel. */
    private static final int HORIZONTAL_PADDING = 6;

    /** Text color (warning yellow, opaque). */
    private static final int TEXT_COLOR = 0xFFFFAA00;

    /** Border color (matches OverlayMessageRenderer for visual consistency). */
    private static final int BORDER_COLOR = 0xFF555555;

    /** Background color (mostly opaque to keep the warning readable on busy backgrounds). */
    private static final int BACKGROUND_COLOR = 0xCC000000;

    private NoChannelsWarningRenderer() {}

    /**
     * Forge bus subscriber. Drawn after {@code GuiScreen.drawScreen()} which
     * means it paints on top of the GUI itself AND any JEI overlays.
     */
    @SubscribeEvent
    public static void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiSubnetProxy)) return;

        GuiSubnetProxy gui = (GuiSubnetProxy) event.getGui();
        if (!gui.shouldShowNoChannelsWarning()) return;

        render(gui);
    }

    /**
     * Render the panel under the given Subnet Proxy GUI. Uses screen-space
     * coordinates (not GUI-relative) so we can position relative to the GUI
     * frame regardless of the {@code drawFG}/{@code drawBG} translation.
     */
    private static void render(GuiSubnetProxy gui) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution res = new ScaledResolution(mc);

        int guiTop = gui.getGuiTop();
        // Full screen width is used for centering. HORIZONTAL_PADDING constrains
        // the panel's max width and the clamped position so it never touches the
        // very edge of the window.
        int fullScreenWidth = mc.currentScreen.width;
        int usableWidth = fullScreenWidth - 2 * HORIZONTAL_PADDING;
        int guiHeight = gui.getYSize();

        String line1 = I18n.format("gui.cells.subnet_proxy.no_channels_warning.line1");
        String line2 = I18n.format("gui.cells.subnet_proxy.no_channels_warning.line2");

        // Wrap each line independently so the heading stays on its own line
        // even when the description wraps to several lines. Cap the panel width
        // at the usable area (full screen width minus HORIZONTAL_PADDING on each
        // side) so the panel sits flush with the GUI's horizontal footprint.
        int maxPanelWidth = usableWidth;
        int maxTextWidth = Math.max(40, maxPanelWidth - 2 * (PADDING + BORDER_WIDTH));

        List<String> wrappedLine1 = fr.listFormattedStringToWidth(line1, maxTextWidth);
        List<String> wrappedLine2 = fr.listFormattedStringToWidth(line2, maxTextWidth);

        int textWidth = 0;
        for (String s : wrappedLine1) textWidth = Math.max(textWidth, fr.getStringWidth(s));
        for (String s : wrappedLine2) textWidth = Math.max(textWidth, fr.getStringWidth(s));

        int textHeight = (wrappedLine1.size() + 1 + wrappedLine2.size()) * fr.FONT_HEIGHT;

        int panelWidth = textWidth + 2 * PADDING;
        int panelHeight = textHeight + 2 * PADDING;

        // Center the panel horizontally on the full screen; clamp so it stays
        // inside the HORIZONTAL_PADDING margin on either side.
        int panelLeft = (fullScreenWidth - panelWidth) / 2;
        if (panelLeft < HORIZONTAL_PADDING) panelLeft = HORIZONTAL_PADDING;
        if (panelLeft + panelWidth > fullScreenWidth - HORIZONTAL_PADDING) {
            panelLeft = fullScreenWidth - HORIZONTAL_PADDING - panelWidth;
        }

        int panelTop = guiTop + guiHeight + VERTICAL_GAP;
        // If the GUI is positioned so low on the screen that the panel would
        // overflow off the bottom, fall back to drawing it ABOVE the GUI to
        // keep it visible.
        if (panelTop + panelHeight > res.getScaledHeight() - 2) {
            panelTop = guiTop - VERTICAL_GAP - panelHeight;
        }

        // Reset GL state in case JEI / other overlays left things in a non-default state.
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // Border (drawn slightly larger than background)
        Gui.drawRect(
            panelLeft - BORDER_WIDTH, panelTop - BORDER_WIDTH,
            panelLeft + panelWidth + BORDER_WIDTH, panelTop + panelHeight + BORDER_WIDTH,
            BORDER_COLOR);
        Gui.drawRect(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, BACKGROUND_COLOR);

        // Draw text. Each wrapped line is centered horizontally inside the panel.
        int y = panelTop + PADDING;
        for (String s : wrappedLine1) {
            int x = panelLeft + (panelWidth - fr.getStringWidth(s)) / 2;
            fr.drawStringWithShadow(s, x, y, TEXT_COLOR);
            y += fr.FONT_HEIGHT;
        }

        // Visual separator between the heading and the description block.
        y += fr.FONT_HEIGHT;

        for (String s : wrappedLine2) {
            int x = panelLeft + (panelWidth - fr.getStringWidth(s)) / 2;
            fr.drawStringWithShadow(s, x, y, TEXT_COLOR);
            y += fr.FONT_HEIGHT;
        }

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
