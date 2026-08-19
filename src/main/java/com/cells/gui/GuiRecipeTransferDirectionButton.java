package com.cells.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;


/**
 * Tiny 2-state button used by filter GUIs to swap JEI recipe transfer behavior.
 * <p>
 * Interface GUIs use it to show which side receives recipe inputs. Creative cell
 * GUIs reuse the same compact sprite as an inputs-vs-outputs selector.
 */
public class GuiRecipeTransferDirectionButton extends GuiButton implements ITooltip {

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/jei_io_toggle.png");

    private static final int TEXTURE_SIZE = 32;

    private static final int WIDTH = 24;
    private static final int HEIGHT = 8;

    private final BooleanSupplier activeStateSupplier;
    private final Supplier<String> tooltipSupplier;

    public GuiRecipeTransferDirectionButton(
            int buttonId,
            int x,
            int y,
            BooleanSupplier activeStateSupplier,
            Supplier<String> tooltipSupplier) {
        super(buttonId, x, y, WIDTH, HEIGHT, "");
        this.activeStateSupplier = activeStateSupplier;
        this.tooltipSupplier = tooltipSupplier;
    }

    @Override
    public void drawButton(@Nonnull Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        boolean hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        int u = 0;
        int offset = this.activeStateSupplier.getAsBoolean() ? 2 * HEIGHT : 0;
        int v = offset + (hovered ? HEIGHT : 0);

        // See {@code DynamicTooltipTabButton#drawButton} for blend state rationale
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        GlStateManager.blendFunc(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        Gui.drawModalRectWithCustomSizedTexture(
            this.x, this.y,
            (float) u, (float) v,
            WIDTH, HEIGHT,
            TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    public String getMessage() {
        return this.tooltipSupplier != null ? this.tooltipSupplier.get() : "";
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}
