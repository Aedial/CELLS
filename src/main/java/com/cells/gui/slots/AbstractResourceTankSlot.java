package com.cells.gui.slots;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiCustomSlot;

import com.cells.gui.ResourceRenderer;


/**
 * Unified abstract base for all resource tank slots (fluid, gas, etc.).
 * <p>
 * Tank slots display the current storage contents in an interface.
 * They are typically read-only (export mode) or allow pouring in (import mode for fluids).
 * <p>
 * Subclasses only need to implement:
 * <ul>
 *   <li>{@link #getResource()} - Get current resource in the tank</li>
 *   <li>{@link #drawResourceContent(Minecraft, int, int, float, Object)} - Render the resource</li>
 *   <li>{@link #getResourceDisplayName(Object)} - Get localized name for tooltip</li>
 *   <li>{@link #getResourceAmount(Object)} - Get the amount in the resource stack</li>
 *   <li>{@link #handlePouring(ItemStack, int)} - Handle fluid/gas pouring (optional)</li>
 * </ul>
 *
 * @param <R> The resource type (FluidStack, GasStack, etc.)
 * @param <H> The host interface type
 */
public abstract class AbstractResourceTankSlot<R, H> extends GuiCustomSlot {

    protected final H host;
    protected final int displayTankIndex;
    protected final IntSupplier pageOffsetSupplier;
    protected final LongSupplier maxSlotSizeSupplier;
    protected final boolean isExport;
    protected FontRenderer fontRenderer;

    /**
     * Create a tank slot with pagination support.
     *
     * @param host The interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     * @param isExport Whether this is an export interface (read-only)
     */
    protected AbstractResourceTankSlot(
            H host,
            int displayTankIndex,
            int id,
            int x, int y,
            IntSupplier pageOffsetSupplier,
            LongSupplier maxSlotSizeSupplier,
            boolean isExport) {
        super(id, x, y);
        this.host = host;
        this.displayTankIndex = displayTankIndex;
        this.pageOffsetSupplier = pageOffsetSupplier;
        this.maxSlotSizeSupplier = maxSlotSizeSupplier;
        this.isExport = isExport;
    }

    // ==================== Abstract methods - implement these ====================

    /**
     * Get the current resource in this tank.
     */
    @Nullable
    public abstract R getResource();

    /**
     * Render the resource content at the slot position.
     * Called only when getResource() returns non-null.
     */
    protected abstract void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, R resource);

    /**
     * Get the localized display name for the resource (for tooltip).
     */
    protected abstract String getResourceDisplayName(R resource);

    /**
     * Get the amount/quantity in the resource stack.
     */
    protected abstract long getResourceAmount(R resource);

    /**
     * Get the type name for localization (e.g., "fluid", "gas").
     */
    protected abstract String getTypeName();

    /**
     * Handle pouring from an item into this tank.
     * Override in subclasses that support pouring (e.g., fluid tanks in import mode).
     * Default implementation does nothing (read-only).
     *
     * @param clickStack The ItemStack clicked with
     * @param mouseButton The mouse button used
     * @return true if handled, false to pass to default
     */
    protected boolean handlePouring(ItemStack clickStack, int mouseButton) {
        return false;
    }

    // ==================== Common implementation ====================

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
    public final void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        R resource = getResource();
        if (resource == null) return;
        if (getResourceAmount(resource) <= 0) return;

        drawResourceContent(mc, mouseX, mouseY, partialTicks, resource);

        // Render stack size in corner (consistent across all tank types)
        if (this.fontRenderer != null) {
            ResourceRenderer.renderStackSize(this.fontRenderer, getResourceAmount(resource), this.xPos(), this.yPos());
        }
    }

    @Override
    public String getMessage() {
        R resource = getResource();
        if (resource == null) return null;

        long amount = getResourceAmount(resource);
        if (amount <= 0) return null;

        // Format: "Resource Name\n1,234 / 16,000 unit"
        long capacity = this.maxSlotSizeSupplier.getAsLong();
        String unit = I18n.format("cells.unit." + getTypeName());
        String amountText = I18n.format("tooltip.cells.amount", amount, capacity, unit);

        return getResourceDisplayName(resource) + "\n" + amountText;
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
        // Check for shift-click first
        if (isShiftKeyDown()) {
            handleShiftClick(clickStack, mouseButton);
            return;
        }

        if (this.isExport) {
            // Export tanks support extraction into containers
            handleFilling(clickStack, mouseButton);
        } else {
            // Import tanks support pouring from containers
            handlePouring(clickStack, mouseButton);
        }
    }

    /**
     * Check if shift key is held down.
     */
    private static boolean isShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /**
     * Handle shift-click for quick transfer.
     * Override in subclasses to implement type-specific behavior.
     *
     * @param clickStack The ItemStack clicked with
     * @param mouseButton The mouse button used
     * @return true if handled, false to pass to default
     */
    protected boolean handleShiftClick(ItemStack clickStack, int mouseButton) {
        return false;
    }

    /**
     * Handle filling a container from this tank (export mode).
     * Override in subclasses that support extraction.
     * Default implementation does nothing (read-only).
     *
     * @param clickStack The ItemStack clicked with
     * @param mouseButton The mouse button used
     * @return true if handled, false to pass to default
     */
    protected boolean handleFilling(ItemStack clickStack, int mouseButton) {
        return false;
    }
}
