package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;

import com.cells.ItemRegistry;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;


/**
 * JEI plugin that dynamically generates configurable cell assembly recipes.
 * <p>
 * For each valid component in the whitelist, generates a recipe showing:
 * empty cell + component = filled cell with that component.
 * <p>
 * This allows JEI to properly display all variants with correct outputs,
 * and enables lookup by component (what can I make with this component?)
 * or by filled cell (how do I make this cell?).
 */
public class ConfigurableCellRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        if (ItemRegistry.CONFIGURABLE_CELL == null) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack itemStack = (ItemStack) focus.getValue();

        if (focus.getMode() == IFocus.Mode.OUTPUT) {
            // Looking up how to craft a filled configurable cell
            if (itemStack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
                ItemStack installedComponent = ComponentHelper.getInstalledComponent(itemStack);

                // Only show recipe if cell has a component installed
                if (!installedComponent.isEmpty()) {
                    return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
                }
            }
        } else if (focus.getMode() == IFocus.Mode.INPUT) {
            // Looking up uses for an empty cell or a component
            if (itemStack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
                // Empty cell can be used to make filled cells
                if (ComponentHelper.getInstalledComponent(itemStack).isEmpty()) {
                    return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
                }
            } else if (ComponentHelper.getComponentInfo(itemStack) != null) {
                // Valid component can be used with empty cell
                return Collections.singletonList(VanillaRecipeCategoryUid.CRAFTING);
            }
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        if (!VanillaRecipeCategoryUid.CRAFTING.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        if (ItemRegistry.CONFIGURABLE_CELL == null) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack itemStack = (ItemStack) focus.getValue();

        if (focus.getMode() == IFocus.Mode.OUTPUT) {
            // Looking up how to craft a specific filled cell
            if (itemStack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
                ItemStack installedComponent = ComponentHelper.getInstalledComponent(itemStack);

                if (!installedComponent.isEmpty()) {
                    // Create recipe for this specific cell variant
                    ItemStack emptyHousing = new ItemStack(ItemRegistry.CONFIGURABLE_CELL);
                    return Collections.singletonList((T) new ConfigurableCellRecipeWrapper(
                        emptyHousing, installedComponent.copy(), itemStack.copy()));
                }
            }
        } else if (focus.getMode() == IFocus.Mode.INPUT) {
            if (itemStack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
                // Empty cell - show all possible outputs
                if (ComponentHelper.getInstalledComponent(itemStack).isEmpty()) {
                    return (List<T>) getAllRecipes();
                }
            } else {
                // Check if it's a component - show recipe with that component
                ComponentInfo info = ComponentHelper.getComponentInfo(itemStack);
                if (info != null) {
                    ItemStack emptyHousing = new ItemStack(ItemRegistry.CONFIGURABLE_CELL);
                    ItemStack filledCell = emptyHousing.copy();
                    ComponentHelper.setInstalledComponent(filledCell, itemStack.copy());

                    return Collections.singletonList((T) new ConfigurableCellRecipeWrapper(
                        emptyHousing, itemStack.copy(), filledCell));
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory) {
        // This is called to get all recipes for the category (e.g. when browsing)
        // We don't return anything here to avoid cluttering the crafting category
        // Users can still find recipes by searching for components or cells
        return Collections.emptyList();
    }

    /**
     * Generate all possible configurable cell assembly recipes.
     */
    private List<ConfigurableCellRecipeWrapper> getAllRecipes() {
        List<ConfigurableCellRecipeWrapper> recipes = new ArrayList<>();

        if (ItemRegistry.CONFIGURABLE_CELL == null) return recipes;

        ItemStack emptyHousing = new ItemStack(ItemRegistry.CONFIGURABLE_CELL);

        for (ItemStack component : ComponentHelper.getValidComponents()) {
            ItemStack filledCell = emptyHousing.copy();
            ComponentHelper.setInstalledComponent(filledCell, component.copy());

            recipes.add(new ConfigurableCellRecipeWrapper(emptyHousing.copy(), component, filledCell));
        }

        return recipes;
    }
}
