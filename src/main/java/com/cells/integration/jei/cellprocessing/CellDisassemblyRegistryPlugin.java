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
 * Finds CELLS cells that can be disassembled through shift-right-click.
 */
public class CellDisassemblyRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();
        if (!CellJeiHelper.canDisassemble(stack)) return Collections.emptyList();

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

        CellOperationRecipe recipe = createRecipe((ItemStack) focus.getValue());
        if (recipe == null) return Collections.emptyList();

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
        for (ItemStack stack : CellJeiHelper.getAllDisassemblyCells()) {
            CellOperationRecipe recipe = createRecipe(stack);
            if (recipe != null) recipes.add(recipe);
        }

        return (List<T>) recipes;
    }

    private static CellOperationRecipe createRecipe(ItemStack stack) {
        if (!CellJeiHelper.canDisassemble(stack)) return null;

        return new CellOperationRecipe(Collections.singletonList(singleCopy(stack)),
            CellJeiHelper.getDisassemblyOutputs(stack));
    }

    private static ItemStack singleCopy(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }
}
