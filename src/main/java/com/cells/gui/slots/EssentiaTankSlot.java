package com.cells.gui.slots;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.EssentiaStack;

import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

import com.cells.gui.ResourceRenderer;


/**
 * Unified essentia tank slot implementation.
 * <p>
 * Displays essentia contents in import/export interfaces.
 * Supports clicking with essentia containers (phials, jars) to pour essentia into import tanks.
 */
@Optional.Interface(iface = "thaumcraft.api.aspects.IAspectContainer", modid = "thaumicenergistics")
public class EssentiaTankSlot<H extends EssentiaTankSlot.IEssentiaTankHost> extends AbstractResourceTankSlot<EssentiaStack, H> {

    /**
     * Interface that hosts must implement to provide essentia tank access.
     */
    public interface IEssentiaTankHost {
        /**
         * Get the essentia in the specified slot.
         * The amount is capped at Integer.MAX_VALUE for API compatibility.
         * Use {@link #getEssentiaAmount(int)} for the true long amount.
         */
        @Nullable
        EssentiaStack getEssentiaInSlot(int slotIndex);

        /**
         * Get the actual amount stored in the specified slot as a long.
         * This bypasses the int limitation of EssentiaStack.getAmount().
         */
        long getEssentiaAmount(int slotIndex);

        /**
         * Get the type name for localization (e.g., "essentia").
         */
        String getTypeName();

        /**
         * Check if this is an export interface.
         */
        boolean isExport();
    }

    /**
     * Create an essentia tank slot with pagination support.
     *
     * @param host The essentia interface host
     * @param displayTankIndex The display tank index (0-35 for one page)
     * @param id The slot ID for the GUI
     * @param x X position in GUI
     * @param y Y position in GUI
     * @param pageOffsetSupplier Supplier that returns the current page's starting slot index
     * @param maxSlotSizeSupplier Supplier for the synced max slot size (from container)
     */
    public EssentiaTankSlot(
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
    public EssentiaStack getResource() {
        return this.host.getEssentiaInSlot(getTankIndex());
    }

    @Override
    @Optional.Method(modid = "thaumicenergistics")
    protected void drawResourceContent(Minecraft mc, int mouseX, int mouseY, float partialTicks, EssentiaStack resource) {
        ResourceRenderer.renderEssentia(resource, this.xPos(), this.yPos(), getWidth(), getHeight());
    }

    @Override
    @Optional.Method(modid = "thaumicenergistics")
    protected String getResourceDisplayName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getName() : "Unknown Essentia";
    }

    @Override
    protected long getResourceAmount(EssentiaStack resource) {
        // Use the host's getEssentiaAmount for true long precision
        return this.host.getEssentiaAmount(getTankIndex());
    }

    @Override
    protected String getTypeName() {
        return this.host.getTypeName();
    }

    @Override
    protected boolean handlePouring(ItemStack clickStack, int mouseButton) {
        // Send EMPTY_ITEM action to server via AE2's packet system
        // The container's doAction will handle the actual essentia transfer
        // Works with phials, jars, and essentia containers
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
        // The container's doAction will handle the actual essentia extraction
        // Works with phials, jars, and essentia containers
        NetworkHandler.instance().sendToServer(new PacketInventoryAction(
            InventoryAction.FILL_ITEM,
            getTankIndex(),
            0
        ));
        return true;
    }
}
