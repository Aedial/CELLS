package com.cells.gui.slots;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.slot.IJEITargetSlot;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;


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

    /**
     * Optional callback invoked on right-click.
     * When set, right-click opens the per-slot size override GUI instead of clearing.
     */
    private Runnable rightClickHandler;

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

    /**
     * Set the right-click handler. When set, right-click invokes this instead of clearing.
     * Used by the GUI to open the per-slot size override GUI.
     */
    public void setRightClickHandler(Runnable handler) {
        this.rightClickHandler = handler;
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
        // Right-click: open per-slot size override GUI if handler is set
        if (mouseButton == 1) {
            if (this.rightClickHandler != null) this.rightClickHandler.run();
            return;
        }

        // Left-click: set or clear filter
        if (mouseButton == 0) {
            if (clickStack.isEmpty()) {
                setResource(null);
                return;
            }

            R resource = extractResourceFromStack(clickStack);
            if (resource != null) setResource(resource);
        }
    }

    @Override
    public String getMessage() {
        R resource = getResource();
        if (resource == null) return null;

        // Build the full tooltip: subclass-provided resource lines first
        // then our own click hints at the bottom.
        List<String> lines = new ArrayList<>(getResourceTooltipLines(resource));

        lines.add("");
        lines.add("§7" + I18n.format("cells.filter_slot.hint.left_click_1"));
        lines.add("§7" + I18n.format("cells.filter_slot.hint.left_click_2"));

        if (this.rightClickHandler != null) {
            lines.add("§7" + I18n.format("cells.filter_slot.hint.right_click"));
        }

        return String.join("\n", lines);
    }

    /**
     * Build the resource-specific tooltip lines (without click hints).
     * <p>
     * Default implementation produces a JEI-style tooltip:
     * <ol>
     *   <li>Resolve a JEI-friendly ingredient via {@link #getTooltipIngredient(Object)}
     *       (which defaults to {@link #getIngredient()}, already overridden by
     *       all standard filter slots).</li>
     *   <li>If it is an {@link ItemStack}, use the vanilla
     *       {@link ItemStack#getTooltip} which fires {@code ItemTooltipEvent}
     *       (so lines added by other mods are included).</li>
     *   <li>Otherwise, ask JEI's ingredient renderer for the tooltip (this
     *       mirrors what JEI shows on hover, including lines added by other
     *       mods through their JEI plugins).</li>
     *   <li>Fall back to {@link #getResourceDisplayName(Object)} if neither
     *       path produced anything.</li>
     * </ol>
     * The first line is rendered white by AE2's tooltip renderer; subsequent
     * lines are gray (unless they contain their own color codes).
     * <p>
     * Subclasses should only need to override {@link #getTooltipIngredient(Object)}.
     */
    @Nonnull
    protected List<String> getResourceTooltipLines(@Nonnull R resource) {
        Object ingredient = getTooltipIngredient(resource);

        // Path 1: ItemStack (uses vanilla tooltip, fires ItemTooltipEvent)
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            if (!stack.isEmpty()) {
                List<String> vanilla = getItemStackTooltip(stack);
                if (vanilla != null && !vanilla.isEmpty()) return vanilla;
            }
        }

        // Path 2: ask JEI for any other ingredient type (FluidStack, GasStack,
        // Aspect, ...) registered with its ingredient registry.
        if (ingredient != null) {
            List<String> jei = jeiTooltipOrNull(ingredient);
            if (jei != null && !jei.isEmpty()) return jei;
        }

        // Path 3: fallback to the display name.
        String name = getResourceDisplayName(resource);
        if (name == null) return Collections.emptyList();

        List<String> lines = new ArrayList<>(1);
        lines.add(name);
        return lines;
    }

    /**
     * Resolve the JEI-friendly ingredient for the given resource (the actual
     * {@link ItemStack}, {@link net.minecraftforge.fluids.FluidStack},
     * {@code GasStack}, {@code Aspect}, etc.).
     * <p>
     * Default returns {@code null}, which makes
     * {@link #getResourceTooltipLines(Object)} fall back to the display name.
     * Subclasses should override this to return the underlying JEI ingredient
     * so they get the full vanilla/JEI-style tooltip (including any lines
     * added by other mods).
     * <p>
     * Subclasses with non-trivial mappings (for example, when {@code R} is a
     * generic wrapper that may contain a dummy item standing in for a fluid
     * or gas) should override this to return the real underlying ingredient
     * so that JEI can produce the proper tooltip.
     */
    @Nullable
    protected Object getTooltipIngredient(@Nonnull R resource) {
        return null;
    }

    /**
     * Get the vanilla item tooltip for the given stack, which fires
     * {@code ItemTooltipEvent} and thus includes lines added by other mods
     * (JEI, Waila, etc.).
     * <p>
     * Wrapped defensively: a misbehaving mod can throw inside the tooltip
     * event handler, and we never want a hover to crash the GUI.
     */
    @Nonnull
    protected static List<String> getItemStackTooltip(@Nonnull ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return stack.getTooltip(mc.player, currentTooltipFlag());
        } catch (Throwable t) {
            // Fallback: just the display name
            return Collections.singletonList(stack.getDisplayName());
        }
    }

    /**
     * Get the JEI-rendered tooltip for an arbitrary ingredient (FluidStack,
     * GasStack, Aspect, etc.), or {@code null} if JEI is unavailable or the
     * ingredient type is not registered with JEI.
     * <p>
     * Resolution is performed by JEI itself, so the returned lines match
     * exactly what JEI shows on hover (including any lines other mods add
     * through their JEI plugins).
     */
    @Nullable
    protected static List<String> jeiTooltipOrNull(@Nullable Object ingredient) {
        if (ingredient == null) return null;
        if (!Loader.isModLoaded("jei")) return null;

        return jeiTooltipOrNullInternal(ingredient, currentTooltipFlag());
    }

    @Optional.Method(modid = "jei")
    @Nullable
    private static List<String> jeiTooltipOrNullInternal(@Nonnull Object ingredient, @Nonnull ITooltipFlag flag) {
        return com.cells.integration.jei.CellsJEIPlugin.getIngredientTooltip(ingredient, flag);
    }

    /**
     * Get the current tooltip flag based on the player's advanced-tooltips
     * setting (toggled by F3+H).
     */
    @Nonnull
    protected static ITooltipFlag currentTooltipFlag() {
        return Minecraft.getMinecraft().gameSettings.advancedItemTooltips
            ? ITooltipFlag.TooltipFlags.ADVANCED
            : ITooltipFlag.TooltipFlags.NORMAL;
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
     * <p>
     * Public so that GUIs can check if a slot can accept an ingredient type
     * (e.g., for unified JEI target creation).
     *
     * @param ingredient The object to convert
     * @return The resource, or null if conversion failed
     */
    @Nullable
    public R convertToResource(Object ingredient) {
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

    // ==================== JEI Integration ====================

    /**
     * Create a JEI ghost ingredient target for this slot.
     * <p>
     * This provides a unified way to create JEI targets for drag-drop support.
     * The GUI can call this method on each filter slot instead of manually
     * creating targets with duplicated code.
     * <p>
     * This method is only available when JEI is loaded.
     *
     * @return A JEI Target that can accept ingredients for this slot
     */
    @Optional.Method(modid = "jei")
    public Target<Object> createJEITarget(IntSupplier guiLeftSupplier, IntSupplier guiTopSupplier) {
        final AbstractResourceFilterSlot<R> self = this;

        return new Target<Object>() {
            @Override
            @Nonnull
            public Rectangle getArea() {
                return new Rectangle(
                    guiLeftSupplier.getAsInt() + self.xPos(),
                    guiTopSupplier.getAsInt() + self.yPos(),
                    self.getWidth(),
                    self.getHeight()
                );
            }

            @Override
            public void accept(@Nonnull Object ingredient) {
                self.acceptResource(ingredient);
            }
        };
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
