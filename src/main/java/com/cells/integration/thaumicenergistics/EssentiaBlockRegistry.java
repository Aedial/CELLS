package com.cells.integration.thaumicenergistics;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
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
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Registry for essentia-related blocks and items.
 * Registers blocks, items, tile entities, and models conditionally when
 * ThaumicEnergistics is loaded.
 */
public final class EssentiaBlockRegistry {

    public static BlockEssentiaImportInterface ESSENTIA_IMPORT_INTERFACE;
    public static BlockEssentiaExportInterface ESSENTIA_EXPORT_INTERFACE;
    public static BlockEssentiaIOInterface ESSENTIA_IO_INTERFACE;
    public static ItemEssentiaPart ESSENTIA_PART;

    private EssentiaBlockRegistry() {}

    /**
     * Register all essentia interface blocks.
     * Called from BlockRegistry when ThaumicEnergistics is loaded.
     */
    public static void registerBlocks(IForgeRegistry<Block> registry) {
        ESSENTIA_IMPORT_INTERFACE = new BlockEssentiaImportInterface();
        ESSENTIA_EXPORT_INTERFACE = new BlockEssentiaExportInterface();
        ESSENTIA_IO_INTERFACE = new BlockEssentiaIOInterface();

        registry.register(ESSENTIA_IMPORT_INTERFACE);
        registry.register(ESSENTIA_EXPORT_INTERFACE);
        registry.register(ESSENTIA_IO_INTERFACE);

        // Register tile entities
        GameRegistry.registerTileEntity(TileEssentiaImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_essentia_interface"));
        GameRegistry.registerTileEntity(TileEssentiaExportInterface.class,
            new ResourceLocation(Tags.MODID, "export_essentia_interface"));
        GameRegistry.registerTileEntity(TileEssentiaIOInterface.class,
            new ResourceLocation(Tags.MODID, "io_essentia_interface"));

        // Register tile-to-item mappings for Network Tool display
        AEBaseTile.registerTileItem(TileEssentiaImportInterface.class,
            new BlockStackSrc(ESSENTIA_IMPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileEssentiaExportInterface.class,
            new BlockStackSrc(ESSENTIA_EXPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileEssentiaIOInterface.class,
            new BlockStackSrc(ESSENTIA_IO_INTERFACE, 0, ActivityState.Enabled));
    }

    /**
     * Register all essentia interface items (block items and part items).
     * Called from BlockRegistry when ThaumicEnergistics is loaded.
     */
    public static void registerItems(IForgeRegistry<Item> registry) {
        // Register block items
        registry.register(createItemBlock(ESSENTIA_IMPORT_INTERFACE));
        registry.register(createItemBlock(ESSENTIA_EXPORT_INTERFACE));
        registry.register(createItemBlock(ESSENTIA_IO_INTERFACE));

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
        registerBlockModel(ESSENTIA_IO_INTERFACE);

        if (ESSENTIA_PART != null) ESSENTIA_PART.registerModels();
    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockModel(Block block) {
        ResourceLocation regName = block.getRegistryName();

        if (CellsConfig.interfaces.useFixedInterfaceTextures) {
            ResourceLocation fixedModel = new ResourceLocation(regName.getNamespace(), regName.getPath() + "_fixed");
            ModelResourceLocation fixedModelLoc = new ModelResourceLocation(fixedModel, "inventory");

            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, fixedModelLoc);

            ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return new ModelResourceLocation(fixedModel, "normal");
                }
            });

            return;
        }

        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block), 0,
            new ModelResourceLocation(regName, "inventory")
        );
    }
}
