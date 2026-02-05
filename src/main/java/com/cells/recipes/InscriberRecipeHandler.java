package com.cells.recipes;

import java.util.Collections;
import java.util.List;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import appeng.api.features.IInscriberRecipeBuilder;
import appeng.api.features.IInscriberRegistry;
import appeng.api.features.InscriberProcessType;

import com.cells.Cells;
import com.cells.ItemRegistry;
import com.cells.items.ItemCompressedCalculationPrint;
import com.cells.items.ItemCompressedEngineeringPrint;
import com.cells.items.ItemCompressedLogicPrint;
import com.cells.items.ItemCompressedSiliconPrint;
import com.cells.items.ItemOverclockedProcessor;
import com.cells.items.ItemSingularityProcessor;


/**
 * Registers all custom inscriber recipes for CELLS.
 *
 * Recipe chains:
 * 1. Compressed prints (using nether star for processors, redstone for silicon):
 *    - 8x base print + catalyst → Compressed print (8x base, 1x catalyst)
 *    - 8x Compressed + catalyst → Double Compressed (64x base, 9x catalyst)
 *    - 8x Double Compressed + catalyst → Triple Compressed (512x base, 73x catalyst)
 *    - 8x Triple Compressed + catalyst → Quadruple Compressed (4096x base, 585x catalyst)
 *
 * 2. Overclocked Processors (for Compacting components):
 *    - Compressed processor print (top) + Matter Ball (middle) + Compressed silicon print (bottom)
 *
 * 3. Singularity Processors (for Hyper-Density components):
 *    - Quadruple compressed processor print (top) + Singularity (middle) + Quadruple compressed silicon print (bottom)
 */
public class InscriberRecipeHandler {

    /**
     * Register all inscriber recipes. Called during init phase.
     */
    public static void registerRecipes() {
        final IInscriberRegistry registry = AEApi.instance().registries().inscriber();
        final IMaterials materials = AEApi.instance().definitions().materials();

        // Get base prints from AE2
        ItemStack calcPrint = materials.calcProcessorPrint().maybeStack(1).orElse(ItemStack.EMPTY);
        ItemStack engPrint = materials.engProcessorPrint().maybeStack(1).orElse(ItemStack.EMPTY);
        ItemStack logicPrint = materials.logicProcessorPrint().maybeStack(1).orElse(ItemStack.EMPTY);
        ItemStack siliconPrint = materials.siliconPrint().maybeStack(1).orElse(ItemStack.EMPTY);

        // Get catalysts
        ItemStack matterBall = materials.matterBall().maybeStack(1).orElse(ItemStack.EMPTY);
        ItemStack singularity = materials.singularity().maybeStack(1).orElse(ItemStack.EMPTY);

        // Register Overclocked Processors (compressed prints + matter ball)
        if (!matterBall.isEmpty()) registerOverclockedProcessors(registry, matterBall);

        // Register Singularity Processors (quadruple compressed prints + singularity)
        if (!singularity.isEmpty()) registerSingularityProcessors(registry, singularity);
    }

    /**
     * Register Overclocked Processor recipes.
     * Uses compressed (1x) prints + matter ball.
     */
    private static void registerOverclockedProcessors(IInscriberRegistry registry, ItemStack matterBall) {
        ItemStack compressedSiliconPrint = ItemCompressedSiliconPrint.create(ItemCompressedSiliconPrint.COMPRESSED);

        // Overclocked Calculation Processor
        ItemStack compressedCalcPrint = ItemCompressedCalculationPrint.create(ItemCompressedCalculationPrint.COMPRESSED);
        ItemStack overclockedCalc = ItemOverclockedProcessor.create(ItemOverclockedProcessor.CALCULATION);
        if (!registerProcessorRecipe(registry, overclockedCalc, matterBall, compressedCalcPrint, compressedSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", compressedCalcPrint, matterBall, compressedSiliconPrint, overclockedCalc);
        }

        // Overclocked Engineering Processor
        ItemStack compressedEngPrint = ItemCompressedEngineeringPrint.create(ItemCompressedEngineeringPrint.COMPRESSED);
        ItemStack overclockedEng = ItemOverclockedProcessor.create(ItemOverclockedProcessor.ENGINEERING);
        if (!registerProcessorRecipe(registry, overclockedEng, matterBall, compressedEngPrint, compressedSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", compressedEngPrint, matterBall, compressedSiliconPrint, overclockedEng);
        }

        // Overclocked Logic Processor
        ItemStack compressedLogicPrint = ItemCompressedLogicPrint.create(ItemCompressedLogicPrint.COMPRESSED);
        ItemStack overclockedLogic = ItemOverclockedProcessor.create(ItemOverclockedProcessor.LOGIC);
        if (!registerProcessorRecipe(registry, overclockedLogic, matterBall, compressedLogicPrint, compressedSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", compressedLogicPrint, matterBall, compressedSiliconPrint, overclockedLogic);
        }
    }

    /**
     * Register Singularity Processor recipes.
     * Uses quadruple compressed (4096x) prints + singularity.
     */
    private static void registerSingularityProcessors(IInscriberRegistry registry, ItemStack singularity) {
        ItemStack quadSiliconPrint = ItemCompressedSiliconPrint.create(ItemCompressedSiliconPrint.QUADRUPLE_COMPRESSED);

        // Singularity Calculation Processor
        ItemStack quadCalcPrint = ItemCompressedCalculationPrint.create(ItemCompressedCalculationPrint.QUADRUPLE_COMPRESSED);
        ItemStack singularityCalc = ItemSingularityProcessor.create(ItemSingularityProcessor.CALCULATION);
        if (!registerProcessorRecipe(registry, singularityCalc, singularity, quadCalcPrint, quadSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", quadCalcPrint, singularity, quadSiliconPrint, singularityCalc);
        }

        // Singularity Engineering Processor
        ItemStack quadEngPrint = ItemCompressedEngineeringPrint.create(ItemCompressedEngineeringPrint.QUADRUPLE_COMPRESSED);
        ItemStack singularityEng = ItemSingularityProcessor.create(ItemSingularityProcessor.ENGINEERING);
        if (!registerProcessorRecipe(registry, singularityEng, singularity, quadEngPrint, quadSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", quadEngPrint, singularity, quadSiliconPrint, singularityEng);
        }

        // Singularity Logic Processor
        ItemStack quadLogicPrint = ItemCompressedLogicPrint.create(ItemCompressedLogicPrint.QUADRUPLE_COMPRESSED);
        ItemStack singularityLogic = ItemSingularityProcessor.create(ItemSingularityProcessor.LOGIC);
        if (!registerProcessorRecipe(registry, singularityLogic, singularity, quadLogicPrint, quadSiliconPrint)) {
            Cells.LOGGER.warn("Failed to register inscriber recipe {} + {} + {} -> {}", quadLogicPrint, singularity, quadSiliconPrint, singularityLogic);
        }
    }

    /**
     * Helper to register a processor inscriber recipe (top print + middle catalyst + bottom silicon).
     * Returns false if any required stack is empty/null.
     */
    private static boolean registerProcessorRecipe(IInscriberRegistry registry, ItemStack output, ItemStack middle, ItemStack topPrint, ItemStack bottomPrint) {
        if (output == null || output.isEmpty() || middle == null || middle.isEmpty()) return false;
        if (topPrint == null || topPrint.isEmpty() || bottomPrint == null || bottomPrint.isEmpty()) return false;

        IInscriberRecipeBuilder builder = registry.builder();
        builder.withOutput(output);
        builder.withProcessType(InscriberProcessType.PRESS);
        builder.withInputs(Collections.singletonList(middle));
        builder.withTopOptional(Collections.singletonList(topPrint));
        builder.withBottomOptional(Collections.singletonList(bottomPrint));
        registry.addRecipe(builder.build());

        return true;
    }
}
