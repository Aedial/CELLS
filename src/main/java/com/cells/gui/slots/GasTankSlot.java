package com.cells.gui.slots;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.Optional;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.client.render.MekanismRenderer;

import appeng.helpers.InventoryAction;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;


/**
 * Unified gas tank slot implementation.
 * <p>
 * Displays gas contents in import/export interfaces.
 * Supports clicking with gas containers (IGasItem, GAS_HANDLER_CAPABILITY) to pour gas into import tanks.
 * (no universal gas container items exist).
 * Both import and export modes are read-only in the GUI.
 *
 * @param <H> The host interface type (must provide gas access methods)
 */
@Optional.Interface(iface = "mekanism.api.gas.IGasHandler", modid = "mekanism")
public class GasTankSlot<H extends GasTankSlot.IGasTankHost> extends AbstractResourceTankSlot<GasStack, H> {

    /**
     * Interface that hosts must implement to provide gas tank access.
     */
    public interface IGasTankHost {
        /**
         * Get the gas in the specified tank.
         * The amount is capped at Integer.MAX_VALUE for API compatibility.
         * Use {@link #getGasAmount(int)} for the true long amount.
         */
        @Nullable
        GasStack getGasInTank(int tankIndex);

        /**
         * Get the actual amount stored in the specified tank as a long.
         * This bypasses the int limitation of GasStack.amount.
         */
        long getGasAmount(int tankIndex);

        /**
         * Get the type name for localization (e.g., "gas").
         */
        String getTypeName();

        /**
         * Check if this is an export interface.
         */
        boolean isExport();
    }

    /**
     * Create a gas tank slot with pagination support.
     *
     * @param host The gas interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public GasTankSlot(
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
    public GasStack getResource() {
        return this.host.getGasInTank(getTankIndex());
    }

    @Override
    @Optional.Method(modid = "mekanism")
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, GasStack resource) {
        Gas gas = resource.getGas();
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
    }

    @Override
    @Optional.Method(modid = "mekanism")
    protected String getResourceDisplayName(GasStack resource) {
        Gas gas = resource.getGas();
        return gas != null ? gas.getLocalizedName() : "Unknown Gas";
    }

    @Override
    protected long getResourceAmount(GasStack resource) {
        // Use the host's getGasAmount for true long precision
        return this.host.getGasAmount(getTankIndex());
    }

    @Override
    protected String getTypeName() {
        return this.host.getTypeName();
    }

    @Override
    protected boolean handlePouring(ItemStack clickStack, int mouseButton) {
        // Send EMPTY_ITEM action to server via AE2's packet system
        // The container's doAction will handle the actual gas transfer
        // Works with IGasItem (Mekanism gas tanks) and GAS_HANDLER_CAPABILITY items
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
        // The container's doAction will handle the actual gas extraction
        // Works with IGasItem (Mekanism gas tanks) and GAS_HANDLER_CAPABILITY items
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
            InventoryAction.FILL_ITEM,
            getTankIndex(),
            0
        ));
        return true;
    }
}
