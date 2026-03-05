package com.cells.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.cells.Cells;
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
import com.cells.blocks.exportinterface.ContainerExportInterface;
import com.cells.blocks.exportinterface.GuiExportInterface;
import com.cells.blocks.exportinterface.IExportInterfaceInventoryHost;
import com.cells.blocks.exportinterface.TileExportInterface;
import com.cells.blocks.fluidexportinterface.ContainerFluidExportInterface;
import com.cells.blocks.fluidexportinterface.GuiFluidExportInterface;
import com.cells.blocks.fluidexportinterface.IFluidExportInterfaceInventoryHost;
import com.cells.blocks.fluidexportinterface.TileFluidExportInterface;
import com.cells.cells.configurable.ContainerConfigurableCell;
import com.cells.cells.configurable.GuiConfigurableCell;
import com.cells.parts.PartImportInterface;
import com.cells.parts.PartFluidImportInterface;
import com.cells.parts.PartExportInterface;
import com.cells.parts.PartFluidExportInterface;

import net.minecraft.util.EnumHand;


/**
 * GUI handler for CELLS mod custom GUIs.
 * <p>
 * For parts, the GUI ID encodes both the base ID and the side:
 * - Bits 0-2: side ordinal (AEPartLocation, 0-6)
 * - Bits 3+: base GUI ID shifted left by 3
 * <p>
 * Use {@link #openPartGui} to open part GUIs with proper side encoding.
 */
public class CellsGuiHandler implements IGuiHandler {

    // Block-based GUI IDs (no side encoding needed)
    public static final int GUI_IMPORT_INTERFACE = 0;
    public static final int GUI_MAX_SLOT_SIZE = 1;
    public static final int GUI_POLLING_RATE = 2;
    public static final int GUI_CONFIGURABLE_CELL = 3;
    public static final int GUI_FLUID_IMPORT_INTERFACE = 4;
    public static final int GUI_EXPORT_INTERFACE = 5;
    public static final int GUI_FLUID_EXPORT_INTERFACE = 6;

    // Part-based GUI IDs (require side encoding)
    public static final int GUI_PART_IMPORT_INTERFACE = 100;
    public static final int GUI_PART_FLUID_IMPORT_INTERFACE = 101;
    public static final int GUI_PART_MAX_SLOT_SIZE = 102;
    public static final int GUI_PART_POLLING_RATE = 103;
    public static final int GUI_PART_EXPORT_INTERFACE = 104;
    public static final int GUI_PART_FLUID_EXPORT_INTERFACE = 105;

    /**
     * Encode a part GUI ID with side information.
     *
     * @param baseGuiId The base GUI ID (must be a part GUI ID >= 100)
     * @param side The side the part is on
     * @return The encoded GUI ID with side information
     */
    public static int encodePartGuiId(int baseGuiId, AEPartLocation side) {
        return (baseGuiId << 3) | (side.ordinal() & 0x07);
    }

    /**
     * Open a GUI for a part, encoding the side information.
     *
     * @param player The player opening the GUI
     * @param tile The tile entity (usually IPartHost)
     * @param side The side the part is on
     * @param guiId The base GUI ID
     */
    public static void openPartGui(EntityPlayer player, TileEntity tile, AEPartLocation side, int guiId) {
        if (tile == null) return;

        BlockPos pos = tile.getPos();
        int encodedId = encodePartGuiId(guiId, side);

        player.openGui(Cells.instance, encodedId, tile.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Extract the base GUI ID from an encoded ID.
     */
    private static int getBaseGuiId(int encodedId) {
        return encodedId >> 3;
    }

    /**
     * Extract the side from an encoded ID.
     */
    private static AEPartLocation getSideFromGuiId(int encodedId) {
        return AEPartLocation.fromOrdinal(encodedId & 0x07);
    }

    /**
     * Check if a GUI ID is for a part (encoded with side).
     */
    private static boolean isPartGui(int id) {
        int baseId = getBaseGuiId(id);
        return baseId >= 100;
    }

    /**
     * Get the part from a tile entity given the encoded GUI ID.
     */
    private static IPart getPartFromTile(TileEntity tile, int encodedId) {
        if (!(tile instanceof IPartHost)) return null;

        AEPartLocation side = getSideFromGuiId(encodedId);
        return ((IPartHost) tile).getPart(side);
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // Handle part GUIs (encoded with side information)
        if (isPartGui(id)) {
            IPart part = getPartFromTile(tile, id);
            int baseId = getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_IMPORT_INTERFACE:
                    if (part instanceof PartImportInterface) {
                        return new ContainerImportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_IMPORT_INTERFACE:
                    if (part instanceof PartFluidImportInterface) {
                        return new ContainerFluidImportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_MAX_SLOT_SIZE:
                    if (part instanceof IImportInterfaceHost) {
                        return new ContainerMaxSlotSize(player.inventory, (IImportInterfaceHost) part);
                    }
                    break;

                case GUI_PART_POLLING_RATE:
                    if (part instanceof IImportInterfaceHost) {
                        return new ContainerPollingRate(player.inventory, (IImportInterfaceHost) part);
                    }
                    break;

                case GUI_PART_EXPORT_INTERFACE:
                    if (part instanceof PartExportInterface) {
                        return new ContainerExportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_EXPORT_INTERFACE:
                    if (part instanceof PartFluidExportInterface) {
                        return new ContainerFluidExportInterface(player.inventory, part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
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

            case GUI_EXPORT_INTERFACE:
                if (tile instanceof TileExportInterface) {
                    return new ContainerExportInterface(player.inventory, (TileExportInterface) tile);
                }
                break;

            case GUI_FLUID_EXPORT_INTERFACE:
                if (tile instanceof TileFluidExportInterface) {
                    return new ContainerFluidExportInterface(player.inventory, (TileFluidExportInterface) tile);
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
        if (isPartGui(id)) {
            IPart part = getPartFromTile(tile, id);
            int baseId = getBaseGuiId(id);

            switch (baseId) {
                case GUI_PART_IMPORT_INTERFACE:
                    if (part instanceof PartImportInterface) {
                        return new GuiImportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_IMPORT_INTERFACE:
                    if (part instanceof PartFluidImportInterface) {
                        return new GuiFluidImportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_MAX_SLOT_SIZE:
                    if (part instanceof IImportInterfaceHost) {
                        return new GuiMaxSlotSize(player.inventory, (IImportInterfaceHost) part);
                    }
                    break;

                case GUI_PART_POLLING_RATE:
                    if (part instanceof IImportInterfaceHost) {
                        return new GuiPollingRate(player.inventory, (IImportInterfaceHost) part);
                    }
                    break;

                case GUI_PART_EXPORT_INTERFACE:
                    if (part instanceof PartExportInterface) {
                        return new GuiExportInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_EXPORT_INTERFACE:
                    if (part instanceof PartFluidExportInterface) {
                        return new GuiFluidExportInterface(player.inventory, part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
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

            case GUI_EXPORT_INTERFACE:
                if (tile instanceof TileExportInterface) {
                    return new GuiExportInterface(player.inventory, (TileExportInterface) tile);
                }
                break;

            case GUI_FLUID_EXPORT_INTERFACE:
                if (tile instanceof TileFluidExportInterface) {
                    return new GuiFluidExportInterface(player.inventory, (TileFluidExportInterface) tile);
                }
                break;
        }

        return null;
    }
}
