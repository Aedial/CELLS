package com.cells.gui.slots;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.helpers.InventoryAction;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;


/**
 * Unified fluid tank slot implementation.
 * <p>
 * Displays fluid contents in import/export interfaces.
 * Import mode: supports clicking with fluid containers to pour fluid into the tank.
 * Export mode: read-only display (tanks are filled from the ME network).
 *
 * @param <H> The host interface type (must provide fluid access methods)
 */
public class FluidTankSlot<H extends FluidTankSlot.IFluidTankHost> extends AbstractResourceTankSlot<FluidStack, H> {

    /**
     * Interface that hosts must implement to provide fluid tank access.
     */
    public interface IFluidTankHost {
        /**
         * Get the fluid in the specified tank.
         * The amount is capped at Integer.MAX_VALUE for API compatibility.
         * Use {@link #getFluidAmount(int)} for the true long amount.
         */
        @Nullable
        FluidStack getFluidInTank(int tankIndex);

        /**
         * Get the actual amount stored in the specified tank as a long.
         * This bypasses the int limitation of FluidStack.amount.
         */
        long getFluidAmount(int tankIndex);

        /**
         * Get the type name for localization (e.g., "fluid").
         */
        String getTypeName();

        /**
         * Check if this is an export interface.
         */
        boolean isExport();
    }

    /**
     * Create a fluid tank slot with pagination support.
     *
     * @param host The fluid interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public FluidTankSlot(
            H host,
            int displayTankIndex,
            int id,
            int x, int y,
            IntSupplier pageOffsetSupplier,
            LongSupplier maxSlotSizeSupplier) {
        super(host, displayTankIndex, id, x, y, pageOffsetSupplier, maxSlotSizeSupplier, host.isExport());
    }

    @Override
    @Nullable
    public FluidStack getResource() {
        return this.host.getFluidInTank(getTankIndex());
    }

    @Override
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, FluidStack resource) {
        Fluid fluidType = resource.getFluid();
        if (fluidType == null) return;
        if (fluidType.getStill() == null) return;

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluidType.getStill().toString());

        // Set fluid color
        int color = fluidType.getColor(resource);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        GlStateManager.color(red, green, blue);

        this.drawTexturedModalRect(this.xPos(), this.yPos(), sprite, getWidth(), getHeight());

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected String getResourceDisplayName(FluidStack resource) {
        return resource.getLocalizedName();
    }

    @Override
    protected long getResourceAmount(FluidStack resource) {
        // Use the host's getFluidAmount for true long precision
        return this.host.getFluidAmount(getTankIndex());
    }

    @Override
    protected String getTypeName() {
        return this.host.getTypeName();
    }

    @Override
    protected boolean handlePouring(ItemStack clickStack, int mouseButton) {
        // Send EMPTY_ITEM action to server via AE2's packet system
        // The container's doAction will handle the actual fluid transfer
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
            InventoryAction.EMPTY_ITEM,
            getTankIndex(),
            0
        ));
        return true;
    }

    @Override
    protected boolean handleFilling(ItemStack clickStack, int mouseButton) {
        // Send FILL_ITEM action to server via AE2's packet system
        // The container's doAction will handle the actual fluid extraction
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
            InventoryAction.FILL_ITEM,
            getTankIndex(),
            0
        ));
        return true;
    }
}
