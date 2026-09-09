package com.cells.integration.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;

import com.cells.gui.subnetproxy.ContainerSubnetProxy;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketJEISubnetProxyFilter;


/**
 * JEI recipe transfer handler for the Subnet Proxy.
 * <p>
 * When the player clicks the '+' button on a JEI recipe, this handler
 * extracts the recipe's input ingredients and adds them as filters
 * to the Subnet Proxy's config inventory.
 * <p>
 * Unlike quick-add, this handler:
 * <ul>
 *   <li>Ignores the current filter mode, trusts AE2's type system
 *       (ItemStacks stay as items, FluidStacks → FluidDummyItem)</li>
 *   <li>Silently skips duplicates and full inventory (no error messages)</li>
 *   <li>Supports both item and fluid ingredients from any recipe category</li>
 * </ul>
 */
public class SubnetProxyRecipeTransferHandler implements IRecipeTransferHandler<ContainerSubnetProxy> {

    /**
     * Determines which ingredients from the recipe to use.
     * Currently only INPUTS is used, but kept flexible for future extension.
     */
    public enum RecipeComponentSelection {
        INPUTS,
        OUTPUTS,
        BOTH
    }

    private final RecipeComponentSelection selection;

    public SubnetProxyRecipeTransferHandler() {
        this(RecipeComponentSelection.INPUTS);
    }

    public SubnetProxyRecipeTransferHandler(RecipeComponentSelection selection) {
        this.selection = selection;
    }

    @Override
    public Class<ContainerSubnetProxy> getContainerClass() {
        return ContainerSubnetProxy.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            ContainerSubnetProxy container,
            IRecipeLayout recipeLayout,
            EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer) {

        String recipeType = recipeLayout.getRecipeCategory().getUid();

        // Skip info/fuel pseudo-recipes (they have no meaningful ingredients)
        if (recipeType.equals(VanillaRecipeCategoryUid.INFORMATION)
                || recipeType.equals(VanillaRecipeCategoryUid.FUEL)) {
            return null;
        }

        if (!doTransfer) return null;

        List<ItemStack> filterStacks = new ArrayList<>();

        // Extract item ingredients
        extractItemIngredients(recipeLayout, filterStacks);

        // Extract fluid ingredients
        extractFluidIngredients(recipeLayout, filterStacks);

        // FIXME: support gas and essentia ingredients

        if (filterStacks.isEmpty()) return null;

        // Send all filter stacks to the server in a single packet
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketJEISubnetProxyFilter(filterStacks));

        return null;
    }

    /**
     * Extract item ingredients from the recipe layout and convert them to filter stacks.
     * ItemStacks are used as-is (item filter = raw ItemStack).
     */
    private void extractItemIngredients(IRecipeLayout recipeLayout, List<ItemStack> filterStacks) {
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients =
            recipeLayout.getItemStacks().getGuiIngredients();

        for (IGuiIngredient<ItemStack> ingredient : ingredients.values()) {
            if (!shouldInclude(ingredient)) continue;

            ItemStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) continue;

            ItemStack filterStack = displayed.copy();
            filterStack.setCount(1);
            filterStacks.add(filterStack);
        }
    }

    /**
     * Extract fluid ingredients from the recipe layout and convert them to FluidDummyItem stacks.
     * FluidStacks are converted to FluidDummyItem (AE2's fluid filter representation).
     */
    private void extractFluidIngredients(IRecipeLayout recipeLayout, List<ItemStack> filterStacks) {
        IGuiFluidStackGroup fluidGroup = recipeLayout.getFluidStacks();
        Map<Integer, ? extends IGuiIngredient<FluidStack>> fluidIngredients = fluidGroup.getGuiIngredients();

        for (IGuiIngredient<FluidStack> ingredient : fluidIngredients.values()) {
            if (!shouldInclude(ingredient)) continue;

            FluidStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null) continue;

            ItemStack filterStack = fluidToFilterStack(displayed);
            if (!filterStack.isEmpty()) filterStacks.add(filterStack);
        }
    }

    /**
     * Check if an ingredient should be included based on the selection mode.
     */
    private boolean shouldInclude(IGuiIngredient<?> ingredient) {
        switch (this.selection) {
            case OUTPUTS:
                return !ingredient.isInput();
            case BOTH:
                return true;
            case INPUTS:
            default:
                return ingredient.isInput();
        }
    }

    /**
     * Convert a FluidStack to a FluidDummyItem filter stack.
     */
    private static ItemStack fluidToFilterStack(FluidStack fluid) {
        fluid = fluid.copy();
        fluid.amount = Fluid.BUCKET_VOLUME;

        ItemStack dummyStack = Api.INSTANCE.definitions().items().dummyFluidItem()
            .maybeStack(1).orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return ItemStack.EMPTY;

        FluidDummyItem dummyItem = (FluidDummyItem) dummyStack.getItem();
        dummyItem.setFluidStack(dummyStack, fluid);

        return dummyStack;
    }
}
