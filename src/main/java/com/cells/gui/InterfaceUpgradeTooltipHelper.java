package com.cells.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;


public final class InterfaceUpgradeTooltipHelper {

    private static final int TEXT_WIDTH = 300;

    private InterfaceUpgradeTooltipHelper() {}

    public static List<String> getTooltip(FontRenderer font, boolean isExport, boolean isPart) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("tooltip.cells.interface.upgrades.title"));

        addCard(lines, font, "capacity", AbstractResourceInterfaceLogic.MAX_CAPACITY_CARDS,
            AbstractResourceInterfaceLogic.SLOTS_PER_PAGE);

        String transfer = isExport ? "push" : "pull";
        String sides = isPart ? "part" : "block";
        addCard(lines, font, transfer + "." + sides, 1);

        if (isExport) addCard(lines, font, "stocking_threshold", 1);

        if (!isExport) {
            addCard(lines, font, "overflow", 1);
            addCard(lines, font, "trash", 1);
        }

        addCard(lines, font, "redstone", 1);
        addCard(lines, font, "inverter", 1);
        return lines;
    }

    private static void addCard(List<String> lines, FontRenderer font, String card, Object... arguments) {
        String text = I18n.format("tooltip.cells.interface.upgrades." + card, arguments);
        lines.addAll(font.listFormattedStringToWidth(TextFormatting.GRAY + text, TEXT_WIDTH));
    }
}
