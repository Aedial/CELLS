package com.cells.cells.creative.essentia;

import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.AbstractCreativeCellItem;
import com.cells.gui.CellsGuiHandler;


@Optional.Interface(iface = "appeng.api.implementations.items.IItemGroup", modid = "thaumicenergistics")
public class ItemCreativeEssentiaCell extends AbstractCreativeCellItem<EssentiaStack, CreativeEssentiaCellFilterHandler> {

    public ItemCreativeEssentiaCell() {
        super("creative_essentia_cell", CellsGuiHandler.GUI_CREATIVE_ESSENTIA_CELL, "essentia", CreativeEssentiaCellFilterHandler::new);
    }

    @Override
    protected String formatStackDisplay(EssentiaStack stack) {
        return "§5" + (stack != null && stack.getAspect() != null ? stack.getAspect().getName() : "");
    }

    @Override
    protected boolean isFilterEmpty(EssentiaStack filter) {
        return filter == null;
    }

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return "gui.thaumicenergistics.EssentiaStorage";
    }
}
