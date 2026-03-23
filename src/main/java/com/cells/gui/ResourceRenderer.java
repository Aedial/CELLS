package com.cells.gui;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.ReadableNumberConverter;


/**
 * Unified rendering helpers for all resource types (fluid, gas, essentia).
 * <p>
 * Centralizes all resource rendering logic to avoid duplication across:
 * - Filter slots (interfaces, creative cells)
 * - Tank slots
 * - JEI Cell View
 * - Any other GUI that needs to render resources
 */
@SideOnly(Side.CLIENT)
public final class ResourceRenderer {

    private ResourceRenderer() {}

    // ==================== Fluid Rendering ====================

    /**
     * Render a fluid at the given position.
     */
    public static void renderFluid(IAEFluidStack aeFluid, int x, int y, int width, int height) {
        if (aeFluid == null) return;

        FluidStack fluidStack = aeFluid.getFluidStack();
        if (fluidStack == null) return;

        renderFluid(fluidStack, x, y, width, height);
    }

    /**
     * Render a fluid at the given position.
     */
    public static void renderFluid(FluidStack fluidStack, int x, int y, int width, int height) {
        if (fluidStack == null) return;

        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());

        // Set color for dynamic fluids - use getColor(FluidStack) for NBT-aware coloring (potions, etc.)
        int color = fluid.getColor(fluidStack);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue);

        drawTexturedRect(x, y, sprite, width, height);

        // Reset color
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableLighting();
        GlStateManager.enableBlend();
    }

    // ==================== Gas Rendering ====================

    private static final String MEKENG_MODID = "mekeng";

    /**
     * Check if the stack is a gas stack.
     */
    public static boolean isGasStack(IAEStack<?> stack) {
        if (!Loader.isModLoaded(MEKENG_MODID)) return false;

        return isGasStackInternal(stack);
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static boolean isGasStackInternal(IAEStack<?> stack) {
        return stack instanceof com.mekeng.github.common.me.data.IAEGasStack;
    }

    /**
     * Render a gas stack (IAEGasStack) at the given position.
     */
    public static void renderGas(IAEStack<?> stack, int x, int y, int width, int height) {
        if (!Loader.isModLoaded(MEKENG_MODID)) return;

        renderGasInternal(stack, x, y, width, height);
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static void renderGasInternal(IAEStack<?> stack, int x, int y, int width, int height) {
        if (!(stack instanceof com.mekeng.github.common.me.data.IAEGasStack)) return;

        com.mekeng.github.common.me.data.IAEGasStack aeGas =
            (com.mekeng.github.common.me.data.IAEGasStack) stack;
        mekanism.api.gas.GasStack gasStack = aeGas.getGasStack();
        if (gasStack == null) return;

        renderGasStack(gasStack, x, y, width, height);
    }

    /**
     * Render a GasStack at the given position.
     */
    public static void renderGasStack(Object gasStackObj, int x, int y, int width, int height) {
        if (!Loader.isModLoaded(MEKENG_MODID)) return;

        renderGasStackInternal(gasStackObj, x, y, width, height);
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static void renderGasStackInternal(Object gasStackObj, int x, int y, int width, int height) {
        if (!(gasStackObj instanceof mekanism.api.gas.GasStack)) return;

        mekanism.api.gas.GasStack gasStack = (mekanism.api.gas.GasStack) gasStackObj;
        mekanism.api.gas.Gas gas = gasStack.getGas();
        if (gas == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = gas.getSprite();

        // Set gas color using Mekanism's renderer
        mekanism.client.render.MekanismRenderer.color(gas);

        drawTexturedRect(x, y, sprite, width, height);

        // Reset color
        mekanism.client.render.MekanismRenderer.resetColor();
        GlStateManager.enableLighting();
        GlStateManager.enableBlend();
    }

    // ==================== Essentia Rendering ====================

    private static final String THAUMICENERGISTICS_MODID = "thaumicenergistics";

    /**
     * Render an essentia stack at the given position.
     */
    public static void renderEssentia(Object essentiaStackObj, int x, int y, int width, int height) {
        if (!Loader.isModLoaded(THAUMICENERGISTICS_MODID)) return;

        renderEssentiaInternal(essentiaStackObj, x, y, width, height);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    private static void renderEssentiaInternal(Object essentiaStackObj, int x, int y, int width, int height) {
        if (!(essentiaStackObj instanceof thaumicenergistics.api.EssentiaStack)) return;

        thaumicenergistics.api.EssentiaStack essentiaStack = (thaumicenergistics.api.EssentiaStack) essentiaStackObj;
        thaumcraft.api.aspects.Aspect aspect = essentiaStack.getAspect();
        if (aspect == null) return;

        Minecraft mc = Minecraft.getMinecraft();

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();

        // Bind the aspect's texture
        mc.getTextureManager().bindTexture(aspect.getImage());

        // Apply aspect color
        java.awt.Color c = new java.awt.Color(aspect.getColor());
        float r = c.getRed() / 255.0F;
        float g = c.getGreen() / 255.0F;
        float b = c.getBlue() / 255.0F;
        GlStateManager.color(r, g, b, 1.0F);

        // Draw the aspect icon using tessellator (aspect textures are full textures, not sprites)
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(x, y + height, 0).tex(0.0D, 1.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x + width, y + height, 0).tex(1.0D, 1.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x + width, y, 0).tex(1.0D, 0.0D).color(r, g, b, 1.0f).endVertex();
        buffer.pos(x, y, 0).tex(0.0D, 0.0D).color(r, g, b, 1.0f).endVertex();
        tess.draw();

        // Reset color and state
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    // ==================== Stack Size Rendering ====================

    /**
     * Render a stack size in the corner of a slot, using the same scaling as FluidStackSizeRenderer.
     * Works with any numeric stack size (for gas, essentia, or other resources).
     *
     * @param fontRenderer The font renderer
     * @param stackSize The stack size to render
     * @param xPos The x position of the slot
     * @param yPos The y position of the slot
     */
    public static void renderStackSize(FontRenderer fontRenderer, long stackSize, int xPos, int yPos) {
        if (stackSize <= 0) return;

        // Use the same scale factor as FluidStackSizeRenderer (small font for compactness)
        final float scaleFactor = 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        String stackSizeText = ReadableNumberConverter.INSTANCE.toSlimReadableForm(stackSize);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);

        final int X = (int) (((float) xPos + offset + 16.0f - fontRenderer.getStringWidth(stackSizeText) * scaleFactor) * inverseScaleFactor);
        final int Y = (int) (((float) yPos + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(stackSizeText, X, Y, 0xFFFFFF);

        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    // ==================== Shared Helpers ====================

    /**
     * Draw a textured rectangle using a sprite.
     * This handles proper UV mapping for animated textures.
     */
    public static void drawTexturedRect(int x, int y, TextureAtlasSprite sprite, int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0.0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y + height, 0.0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y, 0.0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(x, y, 0.0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();

        tessellator.draw();
    }
}
