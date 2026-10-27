package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;


/**
 * JEI crafting wrapper that mirrors the 1x Stocking Threshold Card recipe.
 * <p>
 * Higher-tier cards share the same crafting recipe as the 1x card, so looking
 * up any variant redirects here to display the base recipe.
 */
public class StockingThresholdRecipeWrapper implements ICraftingRecipeWrapper {

    private final IRecipe recipe;

    public StockingThresholdRecipeWrapper(IRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();
        List<List<ItemStack>> inputs = new ArrayList<>(recipeIngredients.size());
        for (Ingredient ingredient : recipeIngredients) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack stack : ingredient.getMatchingStacks()) stacks.add(stack.copy());
            inputs.add(stacks);
        }

        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, recipe.getRecipeOutput().copy());
    }

    @Override
    @Nullable
    public ResourceLocation getRegistryName() {
        return recipe.getRegistryName();
    }
}
