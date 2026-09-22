package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;


/**
 * JEI data for a cell action with any number of item inputs and outputs.
 */
public class CellOperationRecipe implements IRecipeWrapper {

    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;

    public CellOperationRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {
        this.inputs = copyStacks(inputs);
        this.outputs = copyStacks(outputs);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputLists = new ArrayList<>();
        for (ItemStack input : inputs) inputLists.add(Collections.singletonList(input));

        ingredients.setInputLists(VanillaTypes.ITEM, inputLists);
        ingredients.setOutputs(VanillaTypes.ITEM, outputs);
    }

    public List<ItemStack> getInputs() {
        return inputs;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) copies.add(stack.copy());
        }

        return Collections.unmodifiableList(copies);
    }
}
