package com.cells.integration.mekanismenergistics;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.GuiCustomSlot;

import com.mekeng.github.common.me.data.impl.AEGasStack;

import mekanism.api.gas.GasStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.GasFilterSlot;
import com.cells.gui.slots.GasTankSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for both Gas Import Interface and Gas Export Interface.
 * <p>
 * Uses unified slot classes from com.cells.gui.slots:
 * - {@link GasFilterSlot} for filter configuration
 * - {@link GasTankSlot} for tank status display
 * <p>
 * JEI integration is handled automatically by the base class.
 */
public class GuiGasInterface extends AbstractResourceInterfaceGui<IGasInterfaceHost, ContainerGasInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiGasInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerGasInterface(inventoryPlayer, tile), (IGasInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiGasInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerGasInterface(inventoryPlayer, part), (IGasInterfaceHost) part);
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
            ? GasInterfaceGuiHandler.GUI_PART_GAS_MAX_SLOT_SIZE
            : GasInterfaceGuiHandler.GUI_GAS_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart()
            ? GasInterfaceGuiHandler.GUI_PART_GAS_POLLING_RATE
            : GasInterfaceGuiHandler.GUI_GAS_POLLING_RATE;
    }

    @Override
    protected GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y) {
        return new GasFilterSlot(
            this.container::getClientFilterGas, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE
        );
    }

    @Override
    protected GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y) {
        return new GasTankSlot<>(
            this.host, displaySlot, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE,
            () -> this.container.maxSlotSize
        );
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        GasStack gas = QuickAddHelper.getGasUnderCursor(hoveredSlot);

        if (gas != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(
                new PacketQuickAddFilter(ResourceType.GAS, AEGasStack.of(gas))
            );
            return true;
        }

        // Show error if there was something under cursor (slot or JEI) that wasn't a gas
        if (QuickAddHelper.hasAnythingUnderCursor(hoveredSlot)) {
            QuickAddHelper.sendNoValidError("gas");
        }

        return true;
    }
}
