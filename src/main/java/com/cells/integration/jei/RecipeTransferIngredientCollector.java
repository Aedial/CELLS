package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;

import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayout;

import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Shared JEI recipe ingredient extraction for CELLS transfer handlers.
 * <p>
 * Produces CELLS/AE filter resources, already normalized to a stack size of 1,
 * so all transfer paths can reuse the same duplicate detection logic.
 */
final class RecipeTransferIngredientCollector {

    public enum RecipeComponentSelection {
        INPUTS,
        OUTPUTS,
        BOTH
    }

    @FunctionalInterface
    private interface IngredientConverter {
        @Nullable
        Object convert(@Nullable Object ingredient);
    }

    private RecipeTransferIngredientCollector() {
    }

    @Nonnull
    static List<Object> collect(
            IRecipeLayout recipeLayout,
            ResourceType type,
            RecipeComponentSelection selection) {
        List<Object> resources = new ArrayList<>();

        switch (type) {
            case ITEM:
                collectItemIngredients(recipeLayout, selection, resources);
                break;
            case FLUID:
                collectFluidIngredients(recipeLayout, selection, resources);
                break;
            case GAS:
                collectGasIngredients(recipeLayout, selection, resources);
                break;
            case ESSENTIA:
                collectEssentiaIngredients(recipeLayout, selection, resources);
                break;
            default:
                break;
        }

        return resources;
    }

    private static void collectItemIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients =
            recipeLayout.getItemStacks().getGuiIngredients();

        for (IGuiIngredient<ItemStack> ingredient : ingredients.values()) {
            if (!shouldInclude(ingredient, selection)) continue;

            ItemStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) continue;

            IAEItemStack aeItem = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class)
                .createStack(displayed.copy());
            if (aeItem == null) continue;

            aeItem.setStackSize(1);
            resources.add(aeItem);
        }
    }

    private static void collectFluidIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        IGuiFluidStackGroup fluidGroup = recipeLayout.getFluidStacks();
        Map<Integer, ? extends IGuiIngredient<FluidStack>> ingredients = fluidGroup.getGuiIngredients();

        for (IGuiIngredient<FluidStack> ingredient : ingredients.values()) {
            if (!shouldInclude(ingredient, selection)) continue;

            FluidStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null) continue;

            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(displayed.copy());
            if (aeFluid == null) continue;

            aeFluid.setStackSize(1);
            resources.add(aeFluid);
        }
    }

    private static void collectGasIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        if (!ResourceType.GAS.isAvailable()) return;

        collectGasStackIngredients(recipeLayout, selection, resources);
    }

    @Optional.Method(modid = "mekeng")
    private static void collectGasStackIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        collectCustomIngredients(recipeLayout, mekanism.api.gas.GasStack.class, selection, resources,
            RecipeTransferIngredientCollector::convertGasIngredient);
    }

    private static void collectEssentiaIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        if (!ResourceType.ESSENTIA.isAvailable()) return;

        collectAspectIngredients(recipeLayout, selection, resources);
        collectEssentiaStackIngredients(recipeLayout, selection, resources);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static void collectAspectIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        collectCustomIngredients(recipeLayout, thaumcraft.api.aspects.Aspect.class, selection, resources,
            RecipeTransferIngredientCollector::convertEssentiaIngredient);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static void collectEssentiaStackIngredients(
            IRecipeLayout recipeLayout,
            RecipeComponentSelection selection,
            List<Object> resources) {
        collectCustomIngredients(recipeLayout, thaumicenergistics.api.EssentiaStack.class, selection, resources,
            RecipeTransferIngredientCollector::convertEssentiaIngredient);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void collectCustomIngredients(
            IRecipeLayout recipeLayout,
            Class<?> ingredientClass,
            RecipeComponentSelection selection,
            List<Object> resources,
            IngredientConverter converter) {
        IGuiIngredientGroup ingredientGroup;

        try {
            ingredientGroup = recipeLayout.getIngredientsGroup((Class) ingredientClass);
        } catch (IllegalArgumentException e) {
            return;
        }

        Map<Integer, ? extends IGuiIngredient> ingredients = ingredientGroup.getGuiIngredients();

        for (IGuiIngredient ingredient : ingredients.values()) {
            if (!shouldInclude(ingredient, selection)) continue;

            Object displayed = ingredient.getDisplayedIngredient();
            Object converted = converter.convert(displayed);
            if (converted != null) resources.add(converted);
        }
    }

    private static boolean shouldInclude(IGuiIngredient<?> ingredient, RecipeComponentSelection selection) {
        switch (selection) {
            case OUTPUTS:
                return !ingredient.isInput();
            case BOTH:
                return true;
            case INPUTS:
            default:
                return ingredient.isInput();
        }
    }

    @Optional.Method(modid = "mekeng")
    @Nullable
    private static Object convertGasIngredient(@Nullable Object ingredient) {
        mekanism.api.gas.GasStack gas = QuickAddHelper.toGasStack(ingredient);
        if (gas == null || gas.amount <= 0) return null;

        com.mekeng.github.common.me.data.IAEGasStack aeGas =
            com.mekeng.github.common.me.data.impl.AEGasStack.of(gas.copy());
        if (aeGas == null) return null;

        aeGas.setStackSize(1);
        return aeGas;
    }

    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    private static Object convertEssentiaIngredient(@Nullable Object ingredient) {
        thaumicenergistics.api.EssentiaStack essentia = QuickAddHelper.toEssentiaStack(ingredient);
        if (essentia == null || essentia.getAmount() <= 0) return null;

        thaumicenergistics.api.storage.IEssentiaStorageChannel channel =
            AEApi.instance().storage().getStorageChannel(
                thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        thaumicenergistics.api.storage.IAEEssentiaStack aeEssentia = channel.createStack(essentia);
        if (aeEssentia == null) return null;

        aeEssentia.setStackSize(1);
        return aeEssentia;
    }
}