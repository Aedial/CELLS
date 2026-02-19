package com.cells.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import com.cells.blocks.importinterface.ContainerImportInterface;
import com.cells.blocks.importinterface.ContainerMaxSlotSize;
import com.cells.blocks.importinterface.ContainerPollingRate;
import com.cells.blocks.importinterface.GuiImportInterface;
import com.cells.blocks.importinterface.GuiMaxSlotSize;
import com.cells.blocks.importinterface.GuiPollingRate;
import com.cells.blocks.importinterface.TileImportInterface;


/**
 * GUI handler for CELLS mod custom GUIs.
 */
public class CellsGuiHandler implements IGuiHandler {

    public static final int GUI_IMPORT_INTERFACE = 0;
    public static final int GUI_MAX_SLOT_SIZE = 1;
    public static final int GUI_POLLING_RATE = 2;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        switch (id) {
            case GUI_IMPORT_INTERFACE:
                if (tile instanceof TileImportInterface) {
                    return new ContainerImportInterface(player.inventory, (TileImportInterface) tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof TileImportInterface) {
                    return new ContainerMaxSlotSize(player.inventory, (TileImportInterface) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof TileImportInterface) {
                    return new ContainerPollingRate(player.inventory, (TileImportInterface) tile);
                }
                break;
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        switch (id) {
            case GUI_IMPORT_INTERFACE:
                if (tile instanceof TileImportInterface) {
                    return new GuiImportInterface(player.inventory, (TileImportInterface) tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof TileImportInterface) {
                    return new GuiMaxSlotSize(player.inventory, (TileImportInterface) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof TileImportInterface) {
                    return new GuiPollingRate(player.inventory, (TileImportInterface) tile);
                }
                break;
        }

        return null;
    }
}
