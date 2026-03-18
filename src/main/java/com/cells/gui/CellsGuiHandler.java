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
import com.cells.blocks.interfacebase.ContainerFluidInterface;
import com.cells.blocks.interfacebase.ContainerItemInterface;
import com.cells.blocks.interfacebase.GuiFluidInterface;
import com.cells.blocks.interfacebase.GuiItemInterface;
import com.cells.blocks.interfacebase.IFluidInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IItemInterfaceHost;
import com.cells.blocks.importinterface.ContainerMaxSlotSize;
import com.cells.blocks.importinterface.ContainerPollingRate;
import com.cells.blocks.importinterface.GuiMaxSlotSize;
import com.cells.blocks.importinterface.GuiPollingRate;
import com.cells.cells.configurable.ContainerConfigurableCell;
import com.cells.cells.configurable.GuiConfigurableCell;
import com.cells.cells.creative.ContainerCreativeCell;
import com.cells.cells.creative.GuiCreativeCell;

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
    public static final int GUI_CREATIVE_CELL = 7;

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
                    if (part instanceof IItemInterfaceHost) {
                        return new ContainerItemInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_IMPORT_INTERFACE:
                    if (part instanceof IFluidInterfaceHost) {
                        return new ContainerFluidInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new ContainerPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_EXPORT_INTERFACE:
                    if (part instanceof IItemInterfaceHost) {
                        return new ContainerItemInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_EXPORT_INTERFACE:
                    if (part instanceof IFluidInterfaceHost) {
                        return new ContainerFluidInterface(player.inventory, part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_IMPORT_INTERFACE:
                if (tile instanceof IItemInterfaceHost) {
                    return new ContainerItemInterface(player.inventory, tile);
                }
                break;

            case GUI_FLUID_IMPORT_INTERFACE:
                if (tile instanceof IFluidInterfaceHost) {
                    return new ContainerFluidInterface(player.inventory, tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new ContainerMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof IInterfaceHost) {
                    return new ContainerPollingRate(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_CONFIGURABLE_CELL:
                return new ContainerConfigurableCell(player.inventory, EnumHand.values()[x]);

            case GUI_CREATIVE_CELL:
                // Only allow creative mode players to open this GUI
                if (player.isCreative()) {
                    return new ContainerCreativeCell(player.inventory, EnumHand.values()[x]);
                }
                return null;

            case GUI_EXPORT_INTERFACE:
                if (tile instanceof IItemInterfaceHost) {
                    return new ContainerItemInterface(player.inventory, tile);
                }
                break;

            case GUI_FLUID_EXPORT_INTERFACE:
                if (tile instanceof IFluidInterfaceHost) {
                    return new ContainerFluidInterface(player.inventory, tile);
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
                    if (part instanceof IItemInterfaceHost) {
                        return new GuiItemInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_IMPORT_INTERFACE:
                    if (part instanceof IFluidInterfaceHost) {
                        return new GuiFluidInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_MAX_SLOT_SIZE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_POLLING_RATE:
                    if (part instanceof IInterfaceHost) {
                        return new GuiPollingRate(player.inventory, (IInterfaceHost) part);
                    }
                    break;

                case GUI_PART_EXPORT_INTERFACE:
                    if (part instanceof IItemInterfaceHost) {
                        return new GuiItemInterface(player.inventory, part);
                    }
                    break;

                case GUI_PART_FLUID_EXPORT_INTERFACE:
                    if (part instanceof IFluidInterfaceHost) {
                        return new GuiFluidInterface(player.inventory, part);
                    }
                    break;
            }

            return null;
        }

        // Handle block-based GUIs
        switch (id) {
            case GUI_IMPORT_INTERFACE:
                if (tile instanceof IItemInterfaceHost) {
                    return new GuiItemInterface(player.inventory, tile);
                }
                break;

            case GUI_FLUID_IMPORT_INTERFACE:
                if (tile instanceof IFluidInterfaceHost) {
                    return new GuiFluidInterface(player.inventory, tile);
                }
                break;

            case GUI_MAX_SLOT_SIZE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiMaxSlotSize(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_POLLING_RATE:
                if (tile instanceof IInterfaceHost) {
                    return new GuiPollingRate(player.inventory, (IInterfaceHost) tile);
                }
                break;

            case GUI_CONFIGURABLE_CELL:
                return new GuiConfigurableCell(player.inventory, EnumHand.values()[x]);

            case GUI_CREATIVE_CELL:
                // Only allow creative mode players to open this GUI
                if (player.isCreative()) {
                    return new GuiCreativeCell(player.inventory, EnumHand.values()[x]);
                }
                return null;

            case GUI_EXPORT_INTERFACE:
                if (tile instanceof IItemInterfaceHost) {
                    return new GuiItemInterface(player.inventory, tile);
                }
                break;

            case GUI_FLUID_EXPORT_INTERFACE:
                if (tile instanceof IFluidInterfaceHost) {
                    return new GuiFluidInterface(player.inventory, tile);
                }
                break;
        }

        return null;
    }
}
