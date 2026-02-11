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

import com.cells.blocks.importinterface.BlockImportInterface;
import com.cells.blocks.importinterface.TileImportInterface;


public class BlockRegistry {

    public static BlockImportInterface IMPORT_INTERFACE;

    public static void init() {
        IMPORT_INTERFACE = new BlockImportInterface();
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(IMPORT_INTERFACE);

        // Register tile entities
        GameRegistry.registerTileEntity(TileImportInterface.class,
            new ResourceLocation(Tags.MODID, "import_interface"));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(createItemBlock(IMPORT_INTERFACE));
    }

    private ItemBlock createItemBlock(Block block) {
        ItemBlock itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(block.getRegistryName());

        return itemBlock;
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void registerModels(ModelRegistryEvent event) {
        registerBlockModel(IMPORT_INTERFACE);
    }

    @SideOnly(Side.CLIENT)
    private void registerBlockModel(Block block) {
        ModelLoader.setCustomModelResourceLocation(
            Item.getItemFromBlock(block), 0,
            new ModelResourceLocation(block.getRegistryName(), "inventory")
        );
    }
}
