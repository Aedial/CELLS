package com.cells.integration.jei;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IBookmarkOverlay;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;

import com.cells.ItemRegistry;
import com.cells.config.CellsConfig;
import com.cells.integration.jei.cellview.CellViewCategory;
import com.cells.integration.jei.cellview.CellViewRegistryPlugin;


/**
 * JEI integration plugin for CELLS mod.
 * <p>
 * Registers dynamic recipe plugins for:
 * - Configurable cell assembly (empty cell + component = filled cell)
 * <p>
 * Also provides ingredient lookup for quick-add functionality.
 */
@JEIPlugin
public class CellsJEIPlugin implements IModPlugin {

    private static IJeiRuntime jeiRuntime = null;

    // TODO: add config
    /** Config flag for enabling cell view feature */
    public static boolean enableCellView = true;

    @Override
    public void registerCategories(@Nonnull IRecipeCategoryRegistration registry) {
        // Register cell view category
        if (enableCellView) {
            registry.addRecipeCategories(new CellViewCategory(registry.getJeiHelpers()));
        }
    }

    @Override
    public void register(@Nonnull IModRegistry registry) {
        // Register configurable cell assembly recipe plugin
        if (CellsConfig.enableConfigurableCells && ItemRegistry.CONFIGURABLE_CELL != null) {
            registry.addRecipeRegistryPlugin(new ConfigurableCellRegistryPlugin());
        }

        // Register cell view feature
        if (enableCellView) registry.addRecipeRegistryPlugin(new CellViewRegistryPlugin());
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    /**
     * Get the ItemStack ingredient under the mouse cursor from JEI overlays.
     *
     * @return The ItemStack under the cursor, or EMPTY if none found
     */
    @Nullable
    public static ItemStack getItemIngredientUnderMouse() {
        if (jeiRuntime == null) return null;

        // Check ingredient list
        IIngredientListOverlay ingredientList = jeiRuntime.getIngredientListOverlay();
        Object ingredient = ingredientList.getIngredientUnderMouse();

        if (ingredient != null) {
            ItemStack result = convertToItemStack(ingredient);
            if (result != null) return result;
        }

        // Check bookmarks
        IBookmarkOverlay bookmarks = jeiRuntime.getBookmarkOverlay();
        ingredient = bookmarks.getIngredientUnderMouse();

        if (ingredient != null) {
            ItemStack result = convertToItemStack(ingredient);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Get the FluidStack ingredient under the mouse cursor from JEI overlays.
     *
     * @return The FluidStack under the cursor, or null if none found
     */
    @Nullable
    public static FluidStack getFluidIngredientUnderMouse() {
        if (jeiRuntime == null) return null;

        // Check ingredient list
        IIngredientListOverlay ingredientList = jeiRuntime.getIngredientListOverlay();
        Object ingredient = ingredientList.getIngredientUnderMouse();

        if (ingredient != null) {
            FluidStack result = convertToFluidStack(ingredient);
            if (result != null) return result;
        }

        // Check bookmarks
        IBookmarkOverlay bookmarks = jeiRuntime.getBookmarkOverlay();
        ingredient = bookmarks.getIngredientUnderMouse();

        if (ingredient != null) {
            FluidStack result = convertToFluidStack(ingredient);
            if (result != null) return result;
        }

        return null;
    }

    @Nullable
    private static ItemStack convertToItemStack(Object ingredient) {
        if (ingredient instanceof ItemStack) return ((ItemStack) ingredient).copy();

        return null;
    }

    @Nullable
    private static FluidStack convertToFluidStack(Object ingredient) {
        if (ingredient instanceof FluidStack) return ((FluidStack) ingredient).copy();

        // Try to extract fluid from item
        if (ingredient instanceof ItemStack) {
            return FluidUtil.getFluidContained((ItemStack) ingredient);
        }

        return null;
    }
}
