package com.cells.mixin.jei;

import com.cells.integration.jei.IRecipeCategoryWithOverlay;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeLayout;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject overlay drawing into JEI's RecipeLayout.
 * <p>
 * This allows our CellViewCategory to draw stack sizes AFTER items are rendered,
 * ensuring proper z-ordering (stack sizes appear on top of items, not behind them).
 * <p>
 * The injection point is right before GlStateManager.disableBlend() in drawRecipe,
 * which is after items have been rendered but before blend is disabled.
 */
@Mixin(value = RecipeLayout.class, remap = false)
public class MixinRecipeLayout {

    @Shadow
    @Final
    private IRecipeCategory<?> recipeCategory;

    @Shadow
    private int posY;

    @Shadow
    private int posX;

    /**
     * Inject our overlay drawing at the correct point in the rendering pipeline.
     * This runs after items are rendered, so stack sizes will appear on top.
     */
    @Inject(method = "drawRecipe", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;disableBlend()V",
        remap = true
    ))
    public void cells$drawOverlay(Minecraft minecraft, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.recipeCategory instanceof IRecipeCategoryWithOverlay) {
            ((IRecipeCategoryWithOverlay) this.recipeCategory)
                .drawOverlay(minecraft, this.posX, this.posY, mouseX, mouseY);
        }
    }
}
