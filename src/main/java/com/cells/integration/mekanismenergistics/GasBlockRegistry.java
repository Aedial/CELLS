package com.cells.integration.mekanismenergistics;

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
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

import com.cells.Tags;


/**
 * Registry for gas-related blocks and items.
 * Registers blocks, items, tile entities, and models conditionally when
 * MekanismEnergistics is loaded.
 */
public final class GasBlockRegistry {

    public static BlockGasImportInterface GAS_IMPORT_INTERFACE;
    public static BlockGasExportInterface GAS_EXPORT_INTERFACE;
    public static ItemGasPart GAS_PART;

    private GasBlockRegistry() {}

    /**
     * Register all gas interface blocks.
     * Called from BlockRegistry when MekanismEnergistics is loaded.
     */
    public static void registerBlocks(IForgeRegistry<Block> registry) {
        GAS_IMPORT_INTERFACE = new BlockGasImportInterface();
        GAS_EXPORT_INTERFACE = new BlockGasExportInterface();

        registry.register(GAS_IMPORT_INTERFACE);
        registry.register(GAS_EXPORT_INTERFACE);

        // Register tile entities
        GameRegistry.registerTileEntity(TileGasImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_gas_interface"));
        GameRegistry.registerTileEntity(TileGasExportInterface.class,
            new ResourceLocation(Tags.MODID, "export_gas_interface"));

        // Register tile-to-item mappings for Network Tool display
        AEBaseTile.registerTileItem(TileGasImportInterface.class,
            new BlockStackSrc(GAS_IMPORT_INTERFACE, 0, ActivityState.Enabled));
        AEBaseTile.registerTileItem(TileGasExportInterface.class,
            new BlockStackSrc(GAS_EXPORT_INTERFACE, 0, ActivityState.Enabled));
    }

    /**
     * Register all gas interface items (block items and part items).
     * Called from BlockRegistry when MekanismEnergistics is loaded.
     */
    public static void registerItems(IForgeRegistry<Item> registry) {
        // Register block items
        registry.register(createItemBlock(GAS_IMPORT_INTERFACE));
        registry.register(createItemBlock(GAS_EXPORT_INTERFACE));

        // Register gas part item
        GAS_PART = new ItemGasPart();
        registry.register(GAS_PART);
    }

    /**
     * Register part models with AE2's part model registry.
     * Must be called during preInit before parts are placed.
     */
    public static void registerPartModels() {
        IPartModels partModels = AEApi.instance().registries().partModels();

        for (CellsGasPartType type : CellsGasPartType.values()) {
            partModels.registerModels(type.getModels());
        }
    }

    private static ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new AEBaseItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());
        return itemBlock;
    }

    /**
     * Register client-side models for gas blocks and parts.
     * Called from BlockRegistry when MekanismEnergistics is loaded.
     */
    @SideOnly(Side.CLIENT)
    public static void registerModels() {
        registerBlockModel(GAS_IMPORT_INTERFACE);
        registerBlockModel(GAS_EXPORT_INTERFACE);

        if (GAS_PART != null) GAS_PART.registerModels();
    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block), 0,
            new ModelResourceLocation(block.getRegistryName(), "inventory")
        );
    }
}
