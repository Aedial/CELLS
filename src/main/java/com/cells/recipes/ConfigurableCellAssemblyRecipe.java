package com.cells.recipes;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;

import com.cells.ItemRegistry;
import com.cells.cells.configurable.ComponentHelper;


/**
 * Dynamic crafting recipe that combines an empty Configurable Cell housing
 * with any valid ME Storage Component to produce a configured cell.
 * <p>
 * The recipe only matches when:
 * <ul>
 *   <li>Exactly two items are in the grid</li>
 *   <li>One is an empty Configurable Cell (no component installed)</li>
 *   <li>One is a valid component from the whitelist</li>
 * </ul>
 * <p>
 * The output is a copy of the housing with the component installed via NBT.
 * <p>
 * JEI will display all valid component variants by reading from {@link #getIngredients()}.
 */
public class ConfigurableCellAssemblyRecipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {

    /** Cached ingredients for JEI display - lazily initialized */
    private NonNullList<Ingredient> cachedIngredients;

    public ConfigurableCellAssemblyRecipe() {
    }

    @Override
    public boolean matches(@Nonnull InventoryCrafting inv, @Nonnull World world) {
        return !findOutput(inv).isEmpty();
    }

    @Override
    @Nonnull
    public ItemStack getCraftingResult(@Nonnull InventoryCrafting inv) {
        return findOutput(inv);
    }

    /**
     * Find the recipe output for the given crafting inventory.
     *
     * @param inv The crafting inventory
     * @return The output ItemStack, or EMPTY if the recipe doesn't match
     */
    private ItemStack findOutput(InventoryCrafting inv) {
        ItemStack housing = ItemStack.EMPTY;
        ItemStack component = ItemStack.EMPTY;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Check if it's a configurable cell housing (empty, no component)
            if (isEmptyConfigurableCell(stack)) {
                if (!housing.isEmpty()) return ItemStack.EMPTY; // Already found one

                housing = stack;
                continue;
            }

            // Check if it's a valid component
            if (ComponentHelper.getComponentInfo(stack) != null) {
                if (!component.isEmpty()) return ItemStack.EMPTY; // Already found one

                component = stack;
                continue;
            }

            // Unknown item in the grid - recipe doesn't match
            return ItemStack.EMPTY;
        }

        // Need exactly one of each
        if (housing.isEmpty() || component.isEmpty()) return ItemStack.EMPTY;

        // Create the output: housing with the component installed
        ItemStack result = housing.copy();
        result.setCount(1);

        // Install the component (single item)
        ItemStack componentToInstall = component.copy();
        componentToInstall.setCount(1);
        ComponentHelper.setInstalledComponent(result, componentToInstall);

        return result;
    }

    /**
     * Check if the given stack is an empty configurable cell (no component installed).
     */
    private boolean isEmptyConfigurableCell(ItemStack stack) {
        if (ItemRegistry.CONFIGURABLE_CELL == null) return false;

        // Must be a configurable cell item
        if (stack.getItem() != ItemRegistry.CONFIGURABLE_CELL) return false;

        // Must not have a component installed
        return ComponentHelper.getInstalledComponent(stack).isEmpty();
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    @Nonnull
    public ItemStack getRecipeOutput() {
        // Return an empty configurable cell as the "sample" output
        // Actual output depends on input component
        if (ItemRegistry.CONFIGURABLE_CELL != null) return new ItemStack(ItemRegistry.CONFIGURABLE_CELL);

        return ItemStack.EMPTY;
    }

    /**
     * Returns the ingredients for JEI to display.
     * First ingredient is the empty housing, second is all valid components.
     */
    @Override
    @Nonnull
    public NonNullList<Ingredient> getIngredients() {
        if (cachedIngredients != null) return cachedIngredients;

        cachedIngredients = NonNullList.create();

        // First ingredient: empty configurable cell housing
        if (ItemRegistry.CONFIGURABLE_CELL != null) {
            cachedIngredients.add(Ingredient.fromStacks(new ItemStack(ItemRegistry.CONFIGURABLE_CELL)));
        }

        // Second ingredient: all valid components (JEI will cycle through them)
        List<ItemStack> validComponents = ComponentHelper.getValidComponents();
        if (!validComponents.isEmpty()) {
            cachedIngredients.add(Ingredient.fromStacks(validComponents.toArray(new ItemStack[0])));
        }

        return cachedIngredients;
    }

    @Override
    @Nonnull
    public NonNullList<ItemStack> getRemainingItems(@Nonnull InventoryCrafting inv) {
        return NonNullList.withSize(inv.getSizeInventory(), ItemStack.EMPTY);
    }

    @Override
    public boolean isDynamic() {
        // Return true to hide from vanilla recipe book and default JEI handling.
        // Our custom CellsJEIPlugin handles the JEI display instead.
        return true;
    }
}
