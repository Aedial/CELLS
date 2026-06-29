package com.cells.integration.jei;

import java.lang.reflect.Field;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.IIngredientListOverlay;
import mezz.jei.api.IBookmarkOverlay;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import mezz.jei.config.Constants;

import com.cells.ItemRegistry;
import com.cells.cells.creative.fluid.ContainerCreativeFluidCell;
import com.cells.cells.creative.item.ContainerCreativeCell;
import com.cells.blocks.combinedinterface.ContainerCombinedInterface;
import com.cells.blocks.interfacebase.fluid.ContainerFluidInterface;
import com.cells.blocks.interfacebase.item.ContainerItemInterface;
import com.cells.blocks.iointerface.ContainerIOInterface;
import com.cells.config.CellsConfig;
import com.cells.gui.QuickAddHelper;
import com.cells.integration.jei.InterfaceRecipeTransferHandler.TransferMode;
import com.cells.integration.jei.cellview.CellViewCategory;
import com.cells.integration.jei.cellview.CellViewRegistryPlugin;
import com.cells.network.sync.ResourceType;


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

    /**
     * Captured during {@link #register} so that we can look up ingredient
     * renderers (and thus their JEI tooltips) at any time.
     * <p>
     * IJeiRuntime in JEI 4.x does not expose the ingredient registry, so we
     * have to grab it from {@link IModRegistry} while we still have it.
     */
    private static IIngredientRegistry ingredientRegistry = null;

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
        // Capture the ingredient registry for later tooltip lookups (used by
        // filter slot tooltips to show the same content JEI shows on hover).
        ingredientRegistry = registry.getIngredientRegistry();

        // Register configurable cell assembly recipe plugin
        if (CellsConfig.enabledCells.enableConfigurableCells && ItemRegistry.CONFIGURABLE_CELL != null) {
            registry.addRecipeRegistryPlugin(new ConfigurableCellRegistryPlugin());
        }

        // Register cell view feature
        if (enableCellView) registry.addRecipeRegistryPlugin(new CellViewRegistryPlugin());

        registerInterfaceRecipeTransferHandlers(registry);
        registerCreativeCellRecipeTransferHandlers(registry);

        // Register Subnet Proxy recipe transfer handler (universal: works with any recipe category)
        SubnetProxyRecipeTransferHandler transferHandler = new SubnetProxyRecipeTransferHandler();
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            transferHandler, Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
    }

    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    private void registerInterfaceRecipeTransferHandlers(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerItemInterface.class, TransferMode.SINGLE_DIRECTION),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerFluidInterface.class, TransferMode.SINGLE_DIRECTION),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerCombinedInterface.class, TransferMode.COMBINED_ALL_TYPES),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerIOInterface.class, TransferMode.IO_SPLIT_DIRECTIONS),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);

        if (ResourceType.GAS.isAvailable()) registerGasInterfaceTransferHandler(registry);
        if (ResourceType.ESSENTIA.isAvailable()) registerEssentiaInterfaceTransferHandler(registry);
    }

    private void registerCreativeCellRecipeTransferHandlers(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerCreativeCell.class, TransferMode.FILTER_SELECTION_ONLY),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(ContainerCreativeFluidCell.class, TransferMode.FILTER_SELECTION_ONLY),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);

        if (ResourceType.GAS.isAvailable()) registerCreativeGasCellTransferHandler(registry);
        if (ResourceType.ESSENTIA.isAvailable()) registerCreativeEssentiaCellTransferHandler(registry);
    }

    @Optional.Method(modid = "mekeng")
    private void registerGasInterfaceTransferHandler(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(
                com.cells.integration.mekanismenergistics.ContainerGasInterface.class,
                TransferMode.SINGLE_DIRECTION),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private void registerEssentiaInterfaceTransferHandler(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(
                com.cells.integration.thaumicenergistics.ContainerEssentiaInterface.class,
                TransferMode.SINGLE_DIRECTION),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
    }

    @Optional.Method(modid = "mekeng")
    private void registerCreativeGasCellTransferHandler(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(
                com.cells.cells.creative.gas.ContainerCreativeGasCell.class,
                TransferMode.FILTER_SELECTION_ONLY),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private void registerCreativeEssentiaCellTransferHandler(@Nonnull IModRegistry registry) {
        registry.getRecipeTransferRegistry().addRecipeTransferHandler(
            new InterfaceRecipeTransferHandler<>(
                com.cells.cells.creative.essentia.ContainerCreativeEssentiaCell.class,
                TransferMode.FILTER_SELECTION_ONLY),
            Constants.UNIVERSAL_RECIPE_TRANSFER_UID);
    }

    /**
     * Get the raw ingredient under the mouse cursor from JEI overlays.
     * Returns the ingredient as-is (could be ItemStack, FluidStack, GasStack, etc.)
     *
     * @return The ingredient under the cursor, or null if none found
     */
    @Nullable
    public static Object getIngredientUnderMouse() {
        if (jeiRuntime == null) return null;

        // Check ingredient list first
        IIngredientListOverlay ingredientList = jeiRuntime.getIngredientListOverlay();
        Object ingredient = ingredientList.getIngredientUnderMouse();
        if (ingredient != null) return unwrapBookmarkItem(ingredient);

        // Check bookmarks
        IBookmarkOverlay bookmarks = jeiRuntime.getBookmarkOverlay();
        return unwrapBookmarkItem(bookmarks.getIngredientUnderMouse());
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

        // Check bookmarks (latest HEI wraps each bookmark in a BookmarkItem<I>; unwrap reflectively)
        IBookmarkOverlay bookmarks = jeiRuntime.getBookmarkOverlay();
        ingredient = unwrapBookmarkItem(bookmarks.getIngredientUnderMouse());

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
        ingredient = unwrapBookmarkItem(bookmarks.getIngredientUnderMouse());

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

    /**
     * Cached reflective handle to {@code mezz.jei.bookmarks.BookmarkItem.ingredient}.
     * <p>
     * Looked up lazily because the latest HEI fork wraps every bookmark in a
     * {@code BookmarkItem<I>} (and subclasses like {@code RecipeBookmarkItem}),
     * while older HEI / vanilla JEI return raw ingredients directly. We can't
     * compile-link against the HEI class without forcing HEI as a hard dep, so
     * the unwrap is name-based + reflection.
     */
    private static volatile Field bookmarkItemIngredientField;

    /**
     * Class object of {@code BookmarkItem} once we've located it; null on
     * platforms that don't have it (regular JEI, older HEI). Used as a fast
     * negative cache via {@link #bookmarkLookupAttempted}.
     */
    private static volatile Class<?> bookmarkItemClass;

    /** True once we've tried to resolve {@link #bookmarkItemClass} at least once. */
    private static volatile boolean bookmarkLookupAttempted;

    /**
     * Unwrap an HEI {@code BookmarkItem<I>} to the inner ingredient.
     * <p>
     * Newer HEI versions return wrapped {@code BookmarkItem} instances from
     * {@link IBookmarkOverlay#getIngredientUnderMouse()} instead of the raw
     * ingredient, breaking our encode-from-keybind feature when reading from
     * bookmarks. We unwrap reflectively so that we still build cleanly against
     * environments without HEI.
     * <p>
     * Subclasses (e.g. {@code RecipeBookmarkItem}) inherit the {@code ingredient}
     * field, so we walk up the class hierarchy to find it.
     *
     * @param ingredient the (possibly wrapped) ingredient to unwrap
     * @return the inner ingredient, or {@code ingredient} unchanged if it is
     *         not a recognised wrapper
     */
    @Nullable
    private static Object unwrapBookmarkItem(@Nullable Object ingredient) {
        if (ingredient == null) return null;

        // Resolve the BookmarkItem class lazily and cache the result (positive or negative).
        if (!bookmarkLookupAttempted) {
            try {
                bookmarkItemClass = Class.forName("mezz.jei.bookmarks.BookmarkItem");
            } catch (ClassNotFoundException ignored) {
                // Vanilla JEI or older HEI: nothing to unwrap, ever.
            }
            bookmarkLookupAttempted = true;
        }

        Class<?> wrapperClass = bookmarkItemClass;
        if (wrapperClass == null || !wrapperClass.isInstance(ingredient)) return ingredient;

        try {
            Field f = bookmarkItemIngredientField;
            if (f == null) {
                // Field is declared on BookmarkItem itself; subclasses inherit it.
                f = wrapperClass.getField("ingredient");
                bookmarkItemIngredientField = f;
            }
            Object inner = f.get(ingredient);
            return inner != null ? inner : ingredient;
        } catch (ReflectiveOperationException e) {
            return ingredient;
        }
    }

    @Nullable
    private static FluidStack convertToFluidStack(Object ingredient) {
        if (ingredient instanceof FluidStack) return ((FluidStack) ingredient).copy();

        // Try to extract fluid from item
        if (ingredient instanceof ItemStack) {
            return QuickAddHelper.getFluidFromItemStack((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Get the JEI-rendered tooltip lines for any registered ingredient
     * (ItemStack, FluidStack, GasStack, AspectStack, etc.).
     * <p>
     * This goes through JEI's {@link IIngredientRenderer#getTooltip}, which
     * mirrors what JEI itself shows on hover, including lines added by other
     * mods through their JEI plugins.
     * <p>
     * Returns {@code null} if JEI is not loaded, the runtime is not yet ready,
     * or the ingredient type is not registered with JEI. Callers must provide
     * a non-JEI fallback in those cases.
     *
     * @param ingredient The ingredient to query (any JEI-registered type)
     * @param flag       The tooltip flag (NORMAL or ADVANCED, F3+H)
     * @return The tooltip lines, or {@code null} if JEI cannot provide them
     */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<String> getIngredientTooltip(@Nullable Object ingredient, @Nonnull ITooltipFlag flag) {
        if (ingredient == null) return null;
        if (ingredientRegistry == null) return null;

        // Wrap the entire JEI lookup chain: getIngredientRenderer can throw
        // IllegalArgumentException for unregistered types, and individual
        // renderers/plugins can throw at runtime in poorly-behaved integrations.
        // Any failure should fall back to the non-JEI path, never crash the GUI.
        try {
            IIngredientRegistry reg = ingredientRegistry;
            if (reg == null) return null;

            IIngredientRenderer renderer = reg.getIngredientRenderer(ingredient);
            if (renderer == null) return null;

            return renderer.getTooltip(Minecraft.getMinecraft(), ingredient, flag);
        } catch (Throwable t) {
            return null;
        }
    }
}
