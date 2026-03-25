package com.cells.gui.slots;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.ResourceType;


/**
 * Unified item filter slot implementation.
 * <p>
 * Uses unified {@link PacketResourceSlot} for sync.
 * Supports pagination via page offset supplier.
 * <p>
 * Works with IAEItemStack for unified handling across all resource types.
 */
public class ItemFilterSlot extends AbstractResourceFilterSlot<IAEItemStack> {

    /**
     * Provider interface for getting item in a slot.
     */
    @FunctionalInterface
    public interface ItemProvider {
        @Nullable IAEItemStack getItem(int slot);
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
    protected IAEItemStack extractResourceFromStack(ItemStack stack) {
        // Convert ItemStack to IAEItemStack
        if (stack.isEmpty()) return null;
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
    }

    @Override
    protected boolean canExtractResourceFrom(ItemStack stack) {
        // Any non-empty item can be a filter
        return !stack.isEmpty();
    }

    @Override
    @Nullable
    public IAEItemStack getResource() {
        return this.provider.getItem(getSlot());
    }

    @Override
    public void setResource(@Nullable IAEItemStack resource) {
        CellsNetworkHandler.INSTANCE.sendToServer(
            new PacketResourceSlot(ResourceType.ITEM, getSlot(), resource)
        );
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, IAEItemStack resource) {
        // Render the item as a ghost (no count)
        ItemStack stack = resource.getDefinition();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemIntoGUI(stack, this.xPos(), this.yPos());
        RenderHelper.disableStandardItemLighting();
    }

    @Override
    protected String getResourceDisplayName(IAEItemStack resource) {
        return resource.getDefinition().getDisplayName();
    }

    @Override
    protected boolean resourcesEqual(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.equals(b);
    }

    @Override
    @Nullable
    public IAEItemStack convertToResource(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            ItemStack stack = (ItemStack) ingredient;
            if (stack.isEmpty()) return null;
            return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
        }
        if (ingredient instanceof IAEItemStack) {
            return (IAEItemStack) ingredient;
        }
        return null;
    }

    /**
     * Get the item ingredient for JEI integration.
     */
    public Object getIngredient() {
        IAEItemStack resource = getResource();
        return resource != null ? resource.getDefinition() : ItemStack.EMPTY;
    }
}
