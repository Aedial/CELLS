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
import com.cells.blocks.importinterface.IImportInterfaceHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.blocks.fluidimportinterface.ContainerFluidImportInterface;
import com.cells.blocks.fluidimportinterface.GuiFluidImportInterface;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.cells.configurable.ContainerConfigurableCell;
import com.cells.cells.configurable.GuiConfigurableCell;

import net.minecraft.util.EnumHand;


/**
 * GUI handler for CELLS mod custom GUIs.
 */
public class CellsGuiHandler implements IGuiHandler {

    public static final int GUI_IMPORT_INTERFACE = 0;
    public static final int GUI_MAX_SLOT_SIZE = 1;
    public static final int GUI_POLLING_RATE = 2;
    public static final int GUI_CONFIGURABLE_CELL = 3;
    public static final int GUI_FLUID_IMPORT_INTERFACE = 4;

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

            case GUI_FLUID_IMPORT_INTERFACE:
                if (tile instanceof TileFluidImportInterface) {
                    return new ContainerFluidImportInterface(player.inventory, (TileFluidImportInterface) tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof IImportInterfaceHost) {
                    return new ContainerMaxSlotSize(player.inventory, (IImportInterfaceHost) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof IImportInterfaceHost) {
                    return new ContainerPollingRate(player.inventory, (IImportInterfaceHost) tile);
                }
                break;

            case GUI_CONFIGURABLE_CELL:
                return new ContainerConfigurableCell(player.inventory, EnumHand.values()[x]);
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

            case GUI_FLUID_IMPORT_INTERFACE:
                if (tile instanceof TileFluidImportInterface) {
                    return new GuiFluidImportInterface(player.inventory, (TileFluidImportInterface) tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof IImportInterfaceHost) {
                    return new GuiMaxSlotSize(player.inventory, (IImportInterfaceHost) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof IImportInterfaceHost) {
                    return new GuiPollingRate(player.inventory, (IImportInterfaceHost) tile);
                }
                break;

            case GUI_CONFIGURABLE_CELL:
                return new GuiConfigurableCell(player.inventory, EnumHand.values()[x]);
        }

        return null;
    }
}
