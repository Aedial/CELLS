package com.cells;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import com.cells.commands.FillCellCommand;

import com.cells.config.CellsConfig;
import com.cells.proxy.CommonProxy;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:appliedenergistics2;",
    guiFactory = "com.cells.config.CellsGuiFactory"
)
public class Cells {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.Instance(Tags.MODID)
    public static Cells instance;

    @SidedProxy(
        clientSide = "com.cells.proxy.ClientProxy",
        serverSide = "com.cells.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Initialize configuration
        File configDir = event.getModConfigurationDirectory();
        CellsConfig.init(new File(configDir, Tags.MODID + ".cfg"));
        MinecraftForge.EVENT_BUS.register(new CellsConfig());

        proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FillCellCommand());
    }
}
