package com.cells.integration.jei;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Marker interface for JEI recipe categories that need to draw overlays
 * AFTER the items are rendered.
 * <p>
 * This is used by the MixinRecipeLayout mixin to invoke the overlay drawing
 * at the correct time in the rendering pipeline, ensuring stack sizes
 * are rendered on top of items rather than behind them.
 */
@SideOnly(Side.CLIENT)
public interface IRecipeCategoryWithOverlay {

    /**
     * Called after items are rendered in the recipe layout.
     * Use this to draw stack sizes or other overlays on top of items.
     *
     * @param minecraft The Minecraft instance
     * @param offsetX   The x offset of the recipe layout
     * @param offsetY   The y offset of the recipe layout
     * @param mouseX    Current mouse X position
     * @param mouseY    Current mouse Y position
     */
    void drawOverlay(Minecraft minecraft, int offsetX, int offsetY, int mouseX, int mouseY);
}
