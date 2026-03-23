package com.cells.integration.mekanismenergistics;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiCustomSlot;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.render.MekanismRenderer;

import com.cells.gui.ResourceRenderer;


/**
 * GUI slot for rendering gas tanks in Gas Import and Export Interfaces.
 * <p>
 * Import mode: read-only display (no gas pouring support, unlike fluids)
 * Export mode: read-only display (tanks are filled from the ME network)
 * <p>
 * Supports pagination via a page offset supplier, allowing the displayed tank to map
 * to a different actual tank index based on the current page.
 */
public class GuiGasTankSlot extends GuiCustomSlot {

    private final IGasInterfaceHost host;
    private final int displayTankIndex;
    private final IntSupplier pageOffsetSupplier;
    private final LongSupplier maxSlotSizeSupplier;
    private final boolean isExport;
    private FontRenderer fontRenderer;

    /**
     * Create a tank slot with pagination support.
     *
     * @param host The gas interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public GuiGasTankSlot(IGasInterfaceHost host,
                          int displayTankIndex, int id, int x, int y,
                          IntSupplier pageOffsetSupplier,
                          LongSupplier maxSlotSizeSupplier) {
        super(id, x, y);
        this.host = host;
        this.displayTankIndex = displayTankIndex;
        this.pageOffsetSupplier = pageOffsetSupplier;
        this.maxSlotSizeSupplier = maxSlotSizeSupplier;
        this.isExport = host.isExport();
    }

    /**
     * Get the actual tank index in the storage (display tank + page offset).
     */
    public int getTankIndex() {
        return this.displayTankIndex + this.pageOffsetSupplier.getAsInt();
    }

    /**
     * Set the font renderer for stack size rendering.
     * Must be called after construction.
     */
    public void setFontRenderer(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        GasStack gasStack = this.host.getGasInTank(this.getTankIndex());
        if (gasStack == null || gasStack.amount <= 0) return;

        Gas gas = gasStack.getGas();
        if (gas == null) return;

        TextureAtlasSprite sprite = gas.getSprite();
        if (sprite == null) return;

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        // Set gas color
        MekanismRenderer.color(gas);

        this.drawTexturedModalRect(this.xPos(), this.yPos(), sprite, getWidth(), getHeight());

        MekanismRenderer.resetColor();

        // Render stack size in corner (same as fluid tanks for consistency)
        // Use ResourceRenderer for properly scaled text
        if (this.fontRenderer != null) {
            ResourceRenderer.renderStackSize(this.fontRenderer, gasStack.amount, this.xPos(), this.yPos());
        }
    }

    @Override
    public String getMessage() {
        GasStack gasStack = this.host.getGasInTank(this.getTankIndex());
        if (gasStack == null || gasStack.amount <= 0) return null;

        // Format: "Gas Name\n1,234 / 16,000 mB"
        long capacity = this.maxSlotSizeSupplier.getAsLong();
        String unit = I18n.format("cells.unit." + host.getTypeName());
        String amountText = I18n.format("tooltip.cells.amount", gasStack.amount, capacity, unit);

        return gasStack.getGas().getLocalizedName() + "\n" + amountText;
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
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void slotClicked(ItemStack clickStack, int mouseButton) {
        // Gas tanks don't support clicking - no universal gas container items
        // Import and export tanks are both read-only in the GUI
    }

    public GasStack getGasStack() {
        return this.host.getGasInTank(this.getTankIndex());
    }
}
