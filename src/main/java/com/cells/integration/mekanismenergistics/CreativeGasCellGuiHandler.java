package com.cells.integration.mekanismenergistics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.network.IGuiHandler;

import com.cells.cells.creative.gas.ContainerCreativeGasCell;
import com.cells.cells.creative.gas.GuiCreativeGasCell;


/**
 * GUI handler for Creative Gas Cell.
 * Separated from main CellsGuiHandler to allow conditional loading only when
 * MekanismEnergistics is present.
 */
public class CreativeGasCellGuiHandler implements IGuiHandler {

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // x encodes the EnumHand ordinal
        return new ContainerCreativeGasCell(player.inventory, EnumHand.values()[x]);
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // x encodes the EnumHand ordinal
        return new GuiCreativeGasCell(player.inventory, EnumHand.values()[x]);
    }
}
