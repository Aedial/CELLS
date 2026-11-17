package com.cells.gui.scanner;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;

import com.cells.config.CellsConfig;
import com.cells.gui.scanner.button.GuiAtlasButton;
import com.cells.gui.scanner.button.BinaryButton;
import com.cells.gui.scanner.button.GuiScannerFilterButton;
import com.cells.gui.scanner.button.GuiScannerSubnetButton;
import com.cells.gui.scanner.widget.IWidget;
import com.cells.gui.scanner.widget.ScannerHeader;
import com.cells.gui.scanner.widget.ScannerSlotsLine;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketInterfaceScannerSubnets;
import com.cells.network.sync.ResourceType;
import com.cells.scanner.ContainerInterfaceScanner;
import com.cells.scanner.InterfaceScanEntry;
import com.cells.client.ScannerHighlightRenderer;


/**
 * DiskTerminal storage-bus view adapted to static interface buffer results.
 */
public class GuiInterfaceScanner extends AEBaseGui {

    private static final class ScanRow {

        private final InterfaceScanEntry entry;
        private final int startIndex;

        private ScanRow(InterfaceScanEntry entry, int startIndex) {
            this.entry = entry;
            this.startIndex = startIndex;
        }
    }

    private static final long DOUBLE_CLICK_THRESHOLD = 400L;
    private static final int HEADER_HEIGHT = ScannerGuiConstants.CONTENT_START_Y;
    private static final int FOOTER_HEIGHT = 8;
    private static final int SCREEN_PADDING = 12;
    private static final int MIN_ROWS = 6;
    private static final int SCROLLBAR_X = 189;
    private static final int SCROLLBAR_HEIGHT_INSET = 2;
    private static final int BUTTON_SPACING = 18;
    private static final int BUTTON_TOP = 8;
    private static final int ROW_TEXTURE_Y = 52;
    private static final int FOOTER_TEXTURE_Y = 158;
    private static final int FOOTER_EDGE_TEXTURE_Y = 253;
    private static final int FOOTER_EDGE_TEXTURE_WIDTH = 190;
    private static final int FOOTER_EDGE_HEIGHT = 3;
    private static final int BORDER_LEFT = 21;
    private static final int BORDER_RIGHT = 182;
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int TITLE_Y = 6;
    private static final int HEADER_CONNECTOR_OFFSET = 15;
    private static final int CONTENT_CONNECTOR_OFFSET = 9;
    private static final int LINE_WIDTH = 1;
    private static final int MESSAGE_WIDTH = 140;
    private static final int MESSAGE_Y_OFFSET = 5;
    private static final String BACKGROUND_TEXTURE = "guis/newinterfaceterminal.png";

    private final ContainerInterfaceScanner container;
    private final List<GuiScannerFilterButton> filters = new ArrayList<>();
    private final Set<String> collapsed = new HashSet<>();
    private final List<ScanRow> rows = new ArrayList<>();
    private final List<IWidget> visibleRows = new ArrayList<>();
    private GuiImgButton terminalStyleButton;
    private GuiScannerSubnetButton subnetButton;
    private int rowsVisible;
    private int contentVersion = -1;
    private String lastClick;
    private long lastClickTime;

    public GuiInterfaceScanner(ContainerInterfaceScanner container) {
        super(container);
        this.container = container;
        this.xSize = ScannerGuiConstants.GUI_WIDTH;
        setScrollBar(new GuiScrollbar());
    }

    @Override
    public void initGui() {
        int availableHeight = this.height - HEADER_HEIGHT - FOOTER_HEIGHT - SCREEN_PADDING * 2;
        this.rowsVisible = CellsConfig.hidden.interfaceScannerTall
            ? Math.max(MIN_ROWS, availableHeight / ScannerGuiConstants.ROW_HEIGHT) : MIN_ROWS;
        int rowsHeight = this.rowsVisible * ScannerGuiConstants.ROW_HEIGHT;
        this.ySize = HEADER_HEIGHT + rowsHeight + FOOTER_HEIGHT;
        super.initGui();

        this.getScrollBar().setTop(HEADER_HEIGHT).setLeft(SCROLLBAR_X)
            .setHeight(rowsHeight - SCROLLBAR_HEIGHT_INSET);
        this.buttonList.clear();
        int buttonX = this.guiLeft - BUTTON_SPACING;
        int smallModeHeight = HEADER_HEIGHT + MIN_ROWS * ScannerGuiConstants.ROW_HEIGHT + FOOTER_HEIGHT;
        int buttonY = (this.height - smallModeHeight) / 2 + BUTTON_TOP;

        this.terminalStyleButton = new GuiImgButton(buttonX, buttonY, Settings.TERMINAL_STYLE,
            CellsConfig.hidden.interfaceScannerTall ? TerminalStyle.TALL : TerminalStyle.SMALL);
        this.buttonList.add(this.terminalStyleButton);

        buttonY += BUTTON_SPACING;
        this.subnetButton = new GuiScannerSubnetButton(1, buttonX, buttonY, this.container.isIncludeSubnets());
        this.subnetButton.enabled = this.container.canScan();
        this.buttonList.add(this.subnetButton);

        if (this.filters.isEmpty()) {
            ResourceType[] types = {ResourceType.ITEM, ResourceType.FLUID, ResourceType.ESSENTIA, ResourceType.GAS};
            for (ResourceType type : types) {
                if (!type.isAvailable()) continue;

                this.filters.add(new GuiScannerFilterButton(this.filters.size() + 2, 0, 0, type));
            }
        }

        for (GuiScannerFilterButton filter : this.filters) {
            buttonY += BUTTON_SPACING;
            filter.x = buttonX;
            filter.y = buttonY;
            this.buttonList.add(filter);
        }

        rebuildRows();
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();
        for (GuiButton button : this.buttonList) {
            if (!button.visible || button.x >= this.guiLeft) continue;

            areas.add(new Rectangle(button.x, button.y, button.width, button.height));
        }

        return areas;
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        this.bindTexture(BACKGROUND_TEXTURE);

        // Draw top section
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, HEADER_HEIGHT);

        // Draw middle section (repeated rows)
        for (int index = 0; index < this.rowsVisible; index++) {
            this.drawTexturedModalRect(
                offsetX, offsetY + HEADER_HEIGHT + index * ScannerGuiConstants.ROW_HEIGHT,
                0, ROW_TEXTURE_Y, this.xSize, ScannerGuiConstants.ROW_HEIGHT);
        }

        // Draw upper border for the main area (as the texture has a gap)
        drawRect(offsetX + BORDER_LEFT, offsetY + HEADER_HEIGHT - LINE_WIDTH,
            offsetX + BORDER_RIGHT, offsetY + HEADER_HEIGHT, BORDER_COLOR);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Draw the bottom border from the terminal footer
        int bottomY = offsetY + HEADER_HEIGHT + this.rowsVisible * ScannerGuiConstants.ROW_HEIGHT;
        int footerHeight = FOOTER_HEIGHT - FOOTER_EDGE_HEIGHT;
        this.drawTexturedModalRect(offsetX, bottomY, 0, FOOTER_TEXTURE_Y, this.xSize, footerHeight);

        // Extend the flat footer center to include the scrollbar
        int leftWidth = this.xSize / 2;
        int rightWidth = this.xSize - leftWidth;
        int edgeY = bottomY + footerHeight;
        this.drawTexturedModalRect(offsetX, edgeY, 0, FOOTER_EDGE_TEXTURE_Y, leftWidth, FOOTER_EDGE_HEIGHT);
        this.drawTexturedModalRect(offsetX + leftWidth, edgeY,
            FOOTER_EDGE_TEXTURE_WIDTH - rightWidth, FOOTER_EDGE_TEXTURE_Y, rightWidth, FOOTER_EDGE_HEIGHT);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.contentVersion != this.container.getContentVersion()) {
            this.contentVersion = this.container.getContentVersion();
            this.subnetButton.set(this.container.isIncludeSubnets());
            this.subnetButton.enabled = this.container.canScan();
            rebuildRows();
        }

        this.fontRenderer.drawString(I18n.format("cells.interface_scanner.title"),
            ScannerGuiConstants.GUI_INDENT, TITLE_Y, ScannerGuiConstants.COLOR_TEXT_NORMAL);

        buildVisibleRows();
        for (IWidget widget : this.visibleRows) widget.draw(mouseX - offsetX, mouseY - offsetY);

        int lastIndex = this.getScrollBar().getCurrentScroll() + this.visibleRows.size() - 1;
        if (!this.visibleRows.isEmpty() && lastIndex + 1 < this.rows.size()
                && this.rows.get(lastIndex + 1).startIndex >= 0) {
            IWidget last = this.visibleRows.get(this.visibleRows.size() - 1);
            int cutY = last.getY() + (last instanceof ScannerHeader
                ? HEADER_CONNECTOR_OFFSET : CONTENT_CONNECTOR_OFFSET);
            drawRect(ScannerGuiConstants.TREE_LINE_X, cutY, ScannerGuiConstants.TREE_LINE_X + LINE_WIDTH,
                HEADER_HEIGHT + this.rowsVisible * ScannerGuiConstants.ROW_HEIGHT,
                ScannerGuiConstants.COLOR_TREE_LINE);
        }

        if (this.rows.isEmpty()) {
            String key = this.container.hasFailed() ? "no_network"
                : this.container.isComplete() ? "empty" : "scanning";
            String message = I18n.format("cells.interface_scanner." + key);
            int centerX = (ScannerGuiConstants.GUI_INDENT + ScannerGuiConstants.CONTENT_RIGHT_EDGE) / 2;
            this.fontRenderer.drawSplitString(message, centerX - MESSAGE_WIDTH / 2,
                HEADER_HEIGHT + this.rowsVisible * ScannerGuiConstants.ROW_HEIGHT / 2 - MESSAGE_Y_OFFSET,
                MESSAGE_WIDTH, ScannerGuiConstants.COLOR_TEXT_SECONDARY);
        }
    }

    private void rebuildRows() {
        this.rows.clear();
        List<InterfaceScanEntry> entries = new ArrayList<>(this.container.getEntries());
        entries.sort(Comparator.comparingInt((InterfaceScanEntry entry) -> entry.dimension)
            .thenComparingInt(entry -> entry.pos.getX())
            .thenComparingInt(entry -> entry.pos.getY())
            .thenComparingInt(entry -> entry.pos.getZ())
            .thenComparingInt(entry -> entry.facing)
            .thenComparingInt(entry -> entry.resourceType.ordinal())
            .thenComparing(entry -> entry.export));

        for (InterfaceScanEntry entry : entries) {
            boolean shown = true;
            for (GuiScannerFilterButton filter : this.filters) {
                if (filter.type == entry.resourceType) shown = filter.get();
            }

            if (!shown) continue;

            this.rows.add(new ScanRow(entry, -1));
            if (this.collapsed.contains(entry.getKey())) continue;

            for (int start = 0; start < entry.missing.size(); start += ScannerGuiConstants.SLOTS_PER_ROW) {
                this.rows.add(new ScanRow(entry, start));
            }
        }

        this.getScrollBar().setRange(0, Math.max(0, this.rows.size() - this.rowsVisible), 1);
    }

    private void buildVisibleRows() {
        this.visibleRows.clear();
        int scroll = this.getScrollBar().getCurrentScroll();
        int end = Math.min(scroll + this.rowsVisible, this.rows.size());
        int cutY = ScannerGuiConstants.CONTENT_START_Y;

        for (int index = scroll; index < end; index++) {
            ScanRow row = this.rows.get(index);
            int y = ScannerGuiConstants.CONTENT_START_Y + (index - scroll) * ScannerGuiConstants.ROW_HEIGHT;
            if (row.startIndex < 0) {
                this.visibleRows.add(new ScannerHeader(y, this.fontRenderer, this.itemRender, row.entry,
                    !this.collapsed.contains(row.entry.getKey()), () -> toggleGroup(row.entry.getKey()),
                    () -> locate(row.entry)));
                cutY = y + HEADER_CONNECTOR_OFFSET;
            } else {
                this.visibleRows.add(new ScannerSlotsLine(y, this.itemRender,
                    row.entry, row.startIndex, cutY));
                cutY = y + CONTENT_CONNECTOR_OFFSET;
            }
        }
    }

    /**
     * Resolve the displayed resource icon from screen coordinates for JEI lookups.
     */
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        if (this.contentVersion != this.container.getContentVersion()) rebuildRows();
        buildVisibleRows();

        int relativeX = mouseX - this.guiLeft;
        int relativeY = mouseY - this.guiTop;
        for (IWidget widget : this.visibleRows) {
            ItemStack stack = widget.getHoveredItemStack(relativeX, relativeY);
            if (!stack.isEmpty()) return stack;
        }

        return ItemStack.EMPTY;
    }

    private void toggleGroup(String key) {
        if (!this.collapsed.remove(key)) this.collapsed.add(key);
        rebuildRows();
    }

    private void locate(InterfaceScanEntry entry) {
        long now = System.currentTimeMillis();
        String key = entry.getKey();
        if (key.equals(this.lastClick) && now - this.lastClickTime < DOUBLE_CLICK_THRESHOLD) {
            this.lastClick = null;
            this.lastClickTime = 0;
            ScannerHighlightRenderer.highlight(entry.pos, entry.dimension);
            return;
        }

        this.lastClick = key;
        this.lastClickTime = now;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == this.terminalStyleButton) {
            CellsConfig.setInterfaceScannerTall(!CellsConfig.hidden.interfaceScannerTall);
            initGui();
        } else if (button == this.subnetButton) {
            boolean includeSubnets = !this.subnetButton.get();
            this.subnetButton.set(includeSubnets);
            CellsNetworkHandler.INSTANCE.sendToServer(
                new PacketInterfaceScannerSubnets(this.container.windowId, includeSubnets));
        } else if (button instanceof GuiScannerFilterButton) {
            BinaryButton filter = (BinaryButton) button;
            filter.set(!filter.get());
            rebuildRows();
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        buildVisibleRows();
        for (IWidget widget : this.visibleRows) {
            if (widget.handleClick(mouseX - this.guiLeft, mouseY - this.guiTop, mouseButton)) return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        for (GuiButton button : this.buttonList) {
            if (button instanceof GuiAtlasButton && button.isMouseOver()) {
                drawHoveringText(((GuiAtlasButton) button).getTooltip(), mouseX, mouseY);
                return;
            }
        }

        int relativeX = mouseX - this.guiLeft;
        int relativeY = mouseY - this.guiTop;
        for (IWidget widget : this.visibleRows) {
            if (!widget.isHovered(relativeX, relativeY)) continue;

            ItemStack stack = widget.getHoveredItemStack(relativeX, relativeY);
            if (!stack.isEmpty()) {
                renderToolTip(stack, mouseX, mouseY);
            } else {
                List<String> tooltip = widget.getTooltip(relativeX, relativeY);
                if (!tooltip.isEmpty()) drawHoveringText(tooltip, mouseX, mouseY);
            }

            return;
        }
    }
}
