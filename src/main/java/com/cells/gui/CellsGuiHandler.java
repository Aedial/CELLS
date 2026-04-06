package com.cells.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.parts.IPart;
import appeng.api.util.AEPartLocation;

import com.cells.blocks.interfacebase.fluid.ContainerFluidInterface;
import com.cells.blocks.interfacebase.item.ContainerItemInterface;
import com.cells.blocks.interfacebase.fluid.GuiFluidInterface;
import com.cells.blocks.interfacebase.item.GuiItemInterface;
import com.cells.blocks.interfacebase.fluid.IFluidInterfaceHost;
import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.item.IItemInterfaceHost;
import com.cells.blocks.interfacebase.ContainerMaxSlotSize;
import com.cells.blocks.interfacebase.ContainerPollingRate;
import com.cells.blocks.interfacebase.GuiMaxSlotSize;
import com.cells.blocks.interfacebase.GuiPollingRate;
import com.cells.cells.configurable.ContainerConfigurableCell;
import com.cells.cells.configurable.GuiConfigurableCell;
import com.cells.cells.creative.item.ContainerCreativeCell;
import com.cells.cells.creative.item.GuiCreativeCell;
import com.cells.cells.creative.fluid.ContainerCreativeFluidCell;
import com.cells.cells.creative.fluid.GuiCreativeFluidCell;
import com.cells.integration.mekanismenergistics.CreativeGasCellGuiHandler;
import com.cells.integration.mekanismenergistics.GasInterfaceGuiHandler;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.CreativeEssentiaCellGuiHandler;
import com.cells.integration.thaumicenergistics.EssentiaInterfaceGuiHandler;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.items.pullpush.ContainerPullPushCard;
import com.cells.items.pullpush.GuiPullPushCard;


/**
 * GUI handler for CELLS mod custom GUIs.
 * <p>
 * See {@link GuiIdUtils} for GUI ID encoding/decoding utilities.
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
    public static final int GUI_CREATIVE_FLUID_CELL = 8;
    public static final int GUI_CREATIVE_GAS_CELL = 9;
    public static final int GUI_CREATIVE_ESSENTIA_CELL = 10;
    public static final int GUI_PULL_PUSH_CARD = 11;
    public static final int GUI_PULL_PUSH_CARD_INTERFACE = 12;

    // Part-based GUI IDs (require side encoding)
    public static final int GUI_PART_IMPORT_INTERFACE = 100;
    public static final int GUI_PART_FLUID_IMPORT_INTERFACE = 101;
    public static final int GUI_PART_MAX_SLOT_SIZE = 102;
    public static final int GUI_PART_POLLING_RATE = 103;
    public static final int GUI_PART_EXPORT_INTERFACE = 104;
    public static final int GUI_PART_FLUID_EXPORT_INTERFACE = 105;
    public static final int GUI_PART_PULL_PUSH_CARD_INTERFACE = 106;

    // Lazily initialized gas GUI handler (null if MekanismEnergistics not loaded)
    private GasInterfaceGuiHandler gasGuiHandler;
    private boolean gasGuiHandlerChecked = false;

    // Lazily initialized essentia GUI handler (null if ThaumicEnergistics not loaded)
    private EssentiaInterfaceGuiHandler essentiaGuiHandler;
    private boolean essentiaGuiHandlerChecked = false;

    // Lazily initialized creative cell GUI handlers for optional mods
    private CreativeGasCellGuiHandler creativeGasGuiHandler;
    private boolean creativeGasGuiHandlerChecked = false;
    private CreativeEssentiaCellGuiHandler creativeEssentiaGuiHandler;
    private boolean creativeEssentiaGuiHandlerChecked = false;

    /**
     * Open a GUI for a part, encoding the side information.
     * Convenience method that delegates to {@link GuiIdUtils#openPartGui}.
     */
    public static void openPartGui(EntityPlayer player, TileEntity tile, AEPartLocation side, int guiId) {
        GuiIdUtils.openPartGui(player, tile, side, guiId);
    }

    /**
     * Get the gas GUI handler, creating it if necessary and MekanismEnergistics is loaded.
     */
    private GasInterfaceGuiHandler getGasGuiHandler() {
        if (!gasGuiHandlerChecked) {
            gasGuiHandlerChecked = true;
            if (MekanismEnergisticsIntegration.isModLoaded()) {
                gasGuiHandler = new GasInterfaceGuiHandler();
            }
        }
        return gasGuiHandler;
    }

    /**
     * Get the essentia GUI handler, creating it if necessary and ThaumicEnergistics is loaded.
     */
    private EssentiaInterfaceGuiHandler getEssentiaGuiHandler() {
        if (!essentiaGuiHandlerChecked) {
            essentiaGuiHandlerChecked = true;
            if (ThaumicEnergisticsIntegration.isModLoaded()) {
                essentiaGuiHandler = new EssentiaInterfaceGuiHandler();
            }
        }
        return essentiaGuiHandler;
    }

    /**
     * Get the creative gas cell GUI handler, creating if necessary and MekanismEnergistics is loaded.
     */
    private CreativeGasCellGuiHandler getCreativeGasGuiHandler() {
        if (!creativeGasGuiHandlerChecked) {
            creativeGasGuiHandlerChecked = true;
            if (MekanismEnergisticsIntegration.isModLoaded()) {
                creativeGasGuiHandler = new CreativeGasCellGuiHandler();
            }
        }
        return creativeGasGuiHandler;
    }

    /**
     * Get the creative essentia cell GUI handler, creating if necessary and ThaumicEnergistics is loaded.
     */
    private CreativeEssentiaCellGuiHandler getCreativeEssentiaGuiHandler() {
        if (!creativeEssentiaGuiHandlerChecked) {
            creativeEssentiaGuiHandlerChecked = true;
            if (ThaumicEnergisticsIntegration.isModLoaded()) {
                creativeEssentiaGuiHandler = new CreativeEssentiaCellGuiHandler();
            }
        }
        return creativeEssentiaGuiHandler;
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // Delegate to gas GUI handler if this is a gas GUI ID
        GasInterfaceGuiHandler gasHandler = getGasGuiHandler();
        if (gasHandler != null && GasInterfaceGuiHandler.isGasInterfaceGuiId(id)) {
            return gasHandler.getServerGuiElement(id, player, world, x, y, z);
        }

        // Delegate to essentia GUI handler if this is an essentia GUI ID
        EssentiaInterfaceGuiHandler essentiaHandler = getEssentiaGuiHandler();
        if (essentiaHandler != null && EssentiaInterfaceGuiHandler.isEssentiaInterfaceGuiId(id)) {
            return essentiaHandler.getServerGuiElement(id, player, world, x, y, z);
        }

        // Delegate to creative gas cell GUI handler
        if (id == GUI_CREATIVE_GAS_CELL) {
            CreativeGasCellGuiHandler creativeGasHandler = getCreativeGasGuiHandler();
            if (creativeGasHandler != null && player.isCreative()) {
                return creativeGasHandler.getServerGuiElement(id, player, world, x, y, z);
            }
            return null;
        }

        // Delegate to creative essentia cell GUI handler
        if (id == GUI_CREATIVE_ESSENTIA_CELL) {
            CreativeEssentiaCellGuiHandler creativeEssentiaHandler = getCreativeEssentiaGuiHandler();
            if (creativeEssentiaHandler != null && player.isCreative()) {
                return creativeEssentiaHandler.getServerGuiElement(id, player, world, x, y, z);
            }
            return null;
        }

        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // TODO: refactor that
        // Handle part GUIs (encoded with side information)
        if (GuiIdUtils.isPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

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

                case GUI_PART_PULL_PUSH_CARD_INTERFACE:
                    if (part instanceof IFilterableInterfaceHost) {
                        //noinspection rawtypes
                        return new ContainerPullPushCard(player.inventory, (IFilterableInterfaceHost) part);
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

            case GUI_CREATIVE_FLUID_CELL:
                // Only allow creative mode players to open this GUI
                if (player.isCreative()) {
                    return new ContainerCreativeFluidCell(player.inventory, EnumHand.values()[x]);
                }
                return null;

            case GUI_PULL_PUSH_CARD:
                return new ContainerPullPushCard(player.inventory, EnumHand.values()[x]);

            case GUI_PULL_PUSH_CARD_INTERFACE:
                if (tile instanceof IFilterableInterfaceHost) {
                    //noinspection rawtypes
                    return new ContainerPullPushCard(player.inventory, (IFilterableInterfaceHost) tile);
                }
                break;

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
        // Delegate to gas GUI handler if this is a gas GUI ID
        GasInterfaceGuiHandler gasHandler = getGasGuiHandler();
        if (gasHandler != null && GasInterfaceGuiHandler.isGasInterfaceGuiId(id)) {
            return gasHandler.getClientGuiElement(id, player, world, x, y, z);
        }

        // Delegate to essentia GUI handler if this is an essentia GUI ID
        EssentiaInterfaceGuiHandler essentiaHandler = getEssentiaGuiHandler();
        if (essentiaHandler != null && EssentiaInterfaceGuiHandler.isEssentiaInterfaceGuiId(id)) {
            return essentiaHandler.getClientGuiElement(id, player, world, x, y, z);
        }

        // Delegate to creative gas cell GUI handler
        if (id == GUI_CREATIVE_GAS_CELL) {
            CreativeGasCellGuiHandler creativeGasHandler = getCreativeGasGuiHandler();
            if (creativeGasHandler != null && player.isCreative()) {
                return creativeGasHandler.getClientGuiElement(id, player, world, x, y, z);
            }
            return null;
        }

        // Delegate to creative essentia cell GUI handler
        if (id == GUI_CREATIVE_ESSENTIA_CELL) {
            CreativeEssentiaCellGuiHandler creativeEssentiaHandler = getCreativeEssentiaGuiHandler();
            if (creativeEssentiaHandler != null && player.isCreative()) {
                return creativeEssentiaHandler.getClientGuiElement(id, player, world, x, y, z);
            }
            return null;
        }

        BlockPos pos = new BlockPos(x, y, z);
        TileEntity tile = world.getTileEntity(pos);

        // Handle part GUIs (encoded with side information)
        if (GuiIdUtils.isPartGui(id)) {
            IPart part = GuiIdUtils.getPartFromTile(tile, id);
            int baseId = GuiIdUtils.getBaseGuiId(id);

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

                case GUI_PART_PULL_PUSH_CARD_INTERFACE:
                    if (part instanceof IFilterableInterfaceHost) {
                        //noinspection rawtypes
                        return new GuiPullPushCard(player.inventory, (IFilterableInterfaceHost) part);
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

            case GUI_CREATIVE_FLUID_CELL:
                // Only allow creative mode players to open this GUI
                if (player.isCreative()) {
                    return new GuiCreativeFluidCell(player.inventory, EnumHand.values()[x]);
                }
                return null;

            case GUI_PULL_PUSH_CARD:
                return new GuiPullPushCard(player.inventory, EnumHand.values()[x]);

            case GUI_PULL_PUSH_CARD_INTERFACE:
                if (tile instanceof IFilterableInterfaceHost) {
                    //noinspection rawtypes
                    return new GuiPullPushCard(player.inventory, (IFilterableInterfaceHost) tile);
                }
                break;

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
