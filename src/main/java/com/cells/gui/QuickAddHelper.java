package com.cells.gui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IGasItem;
import mekanism.common.capabilities.Capabilities;


/**
 * Helper class for quick-add to filter functionality.
 * Handles getting items/fluids from:
 * - Hovered inventory slots
 * - JEI ingredient list
 * - JEI bookmarks
 */
public class QuickAddHelper {

    private QuickAddHelper() {}

    /**
     * Result of a quick-add lookup containing the item and optional fluid.
     */
    public static class QuickAddResult {
        public final ItemStack item;
        @Nullable
        public final FluidStack fluid;

        public QuickAddResult(ItemStack item, @Nullable FluidStack fluid) {
            this.item = item;
            this.fluid = fluid;
        }
    }

    /**
     * Get the item stack under the mouse cursor.
     * Checks inventory slots first, then JEI overlays.
     *
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return The item stack under cursor, or EMPTY if none
     */
    public static ItemStack getItemUnderCursor(@Nullable Slot hoveredSlot) {
        // Check hovered inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            return hoveredSlot.getStack().copy();
        }

        // Check JEI if available
        if (Loader.isModLoaded("jei")) return getJeiItemIngredient();

        return ItemStack.EMPTY;
    }

    /**
     * Get the fluid under the mouse cursor.
     * Checks inventory slots first (extracts fluid from containers), then JEI overlays.
     *<p>
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return The fluid stack under cursor, or null if none
     */
    @Nullable
    public static FluidStack getFluidUnderCursor(@Nullable Slot hoveredSlot) {
        // Check hovered inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            FluidStack fluid = FluidUtil.getFluidContained(hoveredSlot.getStack());
            if (fluid != null) return fluid;
        }

        // Check JEI if available
        if (Loader.isModLoaded("jei")) return getJeiFluidIngredient();

        return null;
    }

    /**
     * Get a QuickAddResult containing both item and extracted fluid.
     * Useful for fluid interfaces that need to validate fluid content.
     *
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return QuickAddResult with item and optional fluid
     */
    public static QuickAddResult getQuickAddResult(@Nullable Slot hoveredSlot) {
        ItemStack item = ItemStack.EMPTY;
        FluidStack fluid = null;

        // Check hovered inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            item = hoveredSlot.getStack().copy();
            fluid = FluidUtil.getFluidContained(item);
        } else if (Loader.isModLoaded("jei")) {
            // Check JEI
            item = getJeiItemIngredient();
            fluid = getJeiFluidIngredient();
        }

        return new QuickAddResult(item, fluid);
    }

    /**
     * Check if there is anything under the cursor (slot or JEI).
     * Used to determine if quick-add should show an error for invalid content.
     *
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return true if there's something under the cursor that we should validate
     */
    public static boolean hasAnythingUnderCursor(@Nullable Slot hoveredSlot) {
        // Check inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) return true;

        // Check JEI if available
        if (Loader.isModLoaded("jei")) {
            return com.cells.integration.jei.CellsJEIPlugin.getIngredientUnderMouse() != null;
        }

        return false;
    }

    /**
     * Send a "no space" error message to the player.
     */
    public static void sendNoSpaceError() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
        }
    }

    /**
     * Send a "no X" error message to the player (for non-item interfaces).
     */
    public static void sendNoValidError(String type) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("message.cells.not_valid_content",
                new TextComponentTranslation("cells.type." + type)));
        }
    }

    /**
     * Send a "duplicate filter" error message to the player.
     */
    public static void sendDuplicateError() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
        }
    }

    // ==================== Gas extraction methods (MekanismEnergistics integration) ====================

    private static final String MEKENG_MODID = "mekeng";

    /**
     * Get the gas under the mouse cursor.
     * Checks inventory slots first (extracts gas from containers), then JEI overlays.
     *
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return The gas stack under cursor, or null if none
     */
    @Nullable
    public static GasStack getGasUnderCursor(@Nullable Slot hoveredSlot) {
        if (!Loader.isModLoaded(MEKENG_MODID)) return null;

        return getGasUnderCursorInternal(hoveredSlot);
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static GasStack getGasUnderCursorInternal(@Nullable Slot hoveredSlot) {
        // Check hovered inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            GasStack gas = getGasFromItemStack(hoveredSlot.getStack());
            if (gas != null) return gas;
        }

        // Check JEI if available
        if (Loader.isModLoaded("jei")) return getJeiGasIngredient();

        return null;
    }

    /**
     * Extract gas from an ItemStack (gas tanks, canisters, etc.).
     * Handles IGasItem interface and GAS_HANDLER_CAPABILITY.
     *
     * @param stack The item to extract gas from
     * @return The contained gas, or null if none
     */
    @Nullable
    public static GasStack getGasFromItemStack(ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (!Loader.isModLoaded(MEKENG_MODID)) return null;

        return getGasFromItemStackInternal(stack);
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static GasStack getGasFromItemStackInternal(ItemStack stack) {
        // Try IGasItem interface first (Mekanism gas tanks, creative tanks, etc.)
        if (stack.getItem() instanceof IGasItem) {
            IGasItem gasItem = (IGasItem) stack.getItem();
            GasStack gas = gasItem.getGas(stack);
            if (gas != null && gas.amount > 0) return gas;
        }

        // Fallback: try GAS_HANDLER_CAPABILITY (for modded items that use capabilities)
        if (stack.hasCapability(Capabilities.GAS_HANDLER_CAPABILITY, null)) {
            IGasHandler gasHandler = stack.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
            if (gasHandler != null) {
                GasStack drawn = gasHandler.drawGas(EnumFacing.UP, Integer.MAX_VALUE, false);
                if (drawn != null && drawn.amount > 0) return drawn;
            }
        }

        return null;
    }

    @Optional.Method(modid = "jei")
    @Nullable
    private static GasStack getJeiGasIngredient() {
        if (!Loader.isModLoaded(MEKENG_MODID)) return null;

        return getJeiGasIngredientInternal();
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static GasStack getJeiGasIngredientInternal() {
        Object ingredient = com.cells.integration.jei.CellsJEIPlugin.getIngredientUnderMouse();
        if (ingredient == null) return null;

        // Direct GasStack
        if (ingredient instanceof GasStack) return ((GasStack) ingredient).copy();

        // IAEGasStack from MekanismEnergistics
        if (ingredient instanceof com.mekeng.github.common.me.data.IAEGasStack) {
            com.mekeng.github.common.me.data.IAEGasStack aeGas =
                (com.mekeng.github.common.me.data.IAEGasStack) ingredient;
            return aeGas.getGasStack();
        }

        // ItemStack that might contain gas
        if (ingredient instanceof ItemStack) {
            return getGasFromItemStackInternal((ItemStack) ingredient);
        }

        return null;
    }

    @Optional.Method(modid = "jei")
    private static ItemStack getJeiItemIngredient() {
        ItemStack result = com.cells.integration.jei.CellsJEIPlugin.getItemIngredientUnderMouse();
        return result != null ? result : ItemStack.EMPTY;
    }

    @Optional.Method(modid = "jei")
    @Nullable
    private static FluidStack getJeiFluidIngredient() {
        return com.cells.integration.jei.CellsJEIPlugin.getFluidIngredientUnderMouse();
    }

    // ==================== Essentia extraction methods (ThaumicEnergistics integration) ====================

    private static final String THAUMICENERGISTICS_MODID = "thaumicenergistics";

    /**
     * Get the essentia under the mouse cursor.
     * Checks inventory slots first, then JEI overlays.
     *
     * @param hoveredSlot The currently hovered inventory slot (or null)
     * @return The essentia stack under cursor, or null if none
     */
    @Nullable
    public static thaumicenergistics.api.EssentiaStack getEssentiaUnderCursor(@Nullable Slot hoveredSlot) {
        if (!Loader.isModLoaded(THAUMICENERGISTICS_MODID)) return null;

        return getEssentiaUnderCursorInternal(hoveredSlot);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nullable
    private static thaumicenergistics.api.EssentiaStack getEssentiaUnderCursorInternal(@Nullable Slot hoveredSlot) {
        // Check hovered inventory slot first
        if (hoveredSlot != null && hoveredSlot.getHasStack()) {
            thaumicenergistics.api.EssentiaStack essentia = getEssentiaFromItemStack(hoveredSlot.getStack());
            if (essentia != null) return essentia;
        }

        // Check JEI if available
        if (Loader.isModLoaded("jei")) return getJeiEssentiaIngredient();

        return null;
    }

    /**
     * Extract essentia from an ItemStack (phials, jars, etc.).
     *
     * @param stack The item to extract essentia from
     * @return The contained essentia, or null if none
     */
    @Nullable
    public static thaumicenergistics.api.EssentiaStack getEssentiaFromItemStack(ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (!Loader.isModLoaded(THAUMICENERGISTICS_MODID)) return null;

        return getEssentiaFromItemStackInternal(stack);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nullable
    private static thaumicenergistics.api.EssentiaStack getEssentiaFromItemStackInternal(ItemStack stack) {
        // Check for DummyAspect item from Thaumic Energistics
        if (stack.getItem() instanceof thaumicenergistics.item.ItemDummyAspect) {
            thaumicenergistics.item.ItemDummyAspect dummyItem =
                (thaumicenergistics.item.ItemDummyAspect) stack.getItem();
            thaumcraft.api.aspects.Aspect aspect = dummyItem.getAspect(stack);
            if (aspect != null) {
                return new thaumicenergistics.api.EssentiaStack(aspect, 1);
            }
        }

        // Check for phials/jars with aspects (Thaumcraft)
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Aspects")) {
            net.minecraft.nbt.NBTTagList aspectList = stack.getTagCompound().getTagList("Aspects", 10);
            if (aspectList.tagCount() > 0) {
                net.minecraft.nbt.NBTTagCompound aspectTag = aspectList.getCompoundTagAt(0);
                String aspectName = aspectTag.getString("key");
                int amount = aspectTag.getInteger("amount");
                thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(aspectName);
                if (aspect != null && amount > 0) {
                    return new thaumicenergistics.api.EssentiaStack(aspect, amount);
                }
            }
        }

        return null;
    }

    @Optional.Method(modid = "jei")
    @Nullable
    private static thaumicenergistics.api.EssentiaStack getJeiEssentiaIngredient() {
        if (!Loader.isModLoaded(THAUMICENERGISTICS_MODID)) return null;

        return getJeiEssentiaIngredientInternal();
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nullable
    private static thaumicenergistics.api.EssentiaStack getJeiEssentiaIngredientInternal() {
        Object ingredient = com.cells.integration.jei.CellsJEIPlugin.getIngredientUnderMouse();
        if (ingredient == null) return null;

        // Direct EssentiaStack (if JEI supports it)
        if (ingredient instanceof thaumicenergistics.api.EssentiaStack) {
            return ((thaumicenergistics.api.EssentiaStack) ingredient).copy();
        }

        // IAEEssentiaStack from ThaumicEnergistics AE2 storage
        if (ingredient instanceof thaumicenergistics.api.storage.IAEEssentiaStack) {
            thaumicenergistics.api.storage.IAEEssentiaStack aeStack =
                (thaumicenergistics.api.storage.IAEEssentiaStack) ingredient;
            thaumcraft.api.aspects.Aspect aspect = aeStack.getAspect();
            if (aspect != null) {
                return new thaumicenergistics.api.EssentiaStack(aspect, (int) Math.max(1, aeStack.getStackSize()));
            }
        }

        // Aspect directly
        if (ingredient instanceof thaumcraft.api.aspects.Aspect) {
            return new thaumicenergistics.api.EssentiaStack((thaumcraft.api.aspects.Aspect) ingredient, 1);
        }

        // ItemStack that might contain essentia
        if (ingredient instanceof ItemStack) {
            return getEssentiaFromItemStackInternal((ItemStack) ingredient);
        }

        return null;
    }

    // ==================== Ingredient conversion methods (for JEI targets) ====================

    /**
     * Convert a JEI ingredient to a FluidStack.
     * Handles FluidStack directly and ItemStack fluid containers.
     *
     * @param ingredient The JEI ingredient (FluidStack, ItemStack, or other)
     * @return The FluidStack, or null if conversion fails
     */
    @Nullable
    public static FluidStack toFluidStack(@Nullable Object ingredient) {
        if (ingredient == null) return null;

        if (ingredient instanceof FluidStack) return (FluidStack) ingredient;

        if (ingredient instanceof ItemStack) {
            return FluidUtil.getFluidContained((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Convert a JEI ingredient to a GasStack.
     * Handles GasStack directly.
     *
     * @param ingredient The JEI ingredient (GasStack or other)
     * @return The GasStack, or null if conversion fails
     */
    @Nullable
    public static GasStack toGasStack(@Nullable Object ingredient) {
        if (ingredient == null) return null;

        if (!Loader.isModLoaded(MEKENG_MODID)) return null;

        return toGasStackInternal(ingredient);
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static GasStack toGasStackInternal(@Nullable Object ingredient) {
        if (ingredient instanceof GasStack) {
            return (GasStack) ingredient;
        }

        // IAEGasStack from MekanismEnergistics
        if (ingredient instanceof com.mekeng.github.common.me.data.IAEGasStack) {
            return ((com.mekeng.github.common.me.data.IAEGasStack) ingredient).getGasStack();
        }

        // ItemStack that might contain gas (gas tanks, creative tanks, etc.)
        if (ingredient instanceof ItemStack) {
            return getGasFromItemStackInternal((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Convert a JEI ingredient to an EssentiaStack.
     * Handles EssentiaStack directly, Aspect, and DummyAspect ItemStack.
     *
     * @param ingredient The JEI ingredient (EssentiaStack, Aspect, ItemStack, or other)
     * @return The EssentiaStack, or null if conversion fails
     */
    @Nullable
    public static thaumicenergistics.api.EssentiaStack toEssentiaStack(@Nullable Object ingredient) {
        if (ingredient == null) return null;

        if (!Loader.isModLoaded(THAUMICENERGISTICS_MODID)) return null;

        return toEssentiaStackInternal(ingredient);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nullable
    private static thaumicenergistics.api.EssentiaStack toEssentiaStackInternal(@Nullable Object ingredient) {
        // Direct EssentiaStack
        if (ingredient instanceof thaumicenergistics.api.EssentiaStack) {
            return (thaumicenergistics.api.EssentiaStack) ingredient;
        }

        // IAEEssentiaStack from ThaumicEnergistics AE2 storage
        if (ingredient instanceof thaumicenergistics.api.storage.IAEEssentiaStack) {
            thaumicenergistics.api.storage.IAEEssentiaStack aeStack =
                (thaumicenergistics.api.storage.IAEEssentiaStack) ingredient;
            thaumcraft.api.aspects.Aspect aspect = aeStack.getAspect();
            if (aspect != null) {
                return new thaumicenergistics.api.EssentiaStack(aspect, (int) Math.max(1, aeStack.getStackSize()));
            }
        }

        // Direct Aspect
        if (ingredient instanceof thaumcraft.api.aspects.Aspect) {
            return new thaumicenergistics.api.EssentiaStack((thaumcraft.api.aspects.Aspect) ingredient, 1);
        }

        // ItemStack (DummyAspect or phials)
        if (ingredient instanceof ItemStack) {
            return getEssentiaFromItemStackInternal((ItemStack) ingredient);
        }

        return null;
    }
}
