package com.cells.integration.jei;

import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.wrapper.IShapedCraftingRecipeWrapper;


/**
 * JEI recipe wrapper for a single configurable cell assembly variant.
 * <p>
 * Displays: empty housing + specific component = filled cell with that component.
 */
public class ConfigurableCellRecipeWrapper implements IShapedCraftingRecipeWrapper {

    private final ItemStack housing;
    private final ItemStack component;
    private final ItemStack output;

    public ConfigurableCellRecipeWrapper(ItemStack housing, ItemStack component, ItemStack output) {
        this.housing = housing;
        this.component = component;
        this.output = output;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputs = ImmutableList.of(
            ImmutableList.of(housing),
            ImmutableList.of(component)
        );
        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, output);
    }

    @Override
    public int getWidth() {
        return 2;
    }

    @Override
    public int getHeight() {
        return 1;
    }
}
