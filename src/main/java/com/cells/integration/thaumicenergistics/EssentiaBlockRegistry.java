package com.cells.integration.thaumicenergistics;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;

import appeng.api.AEApi;
import appeng.api.parts.IPartModels;
import appeng.block.AEBaseItemBlock;

import com.cells.Tags;


/**
 * Registry for essentia-related blocks and items.
 * Registers blocks, items, tile entities, and models conditionally when
 * ThaumicEnergistics is loaded.
 */
public final class EssentiaBlockRegistry {

    public static BlockEssentiaImportInterface ESSENTIA_IMPORT_INTERFACE;
    public static BlockEssentiaExportInterface ESSENTIA_EXPORT_INTERFACE;
    public static ItemEssentiaPart ESSENTIA_PART;

    private EssentiaBlockRegistry() {}

    /**
     * Register all essentia interface blocks.
     * Called from BlockRegistry when ThaumicEnergistics is loaded.
     */
    public static void registerBlocks(IForgeRegistry<Block> registry) {
        ESSENTIA_IMPORT_INTERFACE = new BlockEssentiaImportInterface();
        ESSENTIA_EXPORT_INTERFACE = new BlockEssentiaExportInterface();

        registry.register(ESSENTIA_IMPORT_INTERFACE);
        registry.register(ESSENTIA_EXPORT_INTERFACE);

        // Register tile entities
        GameRegistry.registerTileEntity(TileEssentiaImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_essentia_interface"));
        GameRegistry.registerTileEntity(TileEssentiaExportInterface.class,
            new ResourceLocation(Tags.MODID, "export_essentia_interface"));
    }

    /**
     * Register all essentia interface items (block items and part items).
     * Called from BlockRegistry when ThaumicEnergistics is loaded.
     */
    public static void registerItems(IForgeRegistry<Item> registry) {
        // Register block items
        registry.register(createItemBlock(ESSENTIA_IMPORT_INTERFACE));
        registry.register(createItemBlock(ESSENTIA_EXPORT_INTERFACE));

        // Register essentia part item
        ESSENTIA_PART = new ItemEssentiaPart();
        registry.register(ESSENTIA_PART);
    }

    /**
     * Register part models with AE2's part model registry.
     * Must be called during preInit before parts are placed.
     */
    public static void registerPartModels() {
        IPartModels partModels = AEApi.instance().registries().partModels();

        for (CellsEssentiaPartType type : CellsEssentiaPartType.values()) {
            partModels.registerModels(type.getModels());
        }
    }

    private static ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new AEBaseItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());
        return itemBlock;
    }

    /**
     * Register client-side models for essentia blocks and parts.
     * Called from BlockRegistry when ThaumicEnergistics is loaded.
     */
    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        registerBlockModel(ESSENTIA_IMPORT_INTERFACE);
        registerBlockModel(ESSENTIA_EXPORT_INTERFACE);

        if (ESSENTIA_PART != null) ESSENTIA_PART.registerModels();
    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block), 0,
            new ModelResourceLocation(block.getRegistryName(), "inventory")
        );
    }
}
