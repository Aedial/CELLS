package com.cells.parts;

import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.parts.IPartModels;


/**
 * Registry for CELLS parts.
 * Handles item registration and model registration for parts.
 */
public class PartRegistry {

    public static ItemCellsPart CELLS_PART;

    public static void init() {
        CELLS_PART = new ItemCellsPart();

        // Register part models with AE2
        IPartModels partModels = AEApi.instance().registries().partModels();
        for (CellsPartType type : CellsPartType.values()) partModels.registerModels(type.getModels());
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        if (CELLS_PART != null) event.getRegistry().register(CELLS_PART);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void registerModels(ModelRegistryEvent event) {
        if (CELLS_PART != null) CELLS_PART.registerModels();
    }
}
