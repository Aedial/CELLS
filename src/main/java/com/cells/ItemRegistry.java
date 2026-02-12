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
import com.cells.config.CellsConfig;
import com.cells.items.ItemCompressedCalculationPrint;
import com.cells.items.ItemCompressedEngineeringPrint;
import com.cells.items.ItemCompressedLogicPrint;
import com.cells.items.ItemCompressedSiliconPrint;
import com.cells.items.ItemCompressionTierCard;
import com.cells.items.ItemDecompressionTierCard;
import com.cells.items.ItemEqualDistributionCard;
import com.cells.items.ItemOverclockedProcessor;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemSingularityProcessor;
import com.cells.items.ItemTrashUnselectedCard;


public class ItemRegistry {

    public static ItemCompactingCell COMPACTING_CELL;
    public static ItemCompactingComponent COMPACTING_COMPONENT;
    public static ItemHyperDensityCell HYPER_DENSITY_CELL;
    public static ItemHyperDensityComponent HYPER_DENSITY_COMPONENT;
    public static ItemHyperDensityCompactingCell HYPER_DENSITY_COMPACTING_CELL;
    public static ItemHyperDensityCompactingComponent HYPER_DENSITY_COMPACTING_COMPONENT;
    public static ItemFluidHyperDensityCell FLUID_HYPER_DENSITY_CELL;
    public static ItemFluidHyperDensityComponent FLUID_HYPER_DENSITY_COMPONENT;
    public static ItemOverflowCard OVERFLOW_CARD;
    public static ItemTrashUnselectedCard TRASH_UNSELECTED_CARD;
    public static ItemEqualDistributionCard EQUAL_DISTRIBUTION_CARD;
    public static ItemCompressionTierCard COMPRESSION_TIER_CARD;
    public static ItemDecompressionTierCard DECOMPRESSION_TIER_CARD;
    public static ItemCompressedCalculationPrint COMPRESSED_CALCULATION_PRINT;
    public static ItemCompressedEngineeringPrint COMPRESSED_ENGINEERING_PRINT;
    public static ItemCompressedLogicPrint COMPRESSED_LOGIC_PRINT;
    public static ItemCompressedSiliconPrint COMPRESSED_SILICON_PRINT;
    public static ItemOverclockedProcessor OVERCLOCKED_PROCESSOR;
    public static ItemSingularityProcessor SINGULARITY_PROCESSOR;

    public static void init() {
        // Initialize items based on config
        if (CellsConfig.enableCompactingCells) {
            COMPACTING_CELL = new ItemCompactingCell();
            COMPACTING_COMPONENT = new ItemCompactingComponent();
        }

        if (CellsConfig.enableHDCells) {
            HYPER_DENSITY_CELL = new ItemHyperDensityCell();
            HYPER_DENSITY_COMPONENT = new ItemHyperDensityComponent();
        }

        if (CellsConfig.enableHDCompactingCells) {
            HYPER_DENSITY_COMPACTING_CELL = new ItemHyperDensityCompactingCell();
            HYPER_DENSITY_COMPACTING_COMPONENT = new ItemHyperDensityCompactingComponent();
        }

        if (CellsConfig.enableFluidHDCells) {
            FLUID_HYPER_DENSITY_CELL = new ItemFluidHyperDensityCell();
            FLUID_HYPER_DENSITY_COMPONENT = new ItemFluidHyperDensityComponent();
        }

        // Upgrades are always available
        OVERFLOW_CARD = new ItemOverflowCard();
        TRASH_UNSELECTED_CARD = new ItemTrashUnselectedCard();
        EQUAL_DISTRIBUTION_CARD = new ItemEqualDistributionCard();
        COMPRESSION_TIER_CARD = new ItemCompressionTierCard();
        DECOMPRESSION_TIER_CARD = new ItemDecompressionTierCard();

        // Processor crafting materials
        COMPRESSED_CALCULATION_PRINT = new ItemCompressedCalculationPrint();
        COMPRESSED_ENGINEERING_PRINT = new ItemCompressedEngineeringPrint();
        COMPRESSED_LOGIC_PRINT = new ItemCompressedLogicPrint();
        COMPRESSED_SILICON_PRINT = new ItemCompressedSiliconPrint();
        OVERCLOCKED_PROCESSOR = new ItemOverclockedProcessor();
        SINGULARITY_PROCESSOR = new ItemSingularityProcessor();
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        if (COMPACTING_CELL != null) {
            event.getRegistry().register(COMPACTING_CELL);
            event.getRegistry().register(COMPACTING_COMPONENT);
        }

        if (HYPER_DENSITY_CELL != null) {
            event.getRegistry().register(HYPER_DENSITY_CELL);
            event.getRegistry().register(HYPER_DENSITY_COMPONENT);
        }

        if (HYPER_DENSITY_COMPACTING_CELL != null) {
            event.getRegistry().register(HYPER_DENSITY_COMPACTING_CELL);
            event.getRegistry().register(HYPER_DENSITY_COMPACTING_COMPONENT);
        }

        if (FLUID_HYPER_DENSITY_CELL != null) {
            event.getRegistry().register(FLUID_HYPER_DENSITY_CELL);
            event.getRegistry().register(FLUID_HYPER_DENSITY_COMPONENT);
        }

        event.getRegistry().register(OVERFLOW_CARD);
        event.getRegistry().register(TRASH_UNSELECTED_CARD);
        event.getRegistry().register(EQUAL_DISTRIBUTION_CARD);
        event.getRegistry().register(COMPRESSION_TIER_CARD);
        event.getRegistry().register(DECOMPRESSION_TIER_CARD);
        event.getRegistry().register(COMPRESSED_CALCULATION_PRINT);
        event.getRegistry().register(COMPRESSED_ENGINEERING_PRINT);
        event.getRegistry().register(COMPRESSED_LOGIC_PRINT);
        event.getRegistry().register(COMPRESSED_SILICON_PRINT);
        event.getRegistry().register(OVERCLOCKED_PROCESSOR);
        event.getRegistry().register(SINGULARITY_PROCESSOR);
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void registerModels(ModelRegistryEvent event) {
        // Overflow card lives under upgrades
        ModelLoader.setCustomModelResourceLocation(OVERFLOW_CARD, 0,
            makeModelLocation(OVERFLOW_CARD, "upgrades"));

        // Trash unselected card also lives under upgrades
        ModelLoader.setCustomModelResourceLocation(TRASH_UNSELECTED_CARD, 0,
            makeModelLocation(TRASH_UNSELECTED_CARD, "upgrades"));

        // Register equal distribution card models for each tier (upgrades)
        String[] equalDistTiers = ItemEqualDistributionCard.getTierNames();
        for (int i = 0; i < equalDistTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(EQUAL_DISTRIBUTION_CARD, i,
                makeModelLocation(EQUAL_DISTRIBUTION_CARD, "upgrades", "_" + equalDistTiers[i]));
        }

        // Register compression tier card models for each tier (upgrades)
        String[] compressionTiers = ItemCompressionTierCard.getTierNames();
        for (int i = 0; i < compressionTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSION_TIER_CARD, i,
                makeModelLocation(COMPRESSION_TIER_CARD, "upgrades", "_" + compressionTiers[i]));
        }

        // Register decompression tier card models for each tier (upgrades)
        String[] decompressionTiers = ItemDecompressionTierCard.getTierNames();
        for (int i = 0; i < decompressionTiers.length; i++) {
            ModelLoader.setCustomModelResourceLocation(DECOMPRESSION_TIER_CARD, i,
                makeModelLocation(DECOMPRESSION_TIER_CARD, "upgrades", "_" + decompressionTiers[i]));
        }

        // Register compressed print models for each type (processors)
        String[] calcPrintLevels = ItemCompressedCalculationPrint.getLevelNames();
        for (int i = 0; i < calcPrintLevels.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSED_CALCULATION_PRINT, i,
                makeModelLocation(COMPRESSED_CALCULATION_PRINT, "processors", "_" + calcPrintLevels[i]));
        }

        String[] engPrintLevels = ItemCompressedEngineeringPrint.getLevelNames();
        for (int i = 0; i < engPrintLevels.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSED_ENGINEERING_PRINT, i,
                makeModelLocation(COMPRESSED_ENGINEERING_PRINT, "processors", "_" + engPrintLevels[i]));
        }

        String[] logicPrintLevels = ItemCompressedLogicPrint.getLevelNames();
        for (int i = 0; i < logicPrintLevels.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSED_LOGIC_PRINT, i,
                makeModelLocation(COMPRESSED_LOGIC_PRINT, "processors", "_" + logicPrintLevels[i]));
        }

        String[] siliconPrintLevels = ItemCompressedSiliconPrint.getLevelNames();
        for (int i = 0; i < siliconPrintLevels.length; i++) {
            ModelLoader.setCustomModelResourceLocation(COMPRESSED_SILICON_PRINT, i,
                makeModelLocation(COMPRESSED_SILICON_PRINT, "processors", "_" + siliconPrintLevels[i]));
        }

        // Register overclocked processor models for each type (processors)
        String[] overclockedTypes = ItemOverclockedProcessor.getTypeNames();
        for (int i = 0; i < overclockedTypes.length; i++) {
            ModelLoader.setCustomModelResourceLocation(OVERCLOCKED_PROCESSOR, i,
                makeModelLocation(OVERCLOCKED_PROCESSOR, "processors", "_" + overclockedTypes[i]));
        }

        // Register singularity processor models for each type (processors)
        String[] singularityTypes = ItemSingularityProcessor.getTypeNames();
        for (int i = 0; i < singularityTypes.length; i++) {
            ModelLoader.setCustomModelResourceLocation(SINGULARITY_PROCESSOR, i,
                makeModelLocation(SINGULARITY_PROCESSOR, "processors", "_" + singularityTypes[i]));
        }

        // Register compacting cell models for each tier (cells/compacting)
        if (COMPACTING_CELL != null) {
            String[] cellTiers = ItemCompactingCell.getTierNames();
            for (int i = 0; i < cellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(COMPACTING_CELL, i,
                    makeModelLocation(COMPACTING_CELL, "cells/compacting", "_" + cellTiers[i]));
            }

            String[] componentTiers = ItemCompactingComponent.getTierNames();
            for (int i = 0; i < componentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(COMPACTING_COMPONENT, i,
                    makeModelLocation(COMPACTING_COMPONENT, "cells/compacting", "_" + componentTiers[i]));
            }
        }

        // Register hyper-density cell models for each tier (cells/hyper_density)
        if (HYPER_DENSITY_CELL != null) {
            String[] hdCellTiers = ItemHyperDensityCell.getTierNames();
            for (int i = 0; i < hdCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HYPER_DENSITY_CELL, i,
                    makeModelLocation(HYPER_DENSITY_CELL, "cells/hyper_density", "_" + hdCellTiers[i]));
            }

            String[] hdComponentTiers = ItemHyperDensityComponent.getTierNames();
            for (int i = 0; i < hdComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HYPER_DENSITY_COMPONENT, i,
                    makeModelLocation(HYPER_DENSITY_COMPONENT, "cells/hyper_density", "_" + hdComponentTiers[i]));
            }
        }

        // Register hyper-density compacting cell models for each tier (cells/hyper_density_compacting)
        if (HYPER_DENSITY_COMPACTING_CELL != null) {
            String[] hdCompactingCellTiers = ItemHyperDensityCompactingCell.getTierNames();
            for (int i = 0; i < hdCompactingCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HYPER_DENSITY_COMPACTING_CELL, i,
                    makeModelLocation(HYPER_DENSITY_COMPACTING_CELL, "cells/hyper_density_compacting", "_" + hdCompactingCellTiers[i]));
            }

            String[] hdCompactingComponentTiers = ItemHyperDensityCompactingComponent.getTierNames();
            for (int i = 0; i < hdCompactingComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(HYPER_DENSITY_COMPACTING_COMPONENT, i,
                    makeModelLocation(HYPER_DENSITY_COMPACTING_COMPONENT, "cells/hyper_density_compacting", "_" + hdCompactingComponentTiers[i]));
            }
        }

        // Register fluid hyper-density cell models for each tier (cells/hyper_density)
        if (FLUID_HYPER_DENSITY_CELL != null) {
            String[] fluidHdCellTiers = ItemFluidHyperDensityCell.getTierNames();
            for (int i = 0; i < fluidHdCellTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_HYPER_DENSITY_CELL, i,
                    makeModelLocation(FLUID_HYPER_DENSITY_CELL, "cells/hyper_density_fluid", "_" + fluidHdCellTiers[i]));
            }

            String[] fluidHdComponentTiers = ItemFluidHyperDensityComponent.getTierNames();
            for (int i = 0; i < fluidHdComponentTiers.length; i++) {
                ModelLoader.setCustomModelResourceLocation(FLUID_HYPER_DENSITY_COMPONENT, i,
                    makeModelLocation(FLUID_HYPER_DENSITY_COMPONENT, "cells/hyper_density_fluid", "_" + fluidHdComponentTiers[i]));
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation makeModelLocation(Item item, String folder, String suffix) {
        String reg = item.getRegistryName().toString();
        int idx = reg.indexOf(':');
        String prefix = reg.substring(0, idx + 1);
        String path = reg.substring(idx + 1);
        String suf = (suffix == null) ? "" : suffix;

        return new ModelResourceLocation(prefix + folder + "/" + path + suf, "inventory");
    }

    private static ModelResourceLocation makeModelLocation(Item item, String folder) {
        return makeModelLocation(item, folder, null);
    }

    @SideOnly(Side.CLIENT)
    private static void registerModel(Item item) {
        if (item == null) return;

        String folder;
        if (item instanceof ItemCompactingCell || item instanceof ItemCompactingComponent) {
            folder = "cells/compacting";
        } else if (item instanceof ItemHyperDensityCell || item instanceof ItemHyperDensityComponent) {
            folder = "cells/hyper_density";
        } else if (item instanceof ItemFluidHyperDensityCell || item instanceof ItemFluidHyperDensityComponent) {
            folder = "cells/hyper_density_fluid";
        } else if (item instanceof ItemHyperDensityCompactingCell || item instanceof ItemHyperDensityCompactingComponent) {
            folder = "cells/hyper_density_compacting";
        } else if (item instanceof ItemOverflowCard || item instanceof ItemTrashUnselectedCard
            || item instanceof ItemEqualDistributionCard
            || item instanceof ItemCompressionTierCard || item instanceof ItemDecompressionTierCard) {
            folder = "upgrades";
        } else {
            folder = "processors";
        }

        ModelLoader.setCustomModelResourceLocation(item, 0, makeModelLocation(item, folder));
    }
}
