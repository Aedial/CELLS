package com.cells.gui.scanner.widget;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import com.cells.gui.scanner.ScannerGuiConstants;
import com.cells.gui.scanner.ScannerGuiConstants.Texture;
import com.cells.scanner.InterfaceScanEntry;


/**
 * DiskTerminal content slots and tree connectors for empty filtered resources.
 */
public class ScannerSlotsLine extends AbstractWidget {

    private static final int LINE_WIDTH = 1;
    private static final int BRANCH_Y_OFFSET = 8;
    private static final int BRANCH_WIDTH = 10;
    private static final int SLOT_HOVER_INSET = 1;

    private final RenderItem itemRender;
    private final InterfaceScanEntry entry;
    private final int startIndex;
    private final int lineAboveCutY;

    public ScannerSlotsLine(int y, RenderItem itemRender, InterfaceScanEntry entry,
            int startIndex, int lineAboveCutY) {
        super(0, y, ScannerGuiConstants.CONTENT_RIGHT_EDGE, ScannerGuiConstants.ROW_HEIGHT);
        this.itemRender = itemRender;
        this.entry = entry;
        this.startIndex = startIndex;
        this.lineAboveCutY = lineAboveCutY;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        int treeX = ScannerGuiConstants.TREE_LINE_X;
        int branchY = this.y + BRANCH_Y_OFFSET;

        // Vertical line from the row above's cut point down to our junction
        if (this.lineAboveCutY < branchY) {
            Gui.drawRect(treeX, this.lineAboveCutY, treeX + LINE_WIDTH, branchY, ScannerGuiConstants.COLOR_TREE_LINE);
        }

        // Horizontal branch
        Gui.drawRect(treeX, branchY, treeX + BRANCH_WIDTH, branchY + LINE_WIDTH, ScannerGuiConstants.COLOR_TREE_LINE);

        int size = Texture.MINI_SLOT.width;
        for (int slot = 0; slot < ScannerGuiConstants.SLOTS_PER_ROW; slot++) {
            int slotX = ScannerGuiConstants.SLOTS_X + slot * size;
            Texture.MINI_SLOT.draw(slotX, this.y);
        }

        int end = Math.min(this.startIndex + ScannerGuiConstants.SLOTS_PER_ROW, this.entry.missing.size());
        for (int index = this.startIndex; index < end; index++) {
            int slotX = ScannerGuiConstants.SLOTS_X + (index - this.startIndex) * size;
            renderItemStack(this.itemRender, this.entry.missing.get(index), slotX, this.y);

            if (mouseX >= slotX && mouseX < slotX + size && mouseY >= this.y && mouseY < this.y + size) {
                Gui.drawRect(slotX + SLOT_HOVER_INSET, this.y + SLOT_HOVER_INSET,
                    slotX + size - SLOT_HOVER_INSET, this.y + size - SLOT_HOVER_INSET,
                    ScannerGuiConstants.COLOR_HOVER_HIGHLIGHT);
            }
        }
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        return false;
    }

    @Override
    public ItemStack getHoveredItemStack(int mouseX, int mouseY) {
        int size = Texture.MINI_SLOT.width;
        if (mouseY < this.y || mouseY >= this.y + size || mouseX < ScannerGuiConstants.SLOTS_X) {
            return ItemStack.EMPTY;
        }

        int column = (mouseX - ScannerGuiConstants.SLOTS_X) / size;
        int index = this.startIndex + column;
        if (column >= ScannerGuiConstants.SLOTS_PER_ROW || index >= this.entry.missing.size()) {
            return ItemStack.EMPTY;
        }

        return this.entry.missing.get(index);
    }
}
