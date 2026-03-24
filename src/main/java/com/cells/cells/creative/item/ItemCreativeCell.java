package com.cells.cells.creative.item;

import java.util.Set;

import net.minecraft.item.ItemStack;

import appeng.core.localization.GuiText;

import com.cells.cells.creative.AbstractCreativeCellItem;
import com.cells.cells.creative.item.CreativeCellFilterHandler;
import com.cells.gui.CellsGuiHandler;


public class ItemCreativeCell extends AbstractCreativeCellItem<ItemStack, CreativeCellFilterHandler> {

    public ItemCreativeCell() {
        super("creative_cell", CellsGuiHandler.GUI_CREATIVE_CELL, "item", CreativeCellFilterHandler::new);
    }

    @Override
    protected String formatStackDisplay(ItemStack stack) {
        return "§f" + stack.getDisplayName();
    }

    @Override
    protected boolean isFilterEmpty(ItemStack filter) {
        return filter == null || filter.isEmpty();
    }
}
