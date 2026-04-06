package com.cells.gui;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;


/**
 * A 16x16 button that renders an ItemStack icon on a button background from AE2's states.png.
 * <p>
 * Used in the interface GUI to open the Pull/Push Card configuration when a card is
 * installed in the upgrade slots. When disabled (no card present), shows a grayed-out
 * state with a tooltip prompting the user to insert the appropriate card.
 * When enabled and hovered, shows a lighter overlay for visual feedback.
 * <p>
 * The button background is drawn from AE2's states.png at row 16, col 16 (1-indexed).
 */
public class GuiPullPushUpgradeButton extends GuiButton implements ITooltip {

    private static final ResourceLocation BACKGROUND =
        new ResourceLocation(Tags.MODID, "textures/guis/button_background.png");

    private static final int BACKGROUND_WIDTH = 64;
    private static final int BACKGROUND_HEIGHT = 64;
    private static final int BACKGROUND_X_OFFSET = 18;
    private static final int BUTTON_SIZE = 20;

    enum States {
        DISABLED,
        ENABLED,
        HOVERED
    }

    private final Supplier<String> tooltipSupplier;
    private final RenderItem itemRenderer;

    /** The card ItemStack to render as the icon. Empty means no card present. */
    private ItemStack cardStack = ItemStack.EMPTY;

    /**
     * Create a Pull/Push upgrade button.
     *
     * @param buttonId        Button ID for event routing
     * @param x               Screen X position
     * @param y               Screen Y position
     * @param tooltipSupplier Supplier for dynamic tooltip text
     * @param itemRenderer    Item renderer for drawing the card icon
     */
    public GuiPullPushUpgradeButton(int buttonId, int x, int y,
                                    Supplier<String> tooltipSupplier,
                                    RenderItem itemRenderer) {
        super(buttonId, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
        this.tooltipSupplier = tooltipSupplier;
        this.itemRenderer = itemRenderer;
    }

    /**
     * Update the card stack displayed by this button.
     * When empty, the button appears disabled (grayed out).
     *
     * @param stack The Pull/Push card ItemStack, or ItemStack.EMPTY if none
     */
    public void setCardStack(ItemStack stack) {
        this.cardStack = stack != null ? stack : ItemStack.EMPTY;
    }

    public ItemStack getCardStack() {
        return this.cardStack;
    }

    @Override
    public void drawButton(@Nonnull Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        boolean isEnabled = this.enabled && !this.cardStack.isEmpty();

        States state = States.DISABLED;
        if (isEnabled) state = this.hovered ? States.HOVERED : States.ENABLED;

        int bg_v = state.ordinal() * BUTTON_SIZE;

        // Draw the button background from AE2's states.png
        mc.getTextureManager().bindTexture(BACKGROUND);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        Gui.drawModalRectWithCustomSizedTexture(
            this.x, this.y, BACKGROUND_X_OFFSET, bg_v, BUTTON_SIZE, BUTTON_SIZE,
            BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        GlStateManager.disableBlend();

        if (isEnabled) {
            // Draw the card's item icon
            this.zLevel = 100.0F;
            this.itemRenderer.zLevel = 100.0F;

            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRenderer.renderItemAndEffectIntoGUI(this.cardStack, this.x + 2, this.y + 2);
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();

            this.itemRenderer.zLevel = 0.0F;
            this.zLevel = 0.0F;
        }
    }

    // ============================== ITooltip ==============================

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
