package com.cells.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import appeng.api.AEApi;

import com.cells.ItemRegistry;
import com.cells.cells.normal.compacting.CompactingCellHandler;
import com.cells.cells.hyperdensity.fluid.FluidHyperDensityCellHandler;
import com.cells.cells.hyperdensity.item.HyperDensityCellHandler;
import com.cells.cells.hyperdensity.compacting.HyperDensityCompactingCellHandler;


public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        ItemRegistry.init();
        MinecraftForge.EVENT_BUS.register(new ItemRegistry());
    }

    public void init(FMLInitializationEvent event) {
        // Register the compacting cell handler with AE2 (must be done in init, after AE2's BasicCellHandler)
        AEApi.instance().registries().cell().addCellHandler(new CompactingCellHandler());

        // Register the hyper-density cell handler with AE2
        AEApi.instance().registries().cell().addCellHandler(new HyperDensityCellHandler());

        // Register the hyper-density compacting cell handler with AE2
        AEApi.instance().registries().cell().addCellHandler(new HyperDensityCompactingCellHandler());

        // Register the fluid hyper-density cell handler with AE2
        AEApi.instance().registries().cell().addCellHandler(new FluidHyperDensityCellHandler());
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
