package com.cells.blocks.exportinterface;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot.hasCalculatedValidness;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotNormal;

import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.slots.PagedItemHandler;
import com.cells.util.ItemStackKey;


/**
 * Container for the Export Interface GUI.
 * Layout: 4 rows of (9 filter slots on top + 9 storage slots below)
 * Plus 4 upgrade slots on the right side.
 * <p>
 * Works with both TileExportInterface (block) and PartExportInterface (part).
 */
public class ContainerExportInterface extends AEBaseContainer {

    private final IExportInterfaceInventoryHost host;

    @GuiSync(0)
    public long maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    private final PagedItemHandler pagedFilterHandler;
    private final PagedItemHandler pagedStorageHandler;

    /**
     * Constructor for tile entity.
     */
    public ContainerExportInterface(final InventoryPlayer ip, final TileExportInterface tile) {
        this(ip, tile, tile);
    }

    /**
     * Constructor for part.
     */
    public ContainerExportInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IExportInterfaceInventoryHost) part, part instanceof TileEntity ? part : null);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerExportInterface(final InventoryPlayer ip, final IExportInterfaceInventoryHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Create paged handlers that offset based on current page
        this.pagedFilterHandler = new PagedItemHandler(
            host.getFilterInventory(),
            TileExportInterface.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );
        this.pagedStorageHandler = new PagedItemHandler(
            host.getStorageInventory(),
            TileExportInterface.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );

        // Add filter slots (ghost/fake slots) and storage slots
        // 4 rows of 9 pairs (filter on top, storage below)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;
                int storageY = filterY + 18;

                // Filter slot (ghost item) - uses paged handler
                this.addSlotToContainer(new SlotFilterExport(
                    this.pagedFilterHandler, slotIndex, xPos, filterY,
                    host, slotIndex, ip.player
                ));

                // Storage slot (items extracted from network, read-only display)
                this.addSlotToContainer(new SlotExportStorage(
                    this.pagedStorageHandler, slotIndex, xPos, storageY,
                    host, slotIndex
                ));
            }
        }

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < TileExportInterface.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (this.maxSlotSize != this.host.getMaxSlotSize()) this.maxSlotSize = this.host.getMaxSlotSize();
        if (this.pollingRate != this.host.getPollingRate()) this.pollingRate = this.host.getPollingRate();

        // Sync pagination state
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();
    }

    public IExportInterfaceInventoryHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Sets the current page for viewing, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int maxPage = this.totalPages - 1;
        if (page < 0) page = 0;
        if (page > maxPage) page = maxPage;

        this.currentPage = page;
        this.host.setCurrentPage(page);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    /**
     * Clear all filters and return all storage items to the network.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        // Refresh upgrade status when an upgrade slot changes
        if (s instanceof SlotUpgrade) host.refreshUpgrades();

        // Refresh filter map when a filter slot changes
        if (s instanceof SlotFilterExport) host.refreshFilterMap();
    }

    /**
     * Filter slot for export interface.
     * Prevents duplicate filters across slots.
     * Uses paged handlers to access the correct slot based on the current page.
     */
    private static class SlotFilterExport extends SlotFake {
        private final IExportInterfaceInventoryHost host;
        private final int localSlot;
        private final EntityPlayer player;

        public SlotFilterExport(PagedItemHandler filterHandler, int idx, int x, int y,
                                IExportInterfaceInventoryHost host, int localSlot, EntityPlayer player) {
            super(filterHandler, idx, x, y);
            this.host = host;
            this.localSlot = localSlot;
            this.player = player;
        }

        @Override
        public void putStack(ItemStack stack) {
            // Allow clearing the filter slot
            if (stack.isEmpty()) {
                super.putStack(stack);
                return;
            }

            // Prevent duplicate filters across ALL pages
            ItemStackKey newKey = ItemStackKey.of(stack);
            if (newKey == null) return;

            int actualSlot = ((PagedItemHandler) this.getItemHandler()).getActualSlotIndex(this.localSlot);
            for (int i = 0; i < host.getFilterInventory().getSlots(); i++) {
                if (i == actualSlot) continue; // Skip current slot

                ItemStackKey otherKey = ItemStackKey.of(host.getFilterInventory().getStackInSlot(i));
                if (otherKey != null && otherKey.equals(newKey)) {
                    if (!player.world.isRemote) {
                        player.sendMessage(new TextComponentTranslation("message.cells.export_interface.filter_duplicate"));
                    }
                    return;
                }
            }

            super.putStack(stack);
        }
    }

    /**
     * Storage slot for export interface - read-only display of items from network.
     * Allows extraction but not insertion.
     * Uses paged handlers to access the correct slot based on the current page.
     */
    private static class SlotExportStorage extends SlotNormal {
        private final IExportInterfaceInventoryHost host;
        private final int localSlot;

        public SlotExportStorage(PagedItemHandler storageHandler, int idx, int x, int y,
                                 IExportInterfaceInventoryHost host, int localSlot) {
            super(storageHandler, idx, x, y);
            this.host = host;
            this.localSlot = localSlot;
        }

        @Override
        public int getSlotStackLimit() {
            return host.getMaxSlotSize();
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            // Always report as valid to prevent AE2 from rendering a red overlay.
            // Insertion is already blocked at the IItemHandler level by the
            // underlying storage inventory's isItemValid() check.
            return hasCalculatedValidness.Valid;
        }
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IExportInterfaceInventoryHost host;

        public SlotUpgrade(IItemHandler inv, int idx, int x, int y, IExportInterfaceInventoryHost host) {
            super(inv, idx, x, y);
            this.host = host;
            this.setIIcon(13 * 16 + 15);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return host.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            return hasCalculatedValidness.Valid;
        }
    }
}
