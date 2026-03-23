package com.cells.integration.thaumicenergistics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.network.IGuiHandler;

import com.cells.cells.creative.essentia.ContainerCreativeEssentiaCell;
import com.cells.cells.creative.essentia.GuiCreativeEssentiaCell;


/**
 * GUI handler for Creative Essentia Cell.
 * Separated from main CellsGuiHandler to allow conditional loading only when
 * ThaumicEnergistics is present.
 */
public class CreativeEssentiaCellGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // x encodes the EnumHand ordinal
        return new ContainerCreativeEssentiaCell(player.inventory, EnumHand.values()[x]);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // x encodes the EnumHand ordinal
        return new GuiCreativeEssentiaCell(player.inventory, EnumHand.values()[x]);
    }
}
