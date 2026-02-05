package com.cells.core;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import com.cells.ItemRegistry;

/**
 * Creative tab for the C.E.L.L.S. mod.
 */
public final class CellsCreativeTab extends CreativeTabs {

    public static CellsCreativeTab instance = null;

    public CellsCreativeTab() {
        super("cells");
    }

    public static void init() {
        instance = new CellsCreativeTab();
    }

    @Override
    public ItemStack getIcon() {
        return this.createIcon();
    }

    @Override
    public ItemStack createIcon() {
        try {
            if (ItemRegistry.HYPER_DENSITY_COMPONENT != null) {
                return new ItemStack(ItemRegistry.HYPER_DENSITY_COMPONENT, 1, 4);
            }
        } catch (Throwable t) {
            // fall through to fallback
        }

        return new ItemStack(Items.DIAMOND);
    }
}
