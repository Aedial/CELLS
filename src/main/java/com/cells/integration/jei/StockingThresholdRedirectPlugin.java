package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;

import com.cells.ItemRegistry;


/**
 * JEI plugin that redirects higher-tier Stocking Threshold Card lookups to the
 * 1x crafting recipe.
 * <p>
 * Only the 1x card (metadata 0) has a crafting recipe, the other multipliers
 * are reached by cycling the card. Looking up the recipe for any other variant
 * would otherwise show nothing, so this plugin surfaces the base recipe instead.
 */
public class StockingThresholdRedirectPlugin implements IRecipeRegistryPlugin {

    private List<IRecipe> baseRecipes;

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (!isHigherTierCard(focus)) return Collections.emptyList();

        return getBaseRecipes().isEmpty()
            ? Collections.emptyList()
            : Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!VanillaRecipeCategoryUid.CRAFTING.equals(recipeCategory.getUid())) return Collections.emptyList();
        if (!isHigherTierCard(focus)) return Collections.emptyList();

        List<T> wrappers = new ArrayList<>();
        for (IRecipe recipe : getBaseRecipes()) wrappers.add((T) new StockingThresholdRecipeWrapper(recipe));

        return wrappers;
    }

    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        // The base recipe is already listed for the 1x card, so browsing the
        // category as a whole should not duplicate it here.
        return Collections.emptyList();
    }

    /**
     * Tells whether the focus targets a Stocking Threshold Card output above
     * the first tier, which is the case that needs redirecting.
     */
    private boolean isHigherTierCard(IFocus<?> focus) {
        if (ItemRegistry.STOCKING_THRESHOLD_CARD == null) return false;
        if (focus.getMode() != IFocus.Mode.OUTPUT) return false;
        if (!(focus.getValue() instanceof ItemStack)) return false;

        ItemStack stack = (ItemStack) focus.getValue();
        return stack.getItem() == ItemRegistry.STOCKING_THRESHOLD_CARD && stack.getMetadata() != 0;
    }

    /**
     * Collects the crafting recipes that produce the 1x card, resolved once on
     * first use since the recipe registry is fixed after load.
     */
    private List<IRecipe> getBaseRecipes() {
        if (baseRecipes != null) return baseRecipes;

        baseRecipes = new ArrayList<>();
        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            ItemStack output = recipe.getRecipeOutput();
            if (output.getItem() == ItemRegistry.STOCKING_THRESHOLD_CARD && output.getMetadata() == 0) {
                baseRecipes.add(recipe);
            }
        }

        return baseRecipes;
    }
}
