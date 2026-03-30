package com.cells;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.block.AEBaseItemBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

import com.cells.blocks.importinterface.BlockImportInterface;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.blocks.fluidimportinterface.BlockFluidImportInterface;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.blocks.exportinterface.BlockExportInterface;
import com.cells.blocks.exportinterface.TileExportInterface;
import com.cells.blocks.fluidexportinterface.BlockFluidExportInterface;
import com.cells.blocks.fluidexportinterface.TileFluidExportInterface;
import com.cells.integration.mekanismenergistics.GasBlockRegistry;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.EssentiaBlockRegistry;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;


public class BlockRegistry {

    public static BlockImportInterface IMPORT_INTERFACE;
    public static BlockFluidImportInterface FLUID_IMPORT_INTERFACE;
    public static BlockExportInterface EXPORT_INTERFACE;
    public static BlockFluidExportInterface FLUID_EXPORT_INTERFACE;

    public static void init() {
        // Block construction is deferred to registry events
        // to ensure Material class fields are properly remapped on Cleanroom
        // Material fields (like Material.IRON) are not yet remapped during preInit on modern JVMs (Java 17+).
        // The registry event fires after remapping is complete, avoiding NoSuchFieldError.
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        IMPORT_INTERFACE = new BlockImportInterface();
        FLUID_IMPORT_INTERFACE = new BlockFluidImportInterface();
        EXPORT_INTERFACE = new BlockExportInterface();
        FLUID_EXPORT_INTERFACE = new BlockFluidExportInterface();

        event.getRegistry().register(IMPORT_INTERFACE);
        event.getRegistry().register(FLUID_IMPORT_INTERFACE);
        event.getRegistry().register(EXPORT_INTERFACE);
        event.getRegistry().register(FLUID_EXPORT_INTERFACE);

        // Register tile entities
        GameRegistry.registerTileEntity(TileImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_interface"));
        GameRegistry.registerTileEntity(TileFluidImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_fluid_interface"));
        GameRegistry.registerTileEntity(TileExportInterface.class,
            new ResourceLocation(Tags.MODID, "export_interface"));
        GameRegistry.registerTileEntity(TileFluidExportInterface.class,
            new ResourceLocation(Tags.MODID, "export_fluid_interface"));

        // Register tile-to-item mappings for Network Tool display
        // Without this, tiles won't show up in the Network Tool GUI
        AEBaseTile.registerTileItem(TileImportInterface.class,
            new BlockStackSrc(IMPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileFluidImportInterface.class,
            new BlockStackSrc(FLUID_IMPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileExportInterface.class,
            new BlockStackSrc(EXPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileFluidExportInterface.class,
            new BlockStackSrc(FLUID_EXPORT_INTERFACE, 0, ActivityState.Enabled));

        // Register gas interface blocks if MekanismEnergistics is loaded
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            GasBlockRegistry.registerBlocks(event.getRegistry());
        }

        // Register essentia interface blocks if ThaumicEnergistics is loaded
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            EssentiaBlockRegistry.registerBlocks(event.getRegistry());
        }
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(createItemBlock(IMPORT_INTERFACE));
        event.getRegistry().register(createItemBlock(FLUID_IMPORT_INTERFACE));
        event.getRegistry().register(createItemBlock(EXPORT_INTERFACE));
        event.getRegistry().register(createItemBlock(FLUID_EXPORT_INTERFACE));

        // Register gas interface items if MekanismEnergistics is loaded
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            GasBlockRegistry.registerItems(event.getRegistry());
        }

        // Register essentia interface items if ThaumicEnergistics is loaded
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            EssentiaBlockRegistry.registerItems(event.getRegistry());
        }
    }

    private ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new AEBaseItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());

        return itemBlock;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void registerModels(ModelRegistryEvent event) {
        registerBlockModel(IMPORT_INTERFACE);
        registerBlockModel(FLUID_IMPORT_INTERFACE);
        registerBlockModel(EXPORT_INTERFACE);
        registerBlockModel(FLUID_EXPORT_INTERFACE);

        // Register gas interface models if MekanismEnergistics is loaded
        if (MekanismEnergisticsIntegration.isModLoaded()) {
            GasBlockRegistry.registerModels();
        }

        // Register essentia interface models if ThaumicEnergistics is loaded
        if (ThaumicEnergisticsIntegration.isModLoaded()) {
            EssentiaBlockRegistry.registerModels();
        }
    }

    @SideOnly(Side.CLIENT)
    private void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block), 0,
            new ModelResourceLocation(block.getRegistryName(), "inventory")
        );
    }

}
