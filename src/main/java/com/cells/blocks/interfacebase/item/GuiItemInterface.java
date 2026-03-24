package com.cells.blocks.interfacebase.item;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;
import appeng.client.gui.widgets.GuiCustomSlot;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceGui;
import com.cells.gui.CellsGuiHandler;
import com.cells.gui.QuickAddHelper;
import com.cells.gui.slots.ItemFilterSlot;
import com.cells.gui.slots.ItemStorageSlot;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;


/**
 * GUI for both Item Import Interface and Item Export Interface.
 * <p>
 * Uses unified slot classes from com.cells.gui.slots:
 * - {@link ItemFilterSlot} for filter configuration (GuiCustomSlot)
 * - {@link ItemStorageSlot} for storage display and interaction (GuiCustomSlot)
 * <p>
 * JEI integration is handled automatically by the base class.
 */
public class GuiItemInterface extends AbstractResourceInterfaceGui<IItemInterfaceHost, ContainerItemInterface> {

    /**
     * Constructor for tile entity.
     */
    public GuiItemInterface(final InventoryPlayer inventoryPlayer, final TileEntity tile) {
        super(new ContainerItemInterface(inventoryPlayer, tile), (IItemInterfaceHost) tile);
    }

    /**
     * Constructor for part.
     */
    public GuiItemInterface(final InventoryPlayer inventoryPlayer, final IPart part) {
        super(new ContainerItemInterface(inventoryPlayer, part), (IItemInterfaceHost) part);
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
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_MAX_SLOT_SIZE : CellsGuiHandler.GUI_MAX_SLOT_SIZE;
    }

    @Override
    protected int getPollingRateGuiId() {
        return this.host.isPart() ? CellsGuiHandler.GUI_PART_POLLING_RATE : CellsGuiHandler.GUI_POLLING_RATE;
    }

    @Override
    protected GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y) {
        return new ItemFilterSlot(this.host::getFilter,
            displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE
        );
    }

    @Override
    protected GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y) {
        return new ItemStorageSlot<>(
            this.host, displaySlot, displaySlot, x, y,
            () -> this.container.currentPage * SLOTS_PER_PAGE,
            () -> this.container.maxSlotSize
        );
    }

    @Override
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);

        if (!item.isEmpty()) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketQuickAddFilter(ResourceType.ITEM, item));
            return true;
        }

        return false;
    }
}
