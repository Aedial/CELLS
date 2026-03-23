package com.cells.proxy;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.cells.ItemRegistry;
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
        // Recovery container uses the contained fluid's color for tinting
        if (ItemRegistry.RECOVERY_CONTAINER != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler(
                ItemRecoveryContainer::getColor,
                ItemRegistry.RECOVERY_CONTAINER
            );
        }
    }
}
