package com.cells.blocks.importinterface;

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

import com.cells.gui.slots.PagedItemHandler;
import com.cells.util.ItemStackKey;

import javax.annotation.Nonnull;


/**
 * Container for the Import Interface GUI.
 * Layout: 4 rows of (9 filter slots on top + 9 storage slots below)
 * Plus 4 upgrade slots on the right side.
 * Supports pagination via Capacity Cards - each card adds a page of 36 slots.
 * <p>
 * Works with both TileImportInterface (block) and PartImportInterface (part).
 */
public class ContainerImportInterface extends AEBaseContainer {

    private final IImportInterfaceInventoryHost host;

    @GuiSync(0)
    public long maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    // Paged handlers for filter and storage inventories
    private final PagedItemHandler pagedFilterHandler;
    private final PagedItemHandler pagedStorageHandler;

    /**
     * Constructor for tile entity.
     */
    public ContainerImportInterface(final InventoryPlayer ip, final TileImportInterface tile) {
        this(ip, tile, tile);
    }

    /**
     * Constructor for part.
     */
    public ContainerImportInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IImportInterfaceInventoryHost) part, part instanceof TileEntity ? part : null);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerImportInterface(final InventoryPlayer ip, final IImportInterfaceInventoryHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Create paged handlers that offset based on current page
        this.pagedFilterHandler = new PagedItemHandler(
            host.getFilterInventory(),
            TileImportInterface.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );
        this.pagedStorageHandler = new PagedItemHandler(
            host.getStorageInventory(),
            TileImportInterface.SLOTS_PER_PAGE,
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
                this.addSlotToContainer(new SlotFilterLocked(
                    this.pagedFilterHandler, slotIndex, xPos, filterY,
                    host, this.pagedStorageHandler, slotIndex, ip.player
                ));

                // Storage slot (actual items, bottom part)
                this.addSlotToContainer(new SlotImportStorage(
                    this.pagedStorageHandler, slotIndex, xPos, storageY,
                    host, slotIndex
                ));
            }
        }

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < TileImportInterface.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(),
                i,
                186,
                25 + i * 18,
                host
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
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();
    }

    public IImportInterfaceInventoryHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Set the current page index and notify the host.
     */
    public void setCurrentPage(int page) {
        int newPage = Math.max(0, Math.min(page, this.totalPages - 1));
        this.currentPage = newPage;
        this.host.setCurrentPage(newPage);
    }

    /**
     * Go to the next page if available.
     */
    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    /**
     * Go to the previous page if available.
     */
    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    /**
     * Clear all filters that don't have items in their storage slots.
     * For Import Interface, we cannot clear filters where items exist
     * because that would orphan the items.
     */
    public void clearFilters() {
        // Clear filters across ALL pages, not just the current page
        int totalSlots = this.totalPages * TileImportInterface.SLOTS_PER_PAGE;
        for (int i = 0; i < totalSlots; i++) {
            // Only clear filter if storage slot is empty
            if (i < this.host.getStorageInventory().getSlots() &&
                this.host.getStorageInventory().getStackInSlot(i).isEmpty()) {
                if (i < this.host.getFilterInventory().getSlots()) {
                    this.host.getFilterInventory().setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }

        this.host.refreshFilterMap();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        // Refresh upgrade status when an upgrade slot changes
        if (s instanceof SlotUpgrade) host.refreshUpgrades();

        // Refresh filter map when a filter slot changes (for slotless storage lookups)
        if (s instanceof SlotFilterLocked) host.refreshFilterMap();
    }

    /**
     * Filter slot that prevents changes when the corresponding storage slot has items.
     * This prevents orphaning items that no longer match any filter.
     * Sends chat warnings to the player when a filter change is rejected.
     * <p>
     * Uses paged handlers to access the correct slot based on the current page.
     */
    private static class SlotFilterLocked extends SlotFake {
        private final IImportInterfaceInventoryHost host;
        private final PagedItemHandler storageHandler;
        private final int localSlot;
        private final EntityPlayer player;

        public SlotFilterLocked(PagedItemHandler filterHandler, int idx, int x, int y,
                                IImportInterfaceInventoryHost host, PagedItemHandler storageHandler,
                                int localSlot, EntityPlayer player) {
            super(filterHandler, idx, x, y);
            this.host = host;
            this.storageHandler = storageHandler;
            this.localSlot = localSlot;
            this.player = player;
        }

        @Override
        public void putStack(ItemStack stack) {
            // Prevent filter changes if there are items in the corresponding storage slot
            ItemStack storageStack = storageHandler.getStackInSlot(this.localSlot);
            if (!storageStack.isEmpty()) {
                if (!player.world.isRemote) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_interface.storage_not_empty"));
                }
                return;
            }

            // Allow clearing the filter slot by clicking with an empty hand
            if (stack.isEmpty()) {
                super.putStack(stack);
                return;
            }

            // Prevent duplicate filters by checking if the new filter item already exists in another slot
            // Check all slots across ALL pages, not just the current page
            ItemStackKey newKey = ItemStackKey.of(stack);
            if (newKey == null) return;

            int actualSlot = ((PagedItemHandler) this.getItemHandler()).getActualSlotIndex(this.localSlot);
            for (int i = 0; i < host.getFilterInventory().getSlots(); i++) {
                if (i == actualSlot) continue; // Skip current slot

                ItemStackKey otherKey = ItemStackKey.of(host.getFilterInventory().getStackInSlot(i));
                if (otherKey != null && otherKey.equals(newKey)) {
                    if (!player.world.isRemote) {
                        player.sendMessage(new TextComponentTranslation("message.cells.import_interface.filter_duplicate"));
                    }
                    return; // Duplicate found, do not allow
                }
            }

            super.putStack(stack);
        }
    }

    /**
     * Custom slot for storage that respects the filter.
     * Uses paged handlers to access the correct slot based on the current page.
     */
    private static class SlotImportStorage extends SlotNormal {
        private final IImportInterfaceInventoryHost host;
        private final int localSlot;

        public SlotImportStorage(PagedItemHandler storageHandler, int idx, int x, int y,
                                  IImportInterfaceInventoryHost host, int localSlot) {
            super(storageHandler, idx, x, y);
            this.host = host;
            this.localSlot = localSlot;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            // Get the actual slot index for validation
            int actualSlot = ((PagedItemHandler) this.getItemHandler()).getActualSlotIndex(this.localSlot);
            return host.isItemValidForSlot(actualSlot, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return host.getMaxSlotSize();
        }
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     * Uses the same icon as AE2 UPGRADES (13 * 16 + 15 = 223) for empty slot background at 40% opacity.
     * Always reports as Valid to prevent red background rendering for custom upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IImportInterfaceInventoryHost host;

        public SlotUpgrade(IItemHandler inv, int idx, int x, int y, IImportInterfaceInventoryHost host) {
            super(inv, idx, x, y);
            this.host = host;
            // Use UPGRADES icon (same as SlotRestrictedInput.PlacableItemType.UPGRADES)
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
            // Always return Valid to prevent red background for custom upgrade cards
            return hasCalculatedValidness.Valid;
        }
    }
}
