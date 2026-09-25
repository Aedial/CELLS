package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.AEApi;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.cells.ItemRegistry;
import com.cells.Tags;
import com.cells.cells.configurable.ChannelType;
import com.cells.cells.configurable.ComponentHelper;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.cells.hyperdensity.compacting.ItemHyperDensityCompactingCell;
import com.cells.cells.hyperdensity.fluid.ItemFluidHyperDensityCell;
import com.cells.cells.hyperdensity.item.ItemHyperDensityCell;
import com.cells.cells.normal.compacting.ItemCompactingCell;
import com.cells.items.upgrades.AbstractCustomUpgrade;
import com.cells.parts.CellsPartType;


/**
 * Supplies Cell Workbench upgrade compatibility rows to JEI.
 */
public class CellWorkbenchUpgradeRegistryPlugin implements IRecipeRegistryPlugin {

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        if (getMatchingRecipes((ItemStack) focus.getValue()).isEmpty()) return Collections.emptyList();

        return Collections.singletonList(CellOperationCategory.WORKBENCH_UID);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!CellOperationCategory.WORKBENCH_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        if (focus.getMode() != IFocus.Mode.INPUT) return Collections.emptyList();
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        List<CellOperationRecipe> recipes = getMatchingRecipes((ItemStack) focus.getValue());
        setMatchingRecipes(recipeCategory, recipes);
        return (List<T>) recipes;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        if (!CellOperationCategory.WORKBENCH_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        List<CellOperationRecipe> recipes = createRecipes();
        setMatchingRecipes(recipeCategory, recipes);
        return (List<T>) recipes;
    }

    static int getMaxWorkbenchStackCount() {
        int maxStacks = 1;
        for (CellOperationRecipe recipe : createRecipes()) {
            maxStacks = Math.max(maxStacks, recipe.getInputs().size());
            maxStacks = Math.max(maxStacks, recipe.getOutputLists().size());
        }

        return maxStacks;
    }

    private static List<CellOperationRecipe> getMatchingRecipes(ItemStack stack) {
        List<CellOperationRecipe> matchingRecipes = new ArrayList<>();
        for (CellOperationRecipe recipe : createRecipes()) {
            if (containsAlternatives(recipe.getInputLists(), stack)
                || containsAlternatives(recipe.getOutputLists(), stack)) {
                matchingRecipes.add(recipe);
            }
        }

        return matchingRecipes;
    }

    private static boolean contains(List<ItemStack> stacks, ItemStack target) {
        for (ItemStack stack : stacks) {
            if (!ItemStack.areItemsEqual(stack, target)) continue;
            if (stack.getItem() != ItemRegistry.CONFIGURABLE_CELL) return true;

            ItemStack component = ComponentHelper.getInstalledComponent(stack);
            ItemStack targetComponent = ComponentHelper.getInstalledComponent(target);
            if (ItemStack.areItemsEqual(component, targetComponent)) return true;
        }

        return false;
    }

    private static boolean containsAlternatives(List<List<ItemStack>> stacks, ItemStack target) {
        for (List<ItemStack> stackList : stacks) {
            if (contains(stackList, target)) return true;
        }

        return false;
    }

    private static void setMatchingRecipes(IRecipeCategory<?> recipeCategory,
                                           List<CellOperationRecipe> recipes) {
        if (recipeCategory instanceof CellOperationCategory) {
            ((CellOperationCategory) recipeCategory).setMatchingRecipes(recipes);
        }
    }

    private static List<CellOperationRecipe> createRecipes() {
        List<CellOperationRecipe> recipes = new ArrayList<>();

        List<List<ItemStack>> configurableUpgrades = ae2CellUpgrades();
        addCard(configurableUpgrades, ItemRegistry.OVERFLOW_CARD);
        addRecipe(recipes, configurableUpgrades, configurableCells(ChannelType.ITEM));
        addRecipe(recipes, configurableUpgrades, configurableCells(ChannelType.FLUID));
        addRecipe(recipes, configurableUpgrades, configurableCells(ChannelType.GAS));
        addRecipe(recipes, configurableUpgrades, configurableCells(ChannelType.ESSENTIA));

        List<List<ItemStack>> hyperDensityUpgrades = ae2CellUpgrades();
        addCard(hyperDensityUpgrades, ItemRegistry.OVERFLOW_CARD);
        addCards(hyperDensityUpgrades, ItemRegistry.EQUAL_DISTRIBUTION_CARD);
        addRecipe(recipes, hyperDensityUpgrades,
            tieredCells(ItemRegistry.HYPER_DENSITY_CELL, ItemHyperDensityCell.getTierNames()));
        addRecipe(recipes, hyperDensityUpgrades,
            tieredCells(ItemRegistry.FLUID_HYPER_DENSITY_CELL, ItemFluidHyperDensityCell.getTierNames()));

        List<List<ItemStack>> compactingUpgrades = ae2CellUpgrades();
        addCard(compactingUpgrades, ItemRegistry.OVERFLOW_CARD);
        addCard(compactingUpgrades, ItemRegistry.OREDICT_CARD);
        addCards(compactingUpgrades, ItemRegistry.COMPRESSION_TIER_CARD);
        addCards(compactingUpgrades, ItemRegistry.DECOMPRESSION_TIER_CARD);
        addRecipe(recipes, compactingUpgrades,
            tieredCells(ItemRegistry.COMPACTING_CELL, ItemCompactingCell.getTierNames()));
        addRecipe(recipes, compactingUpgrades,
            tieredCells(ItemRegistry.HYPER_DENSITY_COMPACTING_CELL, ItemHyperDensityCompactingCell.getTierNames()));

        List<List<ItemStack>> emcUpgrades = new ArrayList<>();
        addCards(emcUpgrades, ItemRegistry.EMC_CAPACITY_CARD);
        addRecipe(recipes, emcUpgrades, single(ItemRegistry.EMC_CELL));

        List<List<ItemStack>> subnetProxyUpgrades = new ArrayList<>();
        addCard(subnetProxyUpgrades, aeCapacityCard());
        addCard(subnetProxyUpgrades, aeFuzzyCard());
        addCard(subnetProxyUpgrades, aeInverterCard());
        addCard(subnetProxyUpgrades, ItemRegistry.INSERTION_CARD);
        addRecipe(recipes, subnetProxyUpgrades, singlePart(CellsPartType.SUBNET_PROXY_FRONT));

        List<List<ItemStack>> importUpgrades = new ArrayList<>();
        addCard(importUpgrades, aeCapacityCard());
        addCard(importUpgrades, ItemRegistry.OVERFLOW_CARD);
        addCard(importUpgrades, ItemRegistry.TRASH_UNSELECTED_CARD);
        addCard(importUpgrades, ItemRegistry.PULL_CARD);
        addAlternativeRecipe(recipes, importUpgrades, importInterfaces());

        List<List<ItemStack>> exportUpgrades = new ArrayList<>();
        addCard(exportUpgrades, aeCapacityCard());
        addCard(exportUpgrades, ItemRegistry.PUSH_CARD);
        addAlternativeRecipe(recipes, exportUpgrades, exportInterfaces());

        return recipes;
    }

    private static List<List<ItemStack>> ae2CellUpgrades() {
        List<List<ItemStack>> upgrades = new ArrayList<>();
        addCard(upgrades, aeFuzzyCard());
        addCard(upgrades, aeInverterCard());
        addCard(upgrades, aeStickyCard());
        return upgrades;
    }

    private static List<ItemStack> configurableCells(ChannelType channelType) {
        if (ItemRegistry.CONFIGURABLE_CELL == null) return Collections.emptyList();

        List<ItemStack> components = new ArrayList<>();
        for (ItemStack component : ComponentHelper.getValidComponents()) {
            ComponentInfo info = ComponentHelper.getComponentInfo(component);
            if (info != null && info.getChannelType() == channelType) components.add(component);
        }

        components.sort(Comparator.comparingLong((ItemStack component) -> {
            return ComponentHelper.getComponentInfo(component).getBytes();
        }).thenComparing(CellWorkbenchUpgradeRegistryPlugin::getRegistryName)
            .thenComparingInt(ItemStack::getMetadata));

        List<ItemStack> cells = new ArrayList<>();
        Set<String> tiers = new HashSet<>();
        for (ItemStack component : components) {
            ComponentInfo info = ComponentHelper.getComponentInfo(component);
            if (!tiers.add(info.getTierName())) continue;

            ItemStack cell = new ItemStack(ItemRegistry.CONFIGURABLE_CELL);
            ComponentHelper.setInstalledComponent(cell, component.copy());
            cells.add(cell);
        }

        return cells;
    }

    private static String getRegistryName(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        return name == null ? "" : name.toString();
    }

    private static List<ItemStack> tieredCells(Item item, String[] tierNames) {
        if (item == null) return Collections.emptyList();

        List<ItemStack> cells = new ArrayList<>();
        for (int tier = 0; tier < tierNames.length; tier++) cells.add(new ItemStack(item, 1, tier));

        return cells;
    }

    private static List<List<ItemStack>> importInterfaces() {
        List<List<ItemStack>> interfaces = new ArrayList<>();
        singleItem(interfaces, "import_interface");
        singleItem(interfaces, "import_fluid_interface");
        singleItem(interfaces, "import_combined_interface");
        singleItem(interfaces, "io_item_interface");
        singleItem(interfaces, "io_fluid_interface");
        singleOptionalItem(interfaces, "import_gas_interface");
        singleOptionalItem(interfaces, "io_gas_interface");
        singleOptionalItem(interfaces, "import_essentia_interface");
        singleOptionalItem(interfaces, "io_essentia_interface");

        return interfaces;
    }

    private static List<List<ItemStack>> exportInterfaces() {
        List<List<ItemStack>> interfaces = new ArrayList<>();

        singleItem(interfaces, "export_interface");
        singleItem(interfaces, "export_fluid_interface");
        singleItem(interfaces, "export_combined_interface");
        singleItem(interfaces, "io_item_interface");
        singleItem(interfaces, "io_fluid_interface");
        singleOptionalItem(interfaces, "export_gas_interface");
        singleOptionalItem(interfaces, "io_gas_interface");
        singleOptionalItem(interfaces, "export_essentia_interface");
        singleOptionalItem(interfaces, "io_essentia_interface");

        return interfaces;
    }

    private static List<ItemStack> singleItem(String itemId) {
        List<ItemStack> stacks = new ArrayList<>();
        addItem(stacks, itemId, 0);
        return stacks;
    }

    private static void singleItem(List<List<ItemStack>> interfaces, String itemId) {
        List<ItemStack> stacks = singleItem(itemId);
        if (!stacks.isEmpty()) interfaces.add(stacks);
    }

    private static void singleOptionalItem(List<List<ItemStack>> interfaces, String itemId) {
        List<ItemStack> stacks = singleItem(itemId);
        if (!stacks.isEmpty()) interfaces.add(stacks);
    }

    private static List<ItemStack> singlePart(CellsPartType partType) {
        List<ItemStack> part = new ArrayList<>();
        addPart(part, "part", partType.getBaseDamage());
        return part;
    }

    private static void addPart(List<ItemStack> stacks, String id, int metadata) {
        addItem(stacks, id, metadata);
    }

    private static void addItem(List<ItemStack> stacks, String id, int metadata) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Tags.MODID, id));
        if (item != null) stacks.add(new ItemStack(item, 1, metadata));
    }

    private static List<ItemStack> single(Item item) {
        if (item == null) return Collections.emptyList();

        return Collections.singletonList(new ItemStack(item));
    }

    private static void addRecipe(List<CellOperationRecipe> recipes, List<List<ItemStack>> upgrades,
                                  List<ItemStack> targets) {
        if (upgrades.isEmpty() || targets.isEmpty()) return;

        recipes.add(CellOperationRecipe.withAlternatingInputs(upgrades, targets));
    }

    private static void addAlternativeRecipe(List<CellOperationRecipe> recipes,
                                             List<List<ItemStack>> upgrades,
                                             List<List<ItemStack>> targets) {
        if (upgrades.isEmpty() || targets.isEmpty()) return;

        recipes.add(CellOperationRecipe.withAlternatingOutputs(upgrades, targets));
    }

    private static void addCards(List<List<ItemStack>> stacks, AbstractCustomUpgrade card) {
        if (card == null) return;

        List<ItemStack> variants = new ArrayList<>();
        for (int tier = 0; tier < card.getTierNames().length; tier++) {
            variants.add(new ItemStack(card, 1, tier));
        }

        if (!variants.isEmpty()) stacks.add(variants);
    }

    private static void addCard(List<List<ItemStack>> stacks, Item item) {
        if (item != null) stacks.add(Collections.singletonList(new ItemStack(item)));
    }

    private static void addCard(List<List<ItemStack>> stacks, ItemStack stack) {
        if (!stack.isEmpty()) stacks.add(Collections.singletonList(stack));
    }

    private static ItemStack aeCapacityCard() {
        return AEApi.instance().definitions().materials().cardCapacity().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    private static ItemStack aeFuzzyCard() {
        return AEApi.instance().definitions().materials().cardFuzzy().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    private static ItemStack aeInverterCard() {
        return AEApi.instance().definitions().materials().cardInverter().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    private static ItemStack aeStickyCard() {
        return AEApi.instance().definitions().materials().cardSticky().maybeStack(1).orElse(ItemStack.EMPTY);
    }
}
