package com.cells.proxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.cells.ItemRegistry;
import com.cells.client.CellItemColors;
import com.cells.client.ComponentItemColors;
import com.cells.client.ComponentTooltipHandler;
import com.cells.client.KeyBindings;
import com.cells.client.MemoryCardInteractionHandler;
import com.cells.items.ItemRecoveryContainer;


public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // Register keybindings
        KeyBindings.registerAll();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);

        // Register memory card interaction handler
        MinecraftForge.EVENT_BUS.register(new MemoryCardInteractionHandler());

        // Register component tooltip handler for configurable cell compatibility
        MinecraftForge.EVENT_BUS.register(new ComponentTooltipHandler());

        // Register item color handlers
        registerItemColors();
    }

    /**
     * Register custom item color handlers for items that need tinting.
     */
    private void registerItemColors() {
        ItemColors itemColors = Minecraft.getMinecraft().getItemColors();

        // Register layered cell color handlers for all cell types
        registerCellColors(itemColors);

        // Register component color handlers for all component types
        registerComponentColors(itemColors);

        // Recovery container uses the contained fluid's color for tinting
        if (ItemRegistry.RECOVERY_CONTAINER != null) {
            itemColors.registerItemColorHandler(
                ItemRecoveryContainer::getColor,
                ItemRegistry.RECOVERY_CONTAINER
            );
        }
    }

    /**
     * Register color handlers for the modular layered cell texture system.
     * All cell types use CellItemColors to determine tint colors for their 5 layers.
     */
    private void registerCellColors(ItemColors itemColors) {
        CellItemColors colorHandler = CellItemColors.INSTANCE;

        // Compacting cells
        if (ItemRegistry.COMPACTING_CELL != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.COMPACTING_CELL);
        }

        // Hyper Density cells (item)
        if (ItemRegistry.HYPER_DENSITY_CELL != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.HYPER_DENSITY_CELL);
        }

        // Hyper Density Compacting cells
        if (ItemRegistry.HYPER_DENSITY_COMPACTING_CELL != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.HYPER_DENSITY_COMPACTING_CELL);
        }

        // Hyper Density Fluid cells
        if (ItemRegistry.FLUID_HYPER_DENSITY_CELL != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.FLUID_HYPER_DENSITY_CELL);
        }

        // Configurable cells (supports all channel types)
        if (ItemRegistry.CONFIGURABLE_CELL != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.CONFIGURABLE_CELL);
        }
    }

    /**
     * Register color handlers for the modular layered component texture system.
     * All component types use ComponentItemColors to determine tint color for the wave layer.
     */
    private void registerComponentColors(ItemColors itemColors) {
        ComponentItemColors colorHandler = ComponentItemColors.INSTANCE;

        // Compacting components
        if (ItemRegistry.COMPACTING_COMPONENT != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.COMPACTING_COMPONENT);
        }

        // Hyper Density components (item)
        if (ItemRegistry.HYPER_DENSITY_COMPONENT != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.HYPER_DENSITY_COMPONENT);
        }

        // Hyper Density Compacting components
        if (ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.HYPER_DENSITY_COMPACTING_COMPONENT);
        }

        // Fluid Hyper Density components
        if (ItemRegistry.FLUID_HYPER_DENSITY_COMPONENT != null) {
            itemColors.registerItemColorHandler(colorHandler, ItemRegistry.FLUID_HYPER_DENSITY_COMPONENT);
        }
    }
}
