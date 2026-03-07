package com.cells.integration.jei.cellview;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;


/**
 * JEI registry plugin for the Cell View feature.
 * <p>
 * Enables viewing cell contents when:
 * - Looking up a CELLS mod cell (R key or equivalent)
 * - The cell is registered with AE2's cell handler system
 * <p>
 * Only shows for CELLS mod cells (not vanilla AE2 or other addon cells),
 * as to not conflict with NAE2's own view.
 */
@SideOnly(Side.CLIENT)
public class CellViewRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();

        // Only show for CELLS mod cells, not vanilla AE2 or other addon cells
        if (!CellViewHelper.isCellsModCell(stack)) return Collections.emptyList();

        // Also verify it's a valid cell with inventory
        if (!CellViewHelper.isCell(stack)) return Collections.emptyList();

        // Show for both INPUT (R key) and OUTPUT (U key) lookups
        return Collections.singletonList(CellViewCategory.UID);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!(recipeCategory instanceof CellViewCategory)) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();

        // Only show for CELLS mod cells
        if (!CellViewHelper.isCellsModCell(stack)) return Collections.emptyList();
        if (!CellViewHelper.isCell(stack)) return Collections.emptyList();

        // Create a recipe wrapper for this specific cell
        return Collections.singletonList((T) new CellViewRecipe(stack));
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        // Return a placeholder recipe to make the category visible in JEI's internal checks.
        if (recipeCategory instanceof CellViewCategory) {
            return Collections.singletonList((T) new CellViewRecipe(ItemStack.EMPTY));
        }

        return Collections.emptyList();
    }
}
