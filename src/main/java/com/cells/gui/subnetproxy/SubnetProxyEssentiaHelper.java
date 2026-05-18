package com.cells.gui.subnetproxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.item.ItemDummyAspect;
import thaumicenergistics.util.ThEUtil;

import com.cells.gui.QuickAddHelper;


/**
 * Consolidated helper for all ThaumicEnergistics (essentia) operations
 * in the Subnet Proxy.
 * <p>
 * Isolates all essentia-related class references behind {@code @Optional.Method}
 * to prevent {@link ClassNotFoundException} when ThaumicEnergistics is absent.
 * <p>
 * Handles:
 * <ul>
 *   <li>ItemStack → essentia dummy conversion (filter mode click)</li>
 *   <li>IAEEssentiaStack → ItemStack conversion (quick-add)</li>
 *   <li>JEI Aspect/EssentiaStack → IAEItemStack conversion (JEI drag-drop)</li>
 *   <li>Essentia dummy detection, rendering, and display name</li>
 *   <li>Essentia dummy equality for duplicate detection in isResourceInFilter</li>
 * </ul>
 */
final class SubnetProxyEssentiaHelper {

    private SubnetProxyEssentiaHelper() {}

    // ==================== Filter Mode Conversion (ItemStack → essentia dummy) ====================

    /**
     * Convert an ItemStack to an ItemDummyAspect if it contains essentia.
     * If the stack is already an ItemDummyAspect, return it as-is.
     *
     * @param stack The ItemStack to convert
     * @return The essentia dummy ItemStack, or null if the stack doesn't contain essentia
     */
    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    static ItemStack convertToEssentiaDummy(ItemStack stack) {
        // Already an essentia dummy? Keep it.
        if (stack.getItem() instanceof ItemDummyAspect) return stack;

        // Try to extract essentia from the item (phials, jars, etc.)
        EssentiaStack essentia = QuickAddHelper.getEssentiaFromItemStack(stack);
        if (essentia == null) return null;

        return createEssentiaDummy(essentia.getAspect());
    }

    // ==================== Quick-Add Conversion (IAEEssentiaStack → ItemStack) ====================

    /**
     * Convert an IAEEssentiaStack to an ItemDummyAspect filter stack.
     * Returns EMPTY if the resource is not an IAEEssentiaStack.
     */
    @Optional.Method(modid = "thaumicenergistics")
    @Nonnull
    static ItemStack essentiaResourceToFilterStack(Object resource) {
        if (!(resource instanceof IAEEssentiaStack)) return ItemStack.EMPTY;

        Aspect aspect = ((IAEEssentiaStack) resource).getAspect();
        if (aspect == null) return ItemStack.EMPTY;

        return createEssentiaDummy(aspect);
    }

    // ==================== JEI Conversion (Aspect/EssentiaStack → IAEItemStack) ====================

    /**
     * Convert a JEI essentia ingredient (EssentiaStack, Aspect) to an
     * IAEItemStack wrapping an ItemDummyAspect, suitable for the config inventory.
     *
     * @param ingredient The JEI ingredient to convert
     * @return An IAEItemStack wrapping the essentia dummy, or null if not essentia
     */
    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    static IAEItemStack essentiaToFilterAEStack(Object ingredient) {
        Aspect aspect = null;

        if (ingredient instanceof Aspect) {
            aspect = (Aspect) ingredient;
        } else if (ingredient instanceof EssentiaStack) {
            aspect = ((EssentiaStack) ingredient).getAspect();
        }

        if (aspect == null) return null;

        ItemStack dummy = createEssentiaDummy(aspect);
        if (dummy.isEmpty()) return null;

        return AEItemStack.fromItemStack(dummy);
    }

    // ==================== Dummy Detection ====================

    static boolean isEssentiaDummy(ItemStack stack) {
        try {
            return isEssentiaDummyInternal(stack);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean isEssentiaDummyInternal(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyAspect;
    }

    // ==================== Rendering ====================

    static void renderEssentia(ItemStack stack, int x, int y) {
        try {
            renderEssentiaInternal(stack, x, y);
        } catch (NoClassDefFoundError e) {
            // Mod not loaded, skip
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static void renderEssentiaInternal(ItemStack stack, int x, int y) {
        // ItemDummyAspect already renders the aspect icon via its item model,
        // so standard item rendering works here.
        RenderHelper.enableGUIStandardItemLighting();
        Minecraft.getMinecraft().getRenderItem().renderItemIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
    }

    // ==================== Display Name ====================

    @Nullable
    static String getEssentiaName(ItemStack stack) {
        try {
            return getEssentiaNameInternal(stack);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    private static String getEssentiaNameInternal(ItemStack stack) {
        return stack.getDisplayName();
    }

    // ==================== JEI Ingredient (for tooltip lookup) ====================

    /**
     * Extract the underlying essentia ingredient ({@link Aspect}) from an
     * essentia dummy ItemStack, to be passed to JEI's ingredient registry
     * for full tooltip lookup (Thaumic JEI registers {@link Aspect} as an
     * ingredient type).
     *
     * @return The contained {@link Aspect} (as {@link Object} to avoid
     *         leaking Thaumcraft types into the call site), or {@code null}
     *         if the stack is not an essentia dummy or the mod is not loaded.
     */
    @Nullable
    static Object getEssentiaIngredient(ItemStack stack) {
        try {
            return getEssentiaIngredientInternal(stack);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    @Nullable
    private static Object getEssentiaIngredientInternal(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemDummyAspect)) return null;

        return ((ItemDummyAspect) stack.getItem()).getAspect(stack);
    }

    // ==================== Duplicate Detection (for isResourceInFilter) ====================

    /**
     * Check if an essentia resource (IAEEssentiaStack) matches any essentia dummy in the config inventory.
     *
     * @param existing The existing ItemStack in the config slot
     * @param resource The resource being checked (IAEEssentiaStack)
     * @return true if the existing stack is an essentia dummy matching the resource
     */
    static boolean isEssentiaResourceMatch(ItemStack existing, Object resource) {
        try {
            return isEssentiaResourceMatchInternal(existing, resource);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static boolean isEssentiaResourceMatchInternal(ItemStack existing, Object resource) {
        if (!(resource instanceof IAEEssentiaStack)) return false;
        if (!(existing.getItem() instanceof ItemDummyAspect)) return false;

        Aspect existingAspect = ((ItemDummyAspect) existing.getItem()).getAspect(existing);
        Aspect resourceAspect = ((IAEEssentiaStack) resource).getAspect();
        if (existingAspect == null || resourceAspect == null) return false;

        return existingAspect == resourceAspect;
    }

    // ==================== Shared Factory ====================

    /**
     * Create an essentia dummy ItemStack for the given Aspect.
     */
    @Optional.Method(modid = "thaumicenergistics")
    @Nonnull
    private static ItemStack createEssentiaDummy(Aspect aspect) {
        ItemStack dummyStack = ThEApi.instance().items().dummyAspect().maybeStack(1).orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return ItemStack.EMPTY;

        return ThEUtil.setAspect(dummyStack, aspect);
    }
}
