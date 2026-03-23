package com.cells.cells.creative.gas;

import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import mekanism.api.gas.GasStack;

import com.cells.cells.creative.AbstractCreativeCellItem;
import com.cells.cells.creative.gas.CreativeGasCellFilterHandler;
import com.cells.gui.CellsGuiHandler;


@Optional.Interface(iface = "appeng.api.implementations.items.IItemGroup", modid = "mekeng")
public class ItemCreativeGasCell extends AbstractCreativeCellItem<GasStack, CreativeGasCellFilterHandler> {

    public ItemCreativeGasCell() {
        super("creative_gas_cell", CellsGuiHandler.GUI_CREATIVE_GAS_CELL, "gas",
            cell -> new CreativeGasCellFilterHandler(cell));
    }

    @Override
    protected String formatStackDisplay(mekanism.api.gas.GasStack stack) {
        return "§d" + (stack.getGas() != null ? stack.getGas().getLocalizedName() : "");
    }

    @Override
    protected boolean isFilterEmpty(GasStack filter) {
        return filter == null || filter.amount <= 0;
    }

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return "gui.appliedenergistics2.GasStorage";
    }
}
