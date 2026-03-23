package com.cells.cells.creative.essentia;

import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.gui.slots.EssentiaFilterSlot;


/**
 * Essentia filter slot for Creative Essentia Cell.
 * <p>
 * Thin wrapper around {@link EssentiaFilterSlot} that gets essentia from filter handler.
 */
@SideOnly(Side.CLIENT)
public class GuiCreativeEssentiaFilterSlot extends EssentiaFilterSlot {

    public GuiCreativeEssentiaFilterSlot(final CreativeEssentiaCellFilterHandler filterHandler,
                                         final int slot, final int x, final int y) {
        super(new EssentiaProvider() {
            @Override
            public thaumicenergistics.api.EssentiaStack getEssentia(int s) {
                return filterHandler.getEssentiaInSlot(s);
            }

            @Override
            public void setEssentia(int s, thaumicenergistics.api.EssentiaStack essentia) {
                filterHandler.setEssentiaInSlot(s, essentia);
            }
        }, slot, x, y);
    }

    @Override
    public boolean isSlotEnabled() {
        return true;
    }

    public ItemStack getDisplayStack() {
        return ItemStack.EMPTY;
    }
}
