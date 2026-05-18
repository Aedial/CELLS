package com.cells.gui.subnetproxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.cells.gui.QuickAddHelper;
import com.cells.gui.ResourceRenderer;

import com.mekeng.github.common.ItemAndBlocks;
import com.mekeng.github.common.item.ItemDummyGas;
import com.mekeng.github.common.me.data.IAEGasStack;

import mekanism.api.gas.GasStack;


/**
 * Helper for all MekanismEnergistics (gas) operations
 * in the Subnet Proxy.
 * <p>
 * Isolates all gas-related class references behind {@code @Optional.Method}
 * to prevent {@link ClassNotFoundException} when MekanismEnergistics is absent.
 * <p>
 * Handles:
 * <ul>
 *   <li>ItemStack → gas dummy conversion (filter mode click)</li>
 *   <li>IAEGasStack → ItemStack conversion (quick-add)</li>
 *   <li>JEI GasStack → IAEItemStack conversion (JEI drag-drop)</li>
 *   <li>Gas dummy detection, rendering, and display name</li>
 *   <li>Gas dummy equality for duplicate detection in isResourceInFilter</li>
 * </ul>
 */
final class SubnetProxyGasHelper {

    private SubnetProxyGasHelper() {}

    // ==================== Filter Mode Conversion (ItemStack → gas dummy) ====================

    /**
     * Convert an ItemStack to an ItemDummyGas if it contains gas.
     * If the stack is already an ItemDummyGas, return it as-is.
     *
     * @param stack The ItemStack to convert
     * @return The gas dummy ItemStack, or null if the stack doesn't contain gas
     */
    @Optional.Method(modid = "mekeng")
    @Nullable
    static ItemStack convertToGasDummy(ItemStack stack) {
        // Already a gas dummy? Keep it.
        if (stack.getItem() instanceof ItemDummyGas) return stack;

        // Try to extract gas from the item (gas tanks, etc.)
        GasStack gas = QuickAddHelper.getGasFromItemStack(stack);
        if (gas == null) return null;

        return createGasDummy(gas);
    }

    // ==================== Quick-Add Conversion (IAEGasStack → ItemStack) ====================

    /**
     * Convert an IAEGasStack to an ItemDummyGas filter stack.
     * Returns EMPTY if the resource is not an IAEGasStack.
     */
    @Optional.Method(modid = "mekeng")
    @Nonnull
    static ItemStack gasResourceToFilterStack(Object resource) {
        if (!(resource instanceof IAEGasStack)) return ItemStack.EMPTY;

        GasStack gas = ((IAEGasStack) resource).getGasStack();
        if (gas == null) return ItemStack.EMPTY;

        return createGasDummy(gas);
    }

    // ==================== JEI Conversion (GasStack → IAEItemStack) ====================

    /**
     * Convert a JEI gas ingredient (GasStack) to an IAEItemStack wrapping
     * an ItemDummyGas, suitable for the config inventory.
     *
     * @param ingredient The JEI ingredient to convert
     * @return An IAEItemStack wrapping the gas dummy, or null if not a gas
     */
    @Optional.Method(modid = "mekeng")
    @Nullable
    static IAEItemStack gasToFilterAEStack(Object ingredient) {
        if (!(ingredient instanceof GasStack)) return null;

        ItemStack dummy = createGasDummy((GasStack) ingredient);
        return AEItemStack.fromItemStack(dummy);
    }

    // ==================== Dummy Detection ====================

    static boolean isGasDummy(ItemStack stack) {
        try {
            return isGasDummyInternal(stack);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Optional.Method(modid = "mekeng")
    private static boolean isGasDummyInternal(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyGas;
    }

    // ==================== Rendering ====================

    static void renderGas(ItemStack stack, int x, int y) {
        try {
            renderGasInternal(stack, x, y);
        } catch (NoClassDefFoundError e) {
            // Mod not loaded, skip
        }
    }

    @Optional.Method(modid = "mekeng")
    private static void renderGasInternal(ItemStack stack, int x, int y) {
        ItemDummyGas dummyGas = (ItemDummyGas) stack.getItem();
        GasStack gasStack = dummyGas.getGasStack(stack);
        if (gasStack != null) {
            ResourceRenderer.renderGasStack(gasStack, x, y, 16, 16);
        }
    }

    // ==================== Display Name ====================

    @Nullable
    static String getGasName(ItemStack stack) {
        try {
            return getGasNameInternal(stack);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Optional.Method(modid = "mekeng")
    @Nullable
    private static String getGasNameInternal(ItemStack stack) {
        ItemDummyGas dummyGas = (ItemDummyGas) stack.getItem();
        GasStack gasStack = dummyGas.getGasStack(stack);
        return gasStack != null ? gasStack.getGas().getLocalizedName() : null;
    }

    // ==================== JEI Ingredient (for tooltip lookup) ====================

    /**
     * Extract the underlying {@link GasStack} from a gas dummy ItemStack,
     * to be passed to JEI's ingredient registry for full tooltip lookup.
     *
     * @return The contained {@link GasStack} (as {@link Object} to avoid
     *         leaking Mekanism types into the call site), or {@code null} if
     *         the stack is not a gas dummy or the mod is not loaded.
     */
    @Nullable
    static Object getGasIngredient(ItemStack stack) {
        try {
            return getGasIngredientInternal(stack);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Optional.Method(modid = "mekeng")
    @Nullable
    private static Object getGasIngredientInternal(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemDummyGas)) return null;

        return ((ItemDummyGas) stack.getItem()).getGasStack(stack);
    }

    // ==================== Duplicate Detection (for isResourceInFilter) ====================

    /**
     * Check if a gas resource (IAEGasStack) matches any gas dummy in the config inventory.
     *
     * @param existing The existing ItemStack in the config slot
     * @param resource The resource being checked (IAEGasStack)
     * @return true if the existing stack is a gas dummy matching the resource
     */
    static boolean isGasResourceMatch(ItemStack existing, Object resource) {
        try {
            return isGasResourceMatchInternal(existing, resource);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    @Optional.Method(modid = "mekeng")
    private static boolean isGasResourceMatchInternal(ItemStack existing, Object resource) {
        if (!(resource instanceof IAEGasStack)) return false;
        if (!(existing.getItem() instanceof ItemDummyGas)) return false;

        GasStack existingGas = ((ItemDummyGas) existing.getItem()).getGasStack(existing);
        GasStack resourceGas = ((IAEGasStack) resource).getGasStack();
        if (existingGas == null || resourceGas == null) return false;

        return existingGas.getGas() == resourceGas.getGas();
    }

    // ==================== Shared Factory ====================

    /**
     * Create a gas dummy ItemStack for the given GasStack.
     */
    @Optional.Method(modid = "mekeng")
    @Nonnull
    private static ItemStack createGasDummy(GasStack gas) {
        gas = gas.copy();
        gas.amount = 1000;

        ItemStack dummyStack = new ItemStack(ItemAndBlocks.DUMMY_GAS);
        ItemDummyGas dummyItem = (ItemDummyGas) dummyStack.getItem();
        dummyItem.setGasStack(dummyStack, gas);

        return dummyStack;
    }
}
