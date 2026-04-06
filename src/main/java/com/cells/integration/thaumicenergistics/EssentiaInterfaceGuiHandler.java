package com.cells.integration.thaumicenergistics;

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
 * GUI handler for Essentia Interface GUIs.
 * Separated from main CellsGuiHandler to allow conditional loading only when
 * ThaumicEnergistics is present.
 * <p>
 * See {@link GuiIdUtils} for GUI ID encoding/decoding utilities.
 */
public class EssentiaInterfaceGuiHandler implements IGuiHandler {

    // Block-based GUI IDs (no side encoding needed)
    public static final int GUI_ESSENTIA_IMPORT_INTERFACE = 400;
    public static final int GUI_ESSENTIA_EXPORT_INTERFACE = 401;
    public static final int GUI_ESSENTIA_MAX_SLOT_SIZE = 402;
    public static final int GUI_ESSENTIA_POLLING_RATE = 403;

    // Part-based GUI IDs (require side encoding)
    public static final int GUI_PART_ESSENTIA_IMPORT_INTERFACE = 500;
    public static final int GUI_PART_ESSENTIA_EXPORT_INTERFACE = 501;
    public static final int GUI_PART_ESSENTIA_MAX_SLOT_SIZE = 502;
    public static final int GUI_PART_ESSENTIA_POLLING_RATE = 503;

    /**
     * Check if a GUI ID is for an essentia part (encoded with side, base >= 500).
     */
    private static boolean isEssentiaPartGui(int id) {
        int baseId = GuiIdUtils.getBaseGuiId(id);
        return baseId >= 500 && baseId < 600;
    }

    /**
     * Check if a GUI ID belongs to this handler.
     */
    public static boolean isEssentiaInterfaceGuiId(int id) {
        // Block GUIs: 400-403
        if (id >= 400 && id <= 403) return true;

        // Part GUIs: encoded with base 500-503
        int baseId = GuiIdUtils.getBaseGuiId(id);
        return baseId >= 500 && baseId <= 503;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // Handle part GUIs (encoded with side information)
        if (isEssentiaPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_ESSENTIA_IMPORT_INTERFACE:
                    if (part instanceof IEssentiaInterfaceHost) {
                        return new ContainerEssentiaInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_ESSENTIA_EXPORT_INTERFACE:
                    if (part instanceof IEssentiaInterfaceHost) {
                        return new ContainerEssentiaInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_ESSENTIA_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_ESSENTIA_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_ESSENTIA_IMPORT_INTERFACE:
                if (tile instanceof IEssentiaInterfaceHost) {
                    return new ContainerEssentiaInterface(player.inventory, tile);
                }
                break;

            case GUI_ESSENTIA_EXPORT_INTERFACE:
                if (tile instanceof IEssentiaInterfaceHost) {
                    return new ContainerEssentiaInterface(player.inventory, tile);
                }
                break;

            case GUI_ESSENTIA_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_ESSENTIA_POLLING_RATE:
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
        if (isEssentiaPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_ESSENTIA_IMPORT_INTERFACE:
                    if (part instanceof IEssentiaInterfaceHost) {
                        return new GuiEssentiaInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_ESSENTIA_EXPORT_INTERFACE:
                    if (part instanceof IEssentiaInterfaceHost) {
                        return new GuiEssentiaInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_ESSENTIA_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_ESSENTIA_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_ESSENTIA_IMPORT_INTERFACE:
                if (tile instanceof IEssentiaInterfaceHost) {
                    return new GuiEssentiaInterface(player.inventory, tile);
                }
                break;

            case GUI_ESSENTIA_EXPORT_INTERFACE:
                if (tile instanceof IEssentiaInterfaceHost) {
                    return new GuiEssentiaInterface(player.inventory, tile);
                }
                break;

            case GUI_ESSENTIA_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_ESSENTIA_POLLING_RATE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiPollingRate(player.inventory, (IInterfaceHost) tile);
                }
                break;
        }

        return null;
    }
}
