package com.cells;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingCell;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingComponent;
import com.cells.cells.hyperdensity.item.ItemHyperDensityCell;
import com.cells.cells.hyperdensity.item.ItemHyperDensityComponent;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityCell;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityComponent;
import com.cells.cells.normal.compacting.ItemCompactingCell;
import com.cells.cells.normal.compacting.ItemCompactingComponent;
import com.cells.cells.normal.item.ItemNormalStorageCell;
import com.cells.cells.normal.item.ItemNormalStorageComponent;
import com.cells.cells.normal.fluid.ItemFluidNormalStorageCell;
import com.cells.cells.normal.fluid.ItemFluidNormalStorageComponent;
import com.cells.config.CellsConfig;
import com.cells.items.ItemCompressionTierCard;
import com.cells.items.ItemDecompressionTierCard;
import com.cells.items.ItemEqualDistributionCard;
import com.cells.items.ItemOverflowCard;


public class ItemRegistry {

    public static ItemCompactingCell COMPACTING_CELL;
    public static ItemCompactingComponent COMPACTING_COMPONENT;
    public static ItemHyperDensityCell HIGH_DENSITY_CELL;
    public static ItemHyperDensityComponent HIGH_DENSITY_COMPONENT;
    public static ItemHyperDensityCompactingCell HIGH_DENSITY_COMPACTING_CELL;
    public static ItemHyperDensityCompactingComponent HIGH_DENSITY_COMPACTING_COMPONENT;
    public static ItemNormalStorageCell NORMAL_STORAGE_CELL;
    public static ItemNormalStorageComponent NORMAL_STORAGE_COMPONENT;
    public static ItemFluidHyperDensityCell FLUID_HYPER_DENSITY_CELL;
    public static ItemFluidHyperDensityComponent FLUID_HYPER_DENSITY_COMPONENT;
    public static ItemFluidNormalStorageCell FLUID_NORMAL_STORAGE_CELL;
    public static ItemFluidNormalStorageComponent FLUID_NORMAL_STORAGE_COMPONENT;
    public static ItemOverflowCard OVERFLOW_CARD;
    public static ItemEqualDistributionCard EQUAL_DISTRIBUTION_CARD;
    public static ItemCompressionTierCard COMPRESSION_TIER_CARD;
    public static ItemDecompressionTierCard DECOMPRESSION_TIER_CARD;

    public static void init() {
        // Initialize items based on config
        if (CellsConfig.enableCompactingCells) {
            COMPACTING_CELL = new ItemCompactingCell();
            COMPACTING_COMPONENT = new ItemCompactingComponent();
        }

        if (CellsConfig.enableHDCells) {
            HIGH_DENSITY_CELL = new ItemHyperDensityCell();
            HIGH_DENSITY_COMPONENT = new ItemHyperDensityComponent();
        }

        if (CellsConfig.enableHDCompactingCells) {
            HIGH_DENSITY_COMPACTING_CELL = new ItemHyperDensityCompactingCell();
            HIGH_DENSITY_COMPACTING_COMPONENT = new ItemHyperDensityCompactingComponent();
        }

        if (CellsConfig.enableNormalCells) {
            NORMAL_STORAGE_CELL = new ItemNormalStorageCell();
            NORMAL_STORAGE_COMPONENT = new ItemNormalStorageComponent();
        }

        if (CellsConfig.enableFluidHDCells) {
            FLUID_HYPER_DENSITY_CELL = new ItemFluidHyperDensityCell();
            FLUID_HYPER_DENSITY_COMPONENT = new ItemFluidHyperDensityComponent();
        }

        if (CellsConfig.enableFluidNormalCells) {
            FLUID_NORMAL_STORAGE_CELL = new ItemFluidNormalStorageCell();
            FLUID_NORMAL_STORAGE_COMPONENT = new ItemFluidNormalStorageComponent();
        }

        // Upgrades are always available
        OVERFLOW_CARD = new ItemOverflowCard();
        EQUAL_DISTRIBUTION_CARD = new ItemEqualDistributionCard();
        COMPRESSION_TIER_CARD = new ItemCompressionTierCard();
        DECOMPRESSION_TIER_CARD = new ItemDecompressionTierCard();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        if (COMPACTING_CELL != null) {
            event.getRegistry().register(COMPACTING_CELL);
            event.getRegistry().register(COMPACTING_COMPONENT);
        }

        if (HIGH_DENSITY_CELL != null) {
            event.getRegistry().register(HIGH_DENSITY_CELL);
            event.getRegistry().register(HIGH_DENSITY_COMPONENT);
        }

        if (HIGH_DENSITY_COMPACTING_CELL != null) {
            event.getRegistry().register(HIGH_DENSITY_COMPACTING_CELL);
            event.getRegistry().register(HIGH_DENSITY_COMPACTING_COMPONENT);
        }

        if (NORMAL_STORAGE_CELL != null) {
            event.getRegistry().register(NORMAL_STORAGE_CELL);
            event.getRegistry().register(NORMAL_STORAGE_COMPONENT);
        }

        if (FLUID_HYPER_DENSITY_CELL != null) {
            event.getRegistry().register(FLUID_HYPER_DENSITY_CELL);
            event.getRegistry().register(FLUID_HYPER_DENSITY_COMPONENT);
        }

        if (FLUID_NORMAL_STORAGE_CELL != null) {
            event.getRegistry().register(FLUID_NORMAL_STORAGE_CELL);
            event.getRegistry().register(FLUID_NORMAL_STORAGE_COMPONENT);
        }

        event.getRegistry().register(OVERFLOW_CARD);
        event.getRegistry().register(EQUAL_DISTRIBUTION_CARD);
        event.getRegistry().register(COMPRESSION_TIER_CARD);
        event.getRegistry().register(DECOMPRESSION_TIER_CARD);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void registerModels(ModelRegistryEvent event) {
        registerModel(OVERFLOW_CARD);

        // Register equal distribution card models for each tier
        String[] equalDistTiers = ItemEqualDistributionCard.getTierNames();
        for (int i = 0; i < equalDistTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(EQUAL_DISTRIBUTION_CARD, i,
                new ModelResourceLocation(EQUAL_DISTRIBUTION_CARD.getRegistryName() + "_" + equalDistTiers[i], "inventory"));
        }

        // Register compression tier card models for each tier
        String[] compressionTiers = ItemCompressionTierCard.getTierNames();
        for (int i = 0; i < compressionTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSION_TIER_CARD, i,
                new ModelResourceLocation(COMPRESSION_TIER_CARD.getRegistryName() + "_" + compressionTiers[i], "inventory"));
        }

        // Register decompression tier card models for each tier
        String[] decompressionTiers = ItemDecompressionTierCard.getTierNames();
        for (int i = 0; i < decompressionTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(DECOMPRESSION_TIER_CARD, i,
                new ModelResourceLocation(DECOMPRESSION_TIER_CARD.getRegistryName() + "_" + decompressionTiers[i], "inventory"));
        }

        // Register compacting cell models for each tier
        if (COMPACTING_CELL != null) {
            String[] cellTiers = ItemCompactingCell.getTierNames();
            for (int i = 0; i < cellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(COMPACTING_CELL, i,
                    new ModelResourceLocation(COMPACTING_CELL.getRegistryName() + "_" + cellTiers[i], "inventory"));
            }

            String[] componentTiers = ItemCompactingComponent.getTierNames();
            for (int i = 0; i < componentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(COMPACTING_COMPONENT, i,
                    new ModelResourceLocation(COMPACTING_COMPONENT.getRegistryName() + "_" + componentTiers[i], "inventory"));
            }
        }

        // Register hyper-density cell models for each tier
        if (HIGH_DENSITY_CELL != null) {
            String[] hdCellTiers = ItemHyperDensityCell.getTierNames();
            for (int i = 0; i < hdCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_CELL, i,
                    new ModelResourceLocation(HIGH_DENSITY_CELL.getRegistryName() + "_" + hdCellTiers[i], "inventory"));
            }

            String[] hdComponentTiers = ItemHyperDensityComponent.getTierNames();
            for (int i = 0; i < hdComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPONENT, i,
                    new ModelResourceLocation(HIGH_DENSITY_COMPONENT.getRegistryName() + "_" + hdComponentTiers[i], "inventory"));
            }
        }

        // Register hyper-density compacting cell models for each tier
        if (HIGH_DENSITY_COMPACTING_CELL != null) {
            String[] hdCompactingCellTiers = ItemHyperDensityCompactingCell.getTierNames();
            for (int i = 0; i < hdCompactingCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPACTING_CELL, i,
                    new ModelResourceLocation(HIGH_DENSITY_COMPACTING_CELL.getRegistryName() + "_" + hdCompactingCellTiers[i], "inventory"));
            }

            String[] hdCompactingComponentTiers = ItemHyperDensityCompactingComponent.getTierNames();
            for (int i = 0; i < hdCompactingComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HIGH_DENSITY_COMPACTING_COMPONENT, i,
                    new ModelResourceLocation(HIGH_DENSITY_COMPACTING_COMPONENT.getRegistryName() + "_" + hdCompactingComponentTiers[i], "inventory"));
            }
        }

        // Register normal storage cell models for each tier (64M-2G)
        if (NORMAL_STORAGE_CELL != null) {
            String[] normalCellTiers = ItemNormalStorageCell.getTierNames();
            for (int i = 0; i < normalCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(NORMAL_STORAGE_CELL, i,
                    new ModelResourceLocation(NORMAL_STORAGE_CELL.getRegistryName() + "_" + normalCellTiers[i], "inventory"));
            }

            String[] normalComponentTiers = ItemNormalStorageComponent.getTierNames();
            for (int i = 0; i < normalComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(NORMAL_STORAGE_COMPONENT, i,
                    new ModelResourceLocation(NORMAL_STORAGE_COMPONENT.getRegistryName() + "_" + normalComponentTiers[i], "inventory"));
            }
        }

        // Register fluid hyper-density cell models for each tier
        if (FLUID_HYPER_DENSITY_CELL != null) {
            String[] fluidHdCellTiers = ItemFluidHyperDensityCell.getTierNames();
            for (int i = 0; i < fluidHdCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_HYPER_DENSITY_CELL, i,
                    new ModelResourceLocation(FLUID_HYPER_DENSITY_CELL.getRegistryName() + "_" + fluidHdCellTiers[i], "inventory"));
            }

            String[] fluidHdComponentTiers = ItemFluidHyperDensityComponent.getTierNames();
            for (int i = 0; i < fluidHdComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_HYPER_DENSITY_COMPONENT, i,
                    new ModelResourceLocation(FLUID_HYPER_DENSITY_COMPONENT.getRegistryName() + "_" + fluidHdComponentTiers[i], "inventory"));
            }
        }

        // Register fluid normal storage cell models for each tier (64M-2G)
        if (FLUID_NORMAL_STORAGE_CELL != null) {
            String[] fluidNormalCellTiers = ItemFluidNormalStorageCell.getTierNames();
            for (int i = 0; i < fluidNormalCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_NORMAL_STORAGE_CELL, i,
                    new ModelResourceLocation(FLUID_NORMAL_STORAGE_CELL.getRegistryName() + "_" + fluidNormalCellTiers[i], "inventory"));
            }

            String[] fluidNormalComponentTiers = ItemFluidNormalStorageComponent.getTierNames();
            for (int i = 0; i < fluidNormalComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_NORMAL_STORAGE_COMPONENT, i,
                    new ModelResourceLocation(FLUID_NORMAL_STORAGE_COMPONENT.getRegistryName() + "_" + fluidNormalComponentTiers[i], "inventory"));
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item) {
        if (item == null) return;

        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
