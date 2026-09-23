package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;


/**
 * Finds CELLS cells and upgrades that can be disassembled through shift-right-click.
 */
public class CellDisassemblyRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();
        if (!CellJeiHelper.canShowDisassembly(stack)) return Collections.emptyList();

        return Collections.singletonList(CellOperationCategory.DISASSEMBLY_UID);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!CellOperationCategory.DISASSEMBLY_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();
        if (!CellJeiHelper.canShowDisassembly(stack)) return Collections.emptyList();

        CellOperationRecipe recipe = createRecipe(stack);

        setMatchingRecipes(recipeCategory, Collections.singletonList(recipe));
        return Collections.singletonList((T) recipe);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        if (!CellOperationCategory.DISASSEMBLY_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        List<CellOperationRecipe> recipes = new ArrayList<>();
        for (ItemStack stack : CellJeiHelper.getAllDisassemblyItems()) {
            if (!CellJeiHelper.canDisassemble(stack)) continue;

            recipes.add(createRecipe(stack));
        }

        setMatchingRecipes(recipeCategory, recipes);
        return (List<T>) recipes;
    }

    private static void setMatchingRecipes(IRecipeCategory<?> recipeCategory,
                                           List<CellOperationRecipe> recipes) {
        if (recipeCategory instanceof CellOperationCategory) {
            ((CellOperationCategory) recipeCategory).setMatchingRecipes(recipes);
        }
    }

    private static CellOperationRecipe createRecipe(ItemStack stack) {
        return new CellOperationRecipe(Collections.singletonList(singleCopy(stack)),
            CellJeiHelper.getDisassemblyOutputs(stack));
    }

    private static ItemStack singleCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
