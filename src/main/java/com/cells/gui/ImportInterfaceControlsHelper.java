package com.cells.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
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
     * Generate help lines for the Import Interface.
     *
     * @param isFluid Whether this is for the fluid interface (changes some text)
     * @return List of formatted help lines
     */
    public static List<String> getHelpLines(boolean isFluid) {
        List<String> lines = new ArrayList<>();

        lines.add(I18n.format("gui.cells.controls.use_settings"));
        lines.add("");
        lines.add(I18n.format("gui.cells.controls.use_cards"));
        lines.add("");

        // Memory card section
        lines.add(I18n.format("gui.cells.controls.memory_card_sneak"));

        String filterKey = KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.isBound()
            ? KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.getDisplayName()
            : I18n.format("gui.cells.controls.key_not_set");
        // FIXME: re-enable when I get the f*** card working properly
        // lines.add(I18n.format("gui.cells.controls.memory_card_filter", filterKey));

        lines.add("");

        // Quick add keybind
        String quickAddKey = KeyBindings.QUICK_ADD_TO_FILTER.isBound()
            ? KeyBindings.QUICK_ADD_TO_FILTER.getDisplayName()
            : I18n.format("gui.cells.controls.key_not_set");
        lines.add(I18n.format("gui.cells.controls.quick_add", quickAddKey));

        // JEI drag info
        lines.add("");
        lines.add(I18n.format("gui.cells.controls.jei_drag"));

        return lines;
    }

    /**
     * Draw the controls help widget.
     *
     * @param fontRenderer Font renderer to use
     * @param guiLeft Left position of the main GUI
     * @param guiTop Top position of the main GUI
     * @param guiHeight Height of the main GUI
     * @param isFluid Whether this is for the fluid interface
     */
    public static void drawControlsHelpWidget(
            FontRenderer fontRenderer,
            int guiLeft,
            int guiTop,
            int guiHeight,
            boolean isFluid) {

        List<String> lines = getHelpLines(isFluid);
        if (lines.isEmpty()) return;

        // Calculate panel width
        int panelWidth = guiLeft - RIGHT_MARGIN - LEFT_MARGIN;
        if (panelWidth < 60) panelWidth = 60;

        int textWidth = panelWidth - (PADDING * 2);

        // Wrap all lines
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrappedLines.add("");
            } else {
                wrappedLines.addAll(fontRenderer.listFormattedStringToWidth(line, textWidth));
            }
        }

        // Calculate positions (relative to GUI left)
        int panelRight = -RIGHT_MARGIN;
        int panelLeft = -guiLeft + LEFT_MARGIN;
        int contentHeight = wrappedLines.size() * LINE_HEIGHT;
        int panelHeight = contentHeight + (PADDING * 2);

        int panelBottom = guiHeight;
        int panelTop = panelBottom - panelHeight;

        // Draw AE2-style panel background
        Gui.drawRect(panelLeft, panelTop, panelRight, panelBottom, 0xC0000000);

        // Border
        Gui.drawRect(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF606060);
        Gui.drawRect(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF606060);
        Gui.drawRect(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF303030);
        Gui.drawRect(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF303030);

        // Draw text
        int textX = panelLeft + PADDING;
        int textY = panelTop + PADDING;
        for (int i = 0; i < wrappedLines.size(); i++) {
            String line = wrappedLines.get(i);
            if (!line.isEmpty()) {
                fontRenderer.drawString(line, textX, textY + (i * LINE_HEIGHT), 0xCCCCCC);
            }
        }
    }
}
