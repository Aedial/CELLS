package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.recipes.CellComponentSwapRecipe;
import com.cells.recipes.CellComponentSwapRecipe.CellComponentSwapResult;


/**
 * Finds the component swaps that the cell-component recipe accepts.
 */
public class CellUpgradeRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        List<CellOperationRecipe> recipes = createRecipes((ItemStack) focus.getValue());
        if (recipes.isEmpty()) return Collections.emptyList();

        return Collections.singletonList(CellOperationCategory.UPGRADE_UID);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!CellOperationCategory.UPGRADE_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        return (List<T>) createRecipes((ItemStack) focus.getValue());
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        if (!CellOperationCategory.UPGRADE_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        List<CellOperationRecipe> recipes = new ArrayList<>();
        for (ItemStack stack : CellJeiHelper.getAllDisassemblyCells()) recipes.addAll(createRecipes(stack));

        return (List<T>) recipes;
    }

    private static List<CellOperationRecipe> createRecipes(ItemStack stack) {
        List<CellOperationRecipe> recipes = new ArrayList<>();
        CellComponentSwapRecipe swapRecipe = new CellComponentSwapRecipe();

        for (ItemStack component : CellJeiHelper.getSwapComponents(stack)) {
            CellComponentSwapResult result = swapRecipe.getSwapResult(stack, component);
            if (result == null) continue;
            if (!shouldShowSwap(stack, component, result)) continue;

            recipes.add(new CellOperationRecipe(
                Arrays.asList(singleCopy(stack), singleCopy(component)),
                Arrays.asList(result.getResult(), result.getOldComponent())));
        }

        return recipes;
    }

    private static ItemStack singleCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean shouldShowSwap(ItemStack sourceCell, ItemStack newComponent,
                                          CellComponentSwapResult result) {
        if (sourceCell.getItem() != result.getResult().getItem()) return false;

        ComponentInfo sourceInfo = ComponentHelper.getComponentInfo(result.getOldComponent());
        if (sourceInfo == null) return true;

        ComponentInfo targetInfo = ComponentHelper.getComponentInfo(newComponent);
        return targetInfo != null && sourceInfo.getChannelType() == targetInfo.getChannelType();
    }
}
