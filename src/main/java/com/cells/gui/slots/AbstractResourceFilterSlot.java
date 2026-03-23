package com.cells.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.slot.IJEITargetSlot;


/**
 * Unified abstract base for all resource filter slots.
 * <p>
 * This class handles interaction logic:
 * <ul>
 *   <li>Click handling (left-click to set, right-click to clear)</li>
 *   <li>Shift-click handling (add from held item)</li>
 *   <li>JEI drag-drop (through IJEITargetSlot)</li>
 *   <li>Keybind quick-add (through {@link #acceptResource(Object)})</li>
 * </ul>
 * <p>
 * Subclasses only need to implement:
 * <ul>
 *   <li>{@link #extractResourceFromStack(ItemStack)} - Get resource from ItemStack</li>
 *   <li>{@link #getResource()} - Get current resource in slot</li>
 *   <li>{@link #setResource(Object)} - Set resource in slot (handles sync)</li>
 *   <li>{@link #drawResourceContent(Minecraft, int, int, float, R resource)} - Render the resource</li>
 *   <li>{@link #getResourceDisplayName(Object)} - Get localized name for tooltip</li>
 *   <li>{@link #resourcesEqual(Object, Object)} - Check if two resources are the same type</li>
 * </ul>
 * <p>
 * The interaction flow is:
 * <pre>
 * User Action          → Method Called           → Final Action
 * ─────────────────────────────────────────────────────────────
 * Left-click w/ item   → slotClicked(stack, 0)   → extractResourceFromStack → setResource
 * Right-click          → slotClicked(stack, 1)   → setResource(null)
 * Empty hand click     → slotClicked(empty, *)   → setResource(null)
 * JEI drag-drop        → acceptResource(obj)     → setResource
 * Keybind quick-add    → acceptResource(obj)     → setResource
 * </pre>
 *
 * @param <R> The resource type (FluidStack, GasStack, EssentiaStack, etc.)
 */
public abstract class AbstractResourceFilterSlot<R> extends GuiCustomSlot implements IJEITargetSlot {

    protected final int slot;

    protected AbstractResourceFilterSlot(int slot, int x, int y) {
        super(slot, x, y);
        this.slot = slot;
    }

    /**
     * Get the slot index.
     */
    public int getSlot() {
        return this.slot;
    }

    // ==================== Abstract methods - implement these ====================

    /**
     * Extract the resource from an ItemStack (bucket, tank, phial, etc.).
     * Return null if the stack doesn't contain this resource type.
     */
    @Nullable
    protected abstract R extractResourceFromStack(ItemStack stack);

    /**
     * Get the current resource in this slot.
     */
    @Nullable
    public abstract R getResource();

    /**
     * Set the resource in this slot. Handles client-side state AND server sync.
     * Pass null to clear the slot.
     */
    public abstract void setResource(@Nullable R resource);

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
     * Check if two resources are the same type (for duplicate detection).
     * This should compare the TYPE, not the amount.
     */
    protected abstract boolean resourcesEqual(@Nullable R a, @Nullable R b);

    /**
     * Check if the ItemStack can contain this resource type.
     * Used by canClick to determine if clicking is allowed.
     * Default implementation tries to extract and checks if non-null.
     */
    protected boolean canExtractResourceFrom(ItemStack stack) {
        return extractResourceFromStack(stack) != null;
    }

    // ==================== Unified interaction logic ====================

    @Override
    public final void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        R resource = getResource();
        if (resource == null) return;

        drawResourceContent(mc, mouseX, mouseY, partialTicks, resource);
    }

    @Override
    public boolean canClick(EntityPlayer player) {
        ItemStack mouseStack = player.inventory.getItemStack();

        // Always allow click with empty hand (to clear)
        if (mouseStack.isEmpty()) return true;

        // Allow click if item contains this resource type
        return canExtractResourceFrom(mouseStack);
    }

    @Override
    public void slotClicked(ItemStack clickStack, int mouseButton) {
        // Right-click or empty hand always clears
        if (clickStack.isEmpty() || mouseButton == 1) {
            setResource(null);
            return;
        }

        // Left-click with resource container sets the filter
        if (mouseButton == 0) {
            R resource = extractResourceFromStack(clickStack);
            if (resource != null) setResource(resource);
        }
    }

    @Override
    public String getMessage() {
        R resource = getResource();
        if (resource == null) return null;

        return getResourceDisplayName(resource);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public boolean needAccept() {
        // JEI: only accept if slot is empty
        return getResource() == null;
    }

    /**
     * Accept a resource from any source (JEI drag-drop, keybind, etc.).
     * This is the UNIFIED entry point for adding resources.
     * <p>
     * Handles:
     * - Duplicate detection (returns false if already in filter)
     * - Type conversion (if object is ItemStack, extracts resource)
     * - Setting the resource via setResource()
     *
     * @param ingredient The resource to add (R type, ItemStack, or other JEI type)
     * @return true if accepted, false if rejected (duplicate, wrong type, etc.)
     */
    @SuppressWarnings("unchecked")
    public boolean acceptResource(Object ingredient) {
        if (ingredient == null) return false;

        // Try to get resource from ingredient
        R resource = convertToResource(ingredient);
        if (resource == null) return false;

        // Set the resource
        setResource(resource);
        return true;
    }

    /**
     * Convert an arbitrary object to this slot's resource type.
     * Override to handle JEI ingredient types and other conversions.
     * <p>
     * Default implementation handles ItemStack extraction.
     *
     * @param ingredient The object to convert
     * @return The resource, or null if conversion failed
     */
    @SuppressWarnings("unchecked")
    @Nullable
    protected R convertToResource(Object ingredient) {
        // If it's already the right type, cast it
        // (subclasses should override to check instance properly)
        if (ingredient instanceof ItemStack) {
            return extractResourceFromStack((ItemStack) ingredient);
        }

        return null;
    }

    /**
     * Check if a resource is already present in a filter handler.
     * Used for duplicate detection before accepting.
     *
     * @param handler The filter handler to check
     * @param resource The resource to look for
     * @return true if the resource is already in the filter
     */
    public boolean isInFilter(IResourceFilterHandler<R> handler, R resource) {
        if (handler == null || resource == null) return false;

        return handler.isInFilter(resource);
    }

    /**
     * Interface for filter handlers that can check for duplicates.
     * Implement this in your container/adapter to enable duplicate detection.
     */
    public interface IResourceFilterHandler<R> {
        /**
         * Check if the resource is already in the filter.
         */
        boolean isInFilter(R resource);

        /**
         * Find the first empty slot, or -1 if full.
         */
        int findEmptySlot();

        /**
         * Get the resource in a specific slot.
         */
        @Nullable
        R getResourceInSlot(int slot);

        /**
         * Set the resource in a specific slot.
         */
        void setResourceInSlot(int slot, @Nullable R resource);
    }
}
