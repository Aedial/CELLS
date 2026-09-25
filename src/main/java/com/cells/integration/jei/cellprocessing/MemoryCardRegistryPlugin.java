package com.cells.integration.jei.cellprocessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;

import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;

import com.cells.Tags;
import com.cells.parts.CellsPartType;


/**
 * Supplies Memory Card transfer rows to JEI.
 */
@SideOnly(Side.CLIENT)
public class MemoryCardRegistryPlugin implements IRecipeRegistryPlugin {

    private static final String MEKENG_MODID = "mekeng";
    private static final String THAUMICENERGISTICS_MODID = "thaumicenergistics";
    private static final String FOOTER_BUS_SETTINGS = "jei.cells.memory_card.copy_bus_settings";
    private static final String FOOTER_PROXY_SETTINGS = "jei.cells.memory_card.copy_proxy_settings";

    @Override
    @Nonnull
    public <V> List<String> getRecipeCategoryUids(@Nonnull IFocus<V> focus) {
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        ItemStack stack = (ItemStack) focus.getValue();
        if (getMatchingRecipes(stack, focus.getMode()).isEmpty()) return Collections.emptyList();

        return Collections.singletonList(CellOperationCategory.MEMORY_CARD_UID);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                   @Nonnull IFocus<V> focus) {
        if (!CellOperationCategory.MEMORY_CARD_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }
        if (!(focus.getValue() instanceof ItemStack)) return Collections.emptyList();

        List<MemoryCardRecipe> recipes = getMatchingRecipes((ItemStack) focus.getValue(), focus.getMode());
        setMatchingRecipes(recipeCategory, recipes);
        return (List<T>) recipes;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(@Nonnull IRecipeCategory<T> recipeCategory) {
        if (!CellOperationCategory.MEMORY_CARD_UID.equals(recipeCategory.getUid())) {
            return Collections.emptyList();
        }

        List<MemoryCardRecipe> recipes = createRecipes();
        setMatchingRecipes(recipeCategory, recipes);
        return (List<T>) recipes;
    }

    static ItemStack getMemoryCard() {
        return AEApi.instance().definitions().items().memoryCard().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    private static List<MemoryCardRecipe> getMatchingRecipes(ItemStack stack, IFocus.Mode mode) {
        List<MemoryCardRecipe> matchingRecipes = new ArrayList<>();
        for (MemoryCardRecipe recipe : createRecipes()) {
            if (recipe.matchesMemoryCard(stack)
                || (mode == IFocus.Mode.INPUT && recipe.matchesInput(stack))
                || (mode == IFocus.Mode.OUTPUT && recipe.matchesOutput(stack))) {
                matchingRecipes.add(recipe);
            }
        }

        return matchingRecipes;
    }

    private static void setMatchingRecipes(IRecipeCategory<?> recipeCategory,
                                           List<MemoryCardRecipe> recipes) {
        if (recipeCategory instanceof CellOperationCategory) {
            ((CellOperationCategory) recipeCategory).setMatchingRecipes(recipes);
        }
    }

    private static List<MemoryCardRecipe> createRecipes() {
        ItemStack memoryCard = getMemoryCard();
        if (memoryCard.isEmpty()) return Collections.emptyList();

        List<ItemStack> itemImport = blockForms("import_interface");
        List<ItemStack> itemExport = blockForms("export_interface");
        List<ItemStack> fluidImport = blockForms("import_fluid_interface");
        List<ItemStack> fluidExport = blockForms("export_fluid_interface");
        List<ItemStack> universalImport = blockForms("import_combined_interface");
        List<ItemStack> universalExport = blockForms("export_combined_interface");
        List<ItemStack> itemIo = blockForms("io_item_interface");
        List<ItemStack> fluidIo = blockForms("io_fluid_interface");
        List<ItemStack> gasImport = blockForms("import_gas_interface");
        List<ItemStack> gasExport = blockForms("export_gas_interface");
        List<ItemStack> gasIo = blockForms("io_gas_interface");
        List<ItemStack> essentiaImport = blockForms("import_essentia_interface");
        List<ItemStack> essentiaExport = blockForms("export_essentia_interface");
        List<ItemStack> essentiaIo = blockForms("io_essentia_interface");
        List<ItemStack> subnetProxy = partForms("part", CellsPartType.SUBNET_PROXY_FRONT.getBaseDamage());

        List<MemoryCardRecipe> recipes = new ArrayList<>();
        addInterfaceRecipes(recipes, memoryCard, itemImport, sources(itemImport, universalImport, itemIo));
        addInterfaceRecipes(recipes, memoryCard, fluidImport, sources(fluidImport, universalImport, fluidIo));
        addInterfaceRecipes(recipes, memoryCard, gasImport, sources(gasImport, universalImport, gasIo));
        addInterfaceRecipes(recipes, memoryCard, essentiaImport, sources(essentiaImport, universalImport, essentiaIo));
        addInterfaceRecipes(recipes, memoryCard, itemExport, sources(itemExport, universalExport, itemIo));
        addInterfaceRecipes(recipes, memoryCard, fluidExport, sources(fluidExport, universalExport, fluidIo));
        addInterfaceRecipes(recipes, memoryCard, gasExport, sources(gasExport, universalExport, gasIo));
        addInterfaceRecipes(recipes, memoryCard, essentiaExport, sources(essentiaExport, universalExport, essentiaIo));
        addInterfaceRecipes(recipes, memoryCard, universalImport,
            sources(itemImport, fluidImport, gasImport, essentiaImport, universalImport,
                itemIo, fluidIo, gasIo, essentiaIo));
        addInterfaceRecipes(recipes, memoryCard, universalExport,
            sources(itemExport, fluidExport, gasExport, essentiaExport, universalExport,
                itemIo, fluidIo, gasIo, essentiaIo));
        addInterfaceRecipes(recipes, memoryCard, itemIo,
            sources(itemImport, itemExport, universalImport, universalExport, itemIo));
        addInterfaceRecipes(recipes, memoryCard, fluidIo,
            sources(fluidImport, fluidExport, universalImport, universalExport, fluidIo));
        addInterfaceRecipes(recipes, memoryCard, gasIo,
            sources(gasImport, gasExport, universalImport, universalExport, gasIo));
        addInterfaceRecipes(recipes, memoryCard, essentiaIo,
            sources(essentiaImport, essentiaExport, universalImport, universalExport, essentiaIo));

        addRecipe(recipes, memoryCard, subnetProxy, FOOTER_BUS_SETTINGS,
            sources(single(AEApi.instance().definitions().parts().storageBus().maybeStack(1).orElse(ItemStack.EMPTY)),
                single(AEApi.instance().definitions().parts().fluidStorageBus().maybeStack(1).orElse(ItemStack.EMPTY)),
                single(itemStack(MEKENG_MODID, "gas_storage_bus", 0)),
                single(itemStack(THAUMICENERGISTICS_MODID, "essentia_storage", 0))));
        addRecipe(recipes, memoryCard, subnetProxy, FOOTER_PROXY_SETTINGS, sources(subnetProxy));

        return recipes;
    }

    private static void addInterfaceRecipes(List<MemoryCardRecipe> recipes, ItemStack memoryCard,
                                            List<ItemStack> target, List<List<ItemStack>> sources) {
        addRecipe(recipes, memoryCard, target, MemoryCardRecipe.INTERFACE_FOOTER, sources);
    }

    private static void addRecipe(List<MemoryCardRecipe> recipes, ItemStack memoryCard,
                                  List<ItemStack> target, String footerKey, List<List<ItemStack>> sources) {
        if (memoryCard.isEmpty() || sources.isEmpty() || target.isEmpty()) return;

        recipes.add(new MemoryCardRecipe(memoryCard, sources, target, footerKey));
    }

    @SafeVarargs
    private static List<List<ItemStack>> sources(List<ItemStack>... candidates) {
        List<List<ItemStack>> sources = new ArrayList<>();
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) sources.add(candidate);
        }

        return sources;
    }

    private static List<ItemStack> blockForms(String id) {
        return single(itemStack(Tags.MODID, id, 0));
    }

    private static List<ItemStack> partForms(String id, int metadata) {
        return single(itemStack(Tags.MODID, id, metadata));
    }

    private static List<ItemStack> single(ItemStack stack) {
        if (stack.isEmpty()) return Collections.emptyList();

        return Collections.singletonList(stack);
    }

    private static ItemStack itemStack(String modid, String id, int metadata) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(modid, id));
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, metadata);
    }
}
