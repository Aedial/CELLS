package com.cells.gui;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;


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
     * Send a "no space" error message to the player.
     */
    public static void sendNoSpaceError() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("message.cells.import_interface.no_filter_space"));
        }
    }

    /**
     * Send a "no fluid" error message to the player (for fluid interfaces).
     */
    public static void sendNoFluidError() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentTranslation("message.cells.import_fluid_interface.not_fluid_container"));
        }
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
}
