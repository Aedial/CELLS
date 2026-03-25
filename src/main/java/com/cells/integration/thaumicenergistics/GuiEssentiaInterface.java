package com.cells.integration.thaumicenergistics;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.GuiCustomSlot;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.EssentiaFilterSlot;
import com.cells.gui.slots.EssentiaTankSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for both Essentia Import Interface and Essentia Export Interface.
 * <p>
 * Uses unified slot classes from com.cells.gui.slots:
 * - {@link EssentiaFilterSlot} for filter configuration
 * - {@link EssentiaTankSlot} for tank status display
 * <p>
 * JEI integration is handled automatically by the base class.
 */
public class GuiEssentiaInterface extends AbstractResourceInterfaceGui<IEssentiaInterfaceHost, ContainerEssentiaInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiEssentiaInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerEssentiaInterface(inventoryPlayer, tile), (IEssentiaInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiEssentiaInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerEssentiaInterface(inventoryPlayer, part), (IEssentiaInterfaceHost) part);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    protected int getCurrentPage() {
        return this.container.currentPage;
    }

    @Override
    protected int getTotalPages() {
        return this.container.totalPages;
    }

    @Override
    protected long getMaxSlotSize() {
        return this.container.maxSlotSize;
    }

    @Override
    protected long getPollingRate() {
        return this.container.pollingRate;
    }

    @Override
    protected void nextPage() {
        this.container.nextPage();
    }

    @Override
    protected void prevPage() {
        this.container.prevPage();
    }

    @Override
    protected int getMaxSlotSizeGuiId() {
        return this.host.isPart()
            ? EssentiaInterfaceGuiHandler.GUI_PART_ESSENTIA_MAX_SLOT_SIZE
            : EssentiaInterfaceGuiHandler.GUI_ESSENTIA_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart()
            ? EssentiaInterfaceGuiHandler.GUI_PART_ESSENTIA_POLLING_RATE
            : EssentiaInterfaceGuiHandler.GUI_ESSENTIA_POLLING_RATE;
    }

    @Override
    protected GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y) {
        return new EssentiaFilterSlot(
            this.container::getClientFilterEssentia, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE
        );
    }

    @Override
    protected GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y) {
        return new EssentiaTankSlot<>(
            this.host, displaySlot, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE,
            () -> this.container.maxSlotSize
        );
    }

    /**
     * Handle quick add when hovering over a slot in another GUI (like JEI).
     * Converts the hovered ingredient to essentia and sends as a filter.
     */
    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        // Get essentia from hovered slot using the same method as creative cells
        EssentiaStack essentia = QuickAddHelper.getEssentiaUnderCursor(hoveredSlot);

        if (essentia != null) {
            // Send packet to add this essentia as a filter
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(
                ResourceType.ESSENTIA, AEEssentiaStack.fromEssentiaStack(essentia)
            ));
            return true;
        }

        // Show error if there was something under cursor (slot or JEI) that wasn't essentia
        if (QuickAddHelper.hasAnythingUnderCursor(hoveredSlot)) {
            QuickAddHelper.sendNoValidError("essentia");
        }

        return true;
    }
}
