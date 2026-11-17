package com.cells.gui.scanner.widget;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.util.AEPartLocation;

import com.cells.gui.scanner.ScannerGuiConstants;
import com.cells.scanner.InterfaceScanEntry;


/**
 * Storage header for interfaces' static scan results.
 */
public class ScannerHeader extends AbstractWidget {

    private static final int LINE_WIDTH = 1;
    private static final int TEXT_Y_OFFSET = 1;
    private static final int LOCATION_Y_OFFSET = 9;
    private static final int NAME_RIGHT_PADDING = 4;
    private static final int EXPAND_HIT_PADDING = 2;
    private static final int EXPAND_TEXT_COLOR = 0x606060;
    private static final int CONNECTOR_HEIGHT = 3;
    private static final int ICON_SIZE = 16;

    private final FontRenderer fontRenderer;
    private final RenderItem itemRender;
    private final InterfaceScanEntry entry;
    private final boolean expanded;
    private final Runnable onExpandToggle;
    private final Runnable onClick;

    public ScannerHeader(int y, FontRenderer fontRenderer, RenderItem itemRender,
                          InterfaceScanEntry entry, boolean expanded, Runnable onExpandToggle, Runnable onClick) {
        super(0, y, ScannerGuiConstants.CONTENT_RIGHT_EDGE, ScannerGuiConstants.ROW_HEIGHT);
        this.fontRenderer = fontRenderer;
        this.itemRender = itemRender;
        this.entry = entry;
        this.expanded = expanded;
        this.onExpandToggle = onExpandToggle;
        this.onClick = onClick;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        int indent = ScannerGuiConstants.GUI_INDENT;
        int rightEdge = ScannerGuiConstants.CONTENT_RIGHT_EDGE;

        // Draw horizontal separator line at the top of the header
        Gui.drawRect(indent, this.y - LINE_WIDTH, rightEdge, this.y, ScannerGuiConstants.COLOR_SEPARATOR);

        // Draw location text
        int locationWidth = rightEdge - ScannerGuiConstants.HEADER_NAME_X;
        this.fontRenderer.drawString(trimTextToWidth(this.fontRenderer, getLocation(), locationWidth),
            ScannerGuiConstants.HEADER_NAME_X, this.y + LOCATION_Y_OFFSET, ScannerGuiConstants.COLOR_TEXT_SECONDARY);

        // Draw expand/collapse indicator
        this.fontRenderer.drawString(this.expanded ? "[-]" : "[+]", ScannerGuiConstants.EXPAND_ICON_X,
            this.y + TEXT_Y_OFFSET, EXPAND_TEXT_COLOR);

        if (mouseX >= indent && mouseX < ScannerGuiConstants.EXPAND_ICON_X
            && mouseY >= this.y && mouseY < this.y + this.height) {
            Gui.drawRect(indent, this.y, ScannerGuiConstants.EXPAND_ICON_X, this.y + this.height,
                ScannerGuiConstants.COLOR_STORAGE_HEADER_HOVER);
        }

        // Draw icon
        renderItemStack(this.itemRender, this.entry.icon, indent, this.y);

        // Draw name
        int nameWidth = ScannerGuiConstants.EXPAND_ICON_X - ScannerGuiConstants.HEADER_NAME_X - NAME_RIGHT_PADDING;
        this.fontRenderer.drawString(trimTextToWidth(this.fontRenderer, getName(), nameWidth),
            ScannerGuiConstants.HEADER_NAME_X, this.y + TEXT_Y_OFFSET, ScannerGuiConstants.COLOR_TEXT_NORMAL);

        // Draw tree connector if content follows
        if (this.expanded) {
            Gui.drawRect(ScannerGuiConstants.TREE_LINE_X, this.y + this.height - CONNECTOR_HEIGHT,
                ScannerGuiConstants.TREE_LINE_X + LINE_WIDTH, this.y + this.height,
                ScannerGuiConstants.COLOR_TREE_LINE);
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) return false;
        if (mouseX < ScannerGuiConstants.GUI_INDENT) return false;

        if (mouseX < ScannerGuiConstants.EXPAND_ICON_X - EXPAND_HIT_PADDING) {
            this.onClick.run();
            return true;
        }

        this.onExpandToggle.run();
        return true;
    }

    @Override
    public List<String> getTooltip(int mouseX, int mouseY) {
        String side = this.entry.facing == AEPartLocation.INTERNAL.ordinal() ? ""
            : I18n.format("cells.interface_scanner.facing", AEPartLocation.fromOrdinal(this.entry.facing).name());
        String locate = I18n.format("cells.interface_scanner.locate");
        return side.isEmpty() ? Arrays.asList(getName(), getLocation(), locate)
            : Arrays.asList(getName(), getLocation(), side, locate);
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (!this.visible || !isHovered(mouseX, mouseY)) return ItemStack.EMPTY;

        int iconX = ScannerGuiConstants.GUI_INDENT;
        if (mouseX >= iconX && mouseX < iconX + ICON_SIZE
            && mouseY >= this.y && mouseY < this.y + ICON_SIZE) {
            return this.entry.icon;
        }

        return ItemStack.EMPTY;
    }

    private String getName() {
        String name = this.entry.icon.isEmpty() ? I18n.format("cells.interface_scanner.interface")
            : this.entry.icon.getDisplayName();
        if (this.entry.directionLabel) {
            name = I18n.format("cells.interface_scanner.direction." + (this.entry.export ? "export" : "import"))
                + " " + name;
        } else if (this.entry.typeLabel) {
            name = I18n.format(this.entry.resourceType.getTranslationKey()) + " " + name;
        }

        return name;
    }

    private String getLocation() {
        return I18n.format("cells.interface_scanner.location", this.entry.pos.getX(), this.entry.pos.getY(),
            this.entry.pos.getZ(), this.entry.dimension);
    }
}
