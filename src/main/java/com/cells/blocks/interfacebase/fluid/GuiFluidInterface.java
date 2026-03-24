package com.cells.blocks.interfacebase.fluid;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.gui.slots.FluidFilterSlot;
import com.cells.gui.slots.FluidTankSlot;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.fluids.util.AEFluidStack;

import com.cells.gui.CellsGuiHandler;
import com.cells.gui.QuickAddHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for both Fluid Import Interface and Fluid Export Interface.
 * <p>
 * Uses unified slot classes from com.cells.gui.slots:
 * - {@link FluidFilterSlot} for filter configuration
 * - {@link FluidTankSlot} for tank status display
 * <p>
 * JEI integration is handled automatically by the base class.
 */
public class GuiFluidInterface extends AbstractResourceInterfaceGui<IFluidInterfaceHost, ContainerFluidInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerFluidInterface(inventoryPlayer, tile), (IFluidInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiFluidInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerFluidInterface(inventoryPlayer, part), (IFluidInterfaceHost) part);
    }

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
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_MAX_SLOT_SIZE : CellsGuiHandler.GUI_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_POLLING_RATE : CellsGuiHandler.GUI_POLLING_RATE;
    }

    @Override
    protected GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y) {
        return new FluidFilterSlot(
            this.host::getFilterFluid, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE
        );
    }

    @Override
    protected GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y) {
        return new FluidTankSlot<>(
            this.host, displaySlot, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE,
            () -> this.container.maxSlotSize
        );
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);

        if (fluid != null) {
            CellsNetworkHandler.INSTANCE.sendToServer(
                new PacketQuickAddFilter(ResourceType.FLUID, AEFluidStack.fromFluidStack(fluid))
            );
            return true;
        }

        QuickAddHelper.sendNoValidError("fluid");
        return true;
    }
}
