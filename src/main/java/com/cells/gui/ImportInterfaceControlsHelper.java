package com.cells.gui;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;

import com.cells.client.KeyBindings;


/**
 * Helper class to render controls help widget for Import Interface GUIs.
 * Provides a panel on the left side of the GUI showing available controls and keybinds.
 */
public class ImportInterfaceControlsHelper {

    // Layout constants
    private static final int LEFT_MARGIN = 4;
    private static final int RIGHT_MARGIN = 4;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    private ImportInterfaceControlsHelper() {}

    /**
     * Wrap help lines to fit within {@code textWidth} and truncate to fit within the
     * scaled screen height. Single source of truth shared by getBounds and drawControlsHelpWidget.
     */
    private static List<String> computeWrappedLines(FontRenderer fontRenderer, int textWidth, boolean cardsHelp) {
        List<String> wrappedLines = new ArrayList<>();
        for (String line : getHelpLines(cardsHelp)) {
            if (line.isEmpty()) {
                wrappedLines.add("");
            } else {
                wrappedLines.addAll(fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Clamp to the full scaled window height — the panel lives outside the GUI,
        // so guiHeight would be too restrictive here.
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int maxPanelHeight = sr.getScaledHeight();
        if (maxPanelHeight > PADDING * 2) {
            int maxLines = (maxPanelHeight - (PADDING * 2)) / LINE_HEIGHT;
            if (maxLines < wrappedLines.size()) {
                wrappedLines = new ArrayList<>(wrappedLines.subList(0, Math.max(1, maxLines)));
            }
        }

        return wrappedLines;
    }

    /**
     * Generate help lines for the Import Interface.
     *
     * @param cardsHelp Whether to include help for upgrade cards
     * @return List of formatted help lines
     */
    public static List<String> getHelpLines(boolean cardsHelp) {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("cells.controls.use_settings"));
        lines.add("");

        lines.add(I18n.format("cells.controls.capacity_cards"));
        if (cardsHelp) {
            lines.add(I18n.format("cells.controls.use_cards"));
            lines.add("");
        }

        lines.add(I18n.format("cells.controls.push_pull_cards"));
        lines.add("");

        // Memory card section
        lines.add(I18n.format("cells.controls.memory_card"));
        lines.add(I18n.format("cells.controls.memory_card_sneak"));

        String filterKey = KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.isBound()
            ? KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.getDisplayName()
            : I18n.format("cells.controls.key_not_set");
        lines.add(I18n.format("cells.controls.memory_card_filter", filterKey));

        lines.add(I18n.format("cells.controls.disassemble"));

        lines.add("");

        // Quick add keybind
        String quickAddKey = KeyBindings.QUICK_ADD_TO_FILTER.isBound()
            ? KeyBindings.QUICK_ADD_TO_FILTER.getDisplayName()
            : I18n.format("cells.controls.key_not_set");
        lines.add(I18n.format("cells.controls.quick_add", quickAddKey));

        // JEI drag info
        lines.add("");
        lines.add(I18n.format("cells.controls.jei_drag"));

        return lines;
    }

    /**
     * Get the bounding rectangle of the controls help widget in screen coordinates.
     * Used for JEI exclusion areas so JEI doesn't render items on top of the panel.
     *
     * @param fontRenderer Font renderer (needed for text wrapping calculations)
     * @param guiLeft Left position of the main GUI
     * @param guiTop Top position of the main GUI
     * @param cardsHelp Whether upgrade card help lines are included
     * @return The bounding rectangle in screen coordinates, or a zero-size rectangle if empty
     */
    public static Rectangle getBounds(
            FontRenderer fontRenderer,
            int guiLeft,
            int guiTop,
            boolean cardsHelp) {

        int panelWidth = guiLeft - RIGHT_MARGIN - LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int textWidth = panelWidth - (PADDING * 2);
        List<String> wrappedLines = computeWrappedLines(fontRenderer, textWidth, cardsHelp);
        if (wrappedLines.isEmpty()) return new Rectangle(0, 0, 0, 0);

        int panelHeight = wrappedLines.size() * LINE_HEIGHT + (PADDING * 2);

        // Center against screen height, not guiHeight — the panel lives outside the GUI.
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int screenY = Math.max(0, (sr.getScaledHeight() - panelHeight) / 2);

        return new Rectangle(LEFT_MARGIN, screenY, panelWidth, panelHeight);
    }

    /**
     * Draw the controls help widget.
     *
     * @param fontRenderer Font renderer to use
     * @param guiLeft Left position of the main GUI
     * @param guiTop Top position of the main GUI
     */
    public static void drawControlsHelpWidget(
            FontRenderer fontRenderer,
            int guiLeft,
            int guiTop,
            boolean cardsHelp) {

        // Calculate panel width
        int panelWidth = guiLeft - RIGHT_MARGIN - LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int textWidth = panelWidth - (PADDING * 2);
        List<String> wrappedLines = computeWrappedLines(fontRenderer, textWidth, cardsHelp);
        if (wrappedLines.isEmpty()) return;

        // Calculate positions (relative to GUI left)
        int panelRight = -RIGHT_MARGIN;
        int panelLeft = -guiLeft + LEFT_MARGIN;
        int panelHeight = wrappedLines.size() * LINE_HEIGHT + (PADDING * 2);

        // Center against screen height, not guiHeight — the panel lives outside the GUI.
        // panelTop must be GUI-relative (the GL matrix is translated by guiTop).
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        int screenY = Math.max(0, (sr.getScaledHeight() - panelHeight) / 2);
        int panelTop = screenY - guiTop;
        int panelBottom = panelTop + panelHeight;

        // Draw AE2-style panel background
        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        Gui.drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        Gui.drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw text, skipping lines that would render outside the panel
        int textX = panelLeft + PADDING;
        int textY = panelTop + PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            int lineY = textY + (i * LINE_HEIGHT);
            if (lineY + LINE_HEIGHT > panelBottom - PADDING) break;

            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                fontRenderer.drawString(line, textX, lineY, 0xCCCCCC);
            }
        }
    }
}
