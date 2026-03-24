package com.cells.gui.slots;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Unified item filter slot implementation.
 * <p>
 * Uses unified {@link PacketResourceSlot} for sync.
 * Supports pagination via page offset supplier.
 * <p>
 * Unlike Fluid/Gas slots, ItemFilterSlot handles ItemStack directly
 * (no AE wrapper needed for filter display).
 */
public class ItemFilterSlot extends AbstractResourceFilterSlot<ItemStack> {

    /**
     * Provider interface for getting item in a slot.
     */
    @FunctionalInterface
    public interface ItemProvider {
        @Nullable ItemStack getItem(int slot);
    }

    private final ItemProvider provider;
    private final IntSupplier pageOffsetSupplier;

    /**
     * Create an item filter slot with pagination support.
     */
    public ItemFilterSlot(ItemProvider provider, int displaySlot, int x, int y, IntSupplier pageOffsetSupplier) {
        super(displaySlot, x, y);
        this.provider = provider;
        this.pageOffsetSupplier = pageOffsetSupplier;
    }

    /**
     * Create an item filter slot without pagination.
     */
    public ItemFilterSlot(ItemProvider provider, int slot, int x, int y) {
        this(provider, slot, x, y, () -> 0);
    }

    @Override
    public int getSlot() {
        return this.slot + this.pageOffsetSupplier.getAsInt();
    }

    @Override
    @Nullable
    protected ItemStack extractResourceFromStack(ItemStack stack) {
        // For items, the stack IS the resource
        return stack.isEmpty() ? null : stack.copy();
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        // Any non-empty item can be a filter
        return !stack.isEmpty();
    }

    @Override
    @Nullable
    public ItemStack getResource() {
        ItemStack stack = this.provider.getItem(getSlot());
        return (stack == null || stack.isEmpty()) ? null : stack;
    }

    @Override
    public void setResource(@Nullable ItemStack resource) {
        // Normalize empty stacks to null
        ItemStack toSend = (resource == null || resource.isEmpty()) ? null : resource;

        // Send packet to server using unified resource sync
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.ITEM, getSlot(), toSend)
        );
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, ItemStack resource) {
        // Render the item as a ghost (no count)
        // Note: Do NOT enable depth testing here - filter overlays must render flat
        // so they don't appear above the item in cursor
        RenderHelper.enableGUIStandardItemLighting();

        mc.getRenderItem().renderItemIntoGUI(resource, this.xPos(), this.yPos());

        RenderHelper.disableStandardItemLighting();
    }

    @Override
    protected String getResourceDisplayName(ItemStack resource) {
        return resource.getDisplayName();
    }

    @Override
    protected boolean resourcesEqual(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        if (b == null || b.isEmpty()) return false;

        // Compare item and metadata, ignore count
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    @Override
    @Nullable
    public ItemStack convertToResource(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            return stack.isEmpty() ? null : stack.copy();
        }
        return null;
    }

    /**
     * Get the item ingredient for JEI integration.
     */
    public Object getIngredient() {
        return getResource();
    }
}
