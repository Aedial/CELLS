package com.cells.integration.mekanismenergistics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.parts.IPart;

import com.cells.blocks.interfacebase.ContainerMaxSlotSize;
import com.cells.blocks.interfacebase.ContainerPollingRate;
import com.cells.blocks.interfacebase.GuiMaxSlotSize;
import com.cells.blocks.interfacebase.GuiPollingRate;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.gui.GuiIdUtils;


/**
 * GUI handler for Gas Interface GUIs.
 * Separated from main CellsGuiHandler to allow conditional loading only when
 * MekanismEnergistics is present.
 * <p>
 * See {@link GuiIdUtils} for GUI ID encoding/decoding utilities.
 */
public class GasInterfaceGuiHandler implements IGuiHandler {

    // Block-based GUI IDs (no side encoding needed)
    public static final int GUI_GAS_IMPORT_INTERFACE = 200;
    public static final int GUI_GAS_EXPORT_INTERFACE = 201;
    public static final int GUI_GAS_MAX_SLOT_SIZE = 202;
    public static final int GUI_GAS_POLLING_RATE = 203;

    // Part-based GUI IDs (require side encoding)
    public static final int GUI_PART_GAS_IMPORT_INTERFACE = 300;
    public static final int GUI_PART_GAS_EXPORT_INTERFACE = 301;
    public static final int GUI_PART_GAS_MAX_SLOT_SIZE = 302;
    public static final int GUI_PART_GAS_POLLING_RATE = 303;

    /**
     * Check if a GUI ID is for a gas part (encoded with side, base >= 300).
     */
    private static boolean isGasPartGui(int id) {
        int baseId = GuiIdUtils.getBaseGuiId(id);
        return baseId >= 300;
    }

    /**
     * Check if a GUI ID belongs to this handler.
     */
    public static boolean isGasInterfaceGuiId(int id) {
        // Block GUIs: 200-203
        if (id >= 200 && id <= 203) return true;

        // Part GUIs: encoded with base 300-303
        int baseId = GuiIdUtils.getBaseGuiId(id);
        return baseId >= 300 && baseId <= 303;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // Handle part GUIs (encoded with side information)
        if (isGasPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_GAS_IMPORT_INTERFACE:
                    if (part instanceof IGasInterfaceHost) {
                        return new ContainerGasInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_GAS_EXPORT_INTERFACE:
                    if (part instanceof IGasInterfaceHost) {
                        return new ContainerGasInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_GAS_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_GAS_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_GAS_IMPORT_INTERFACE:
                if (tile instanceof IGasInterfaceHost) {
                    return new ContainerGasInterface(player.inventory, tile);
                }
                break;

            case GUI_GAS_EXPORT_INTERFACE:
                if (tile instanceof IGasInterfaceHost) {
                    return new ContainerGasInterface(player.inventory, tile);
                }
                break;

            case GUI_GAS_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_GAS_POLLING_RATE:
                if (tile instanceof IInterfaceHost) {
                    return new ContainerPollingRate(player.inventory, (IInterfaceHost) tile);
                }
                break;
        }

        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // Handle part GUIs (encoded with side information)
        if (isGasPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_GAS_IMPORT_INTERFACE:
                    if (part instanceof IGasInterfaceHost) {
                        return new GuiGasInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_GAS_EXPORT_INTERFACE:
                    if (part instanceof IGasInterfaceHost) {
                        return new GuiGasInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_GAS_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_GAS_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_GAS_IMPORT_INTERFACE:
                if (tile instanceof IGasInterfaceHost) {
                    return new GuiGasInterface(player.inventory, tile);
                }
                break;

            case GUI_GAS_EXPORT_INTERFACE:
                if (tile instanceof IGasInterfaceHost) {
                    return new GuiGasInterface(player.inventory, tile);
                }
                break;

            case GUI_GAS_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_GAS_POLLING_RATE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiPollingRate(player.inventory, (IInterfaceHost) tile);
                }
                break;
        }

        return null;
    }
}
