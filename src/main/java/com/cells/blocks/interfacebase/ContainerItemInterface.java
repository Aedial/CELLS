package com.cells.blocks.interfacebase;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotNormal;

import com.cells.gui.slots.PagedItemHandler;
import com.cells.util.ItemStackKey;


/**
 * Unified container for both Item Import Interface and Item Export Interface GUIs.
 * Layout: 4 rows of (9 filter slots on top + 9 storage slots below),
 * plus 4 upgrade slots on the right side.
 * Supports pagination via Capacity Cards - each card adds a page of 36 slots.
 * <p>
 * Uses {@link IItemInterfaceHost#isExport()} to determine behavioral differences:
 * <ul>
 *   <li>Import: filter slots are locked when storage has items, storage validates against filters</li>
 *   <li>Export: filter slots are freely settable, storage is read-only (filled from network)</li>
 * </ul>
 * <p>
 * Works with both tile entities and parts via {@link IItemInterfaceHost}.
 */
public class ContainerItemInterface extends AEBaseContainer {

    private final IItemInterfaceHost host;

    @GuiSync(0)
    public long maxSlotSize = ItemInterfaceLogic.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = 0;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    // Paged handlers for filter and storage inventories
    private final PagedItemHandler pagedFilterHandler;
    private final PagedItemHandler pagedStorageHandler;

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerItemInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IItemInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerItemInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IItemInterfaceHost) part, part);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerItemInterface(final InventoryPlayer ip, final IItemInterfaceHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Create paged handlers that offset based on current page
        this.pagedFilterHandler = new PagedItemHandler(
            host.getFilterInventory(),
            ItemInterfaceLogic.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );
        this.pagedStorageHandler = new PagedItemHandler(
            host.getStorageInventory(),
            ItemInterfaceLogic.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );

        // Add filter slots (ghost/fake slots) and storage slots
        // 4 rows of 9 pairs (filter on top, storage below)
        final boolean isExport = host.isExport();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;
                int storageY = filterY + 18;

                // Filter slot (ghost item) - uses paged handler
                this.addSlotToContainer(new SlotFilter(
                    this.pagedFilterHandler, slotIndex, xPos, filterY,
                    host, this.pagedStorageHandler, slotIndex, ip.player, isExport
                ));

                // Storage slot (actual items, bottom part)
                this.addSlotToContainer(new SlotStorage(
                    this.pagedStorageHandler, slotIndex, xPos, storageY,
                    host, slotIndex, isExport
                ));
            }
        }

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < ItemInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    /**
     * Cap the return value of slotClick to prevent oversized stacks (count > 64) from being
     * serialized in vanilla CPacketClickWindow. AE2-UEL's PacketBufferPatch uses a magic byte
     * (-42) + int encoding for oversized counts, but not all server implementations support this
     * (e.g., hybrid Forge+Bukkit servers, or when the 'stackup' mod causes the patch to be skipped
     * on one side). The return value is ONLY used for transaction verification and does NOT affect
     * actual slot interaction logic (items taken/placed are handled before the return).
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);

        if (!result.isEmpty() && result.getCount() > result.getMaxStackSize()) {
            result = result.copy();
            result.setCount(result.getMaxStackSize());
        }

        return result;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (this.maxSlotSize != this.host.getMaxSlotSize()) this.maxSlotSize = this.host.getMaxSlotSize();
        if (this.pollingRate != this.host.getPollingRate()) this.pollingRate = this.host.getPollingRate();
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();
    }

    public IItemInterfaceHost getHost() {
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

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    /**
     * Clear all filters. Import only clears where storage is empty (prevents orphans).
     * Export clears all and returns items to network. Delegates to host.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) host.refreshUpgrades();
        if (s instanceof SlotFilter) host.refreshFilterMap();
    }

    // ============================== Inner slot classes ==============================

    /**
     * Filter slot for both import and export interfaces.
     * <p>
     * Import mode: prevents changes when the corresponding storage slot has items
     *              (to prevent orphaning items that no longer match any filter).
     * Export mode: freely allows setting any filter, prevents duplicates.
     */
    static class SlotFilter extends SlotFake {
        private final IItemInterfaceHost host;
        private final PagedItemHandler storageHandler;
        private final int localSlot;
        private final EntityPlayer player;
        private final boolean isExport;

        public SlotFilter(PagedItemHandler filterHandler, int idx, int x, int y,
                          IItemInterfaceHost host, PagedItemHandler storageHandler,
                          int localSlot, EntityPlayer player, boolean isExport) {
            super(filterHandler, idx, x, y);
            this.host = host;
            this.storageHandler = storageHandler;
            this.localSlot = localSlot;
            this.player = player;
            this.isExport = isExport;
        }

        @Override
        public void putStack(ItemStack stack) {
            // Import mode: prevent filter changes if there are items in the corresponding storage slot
            if (!this.isExport) {
                ItemStack storageStack = storageHandler.getStackInSlot(this.localSlot);
                if (!storageStack.isEmpty()) {
                    if (!player.world.isRemote) {
                        player.sendMessage(new TextComponentTranslation("message.cells.import_interface.storage_not_empty"));
                    }
                    return;
                }
            }

            // Allow clearing the filter slot by clicking with an empty hand
            if (stack.isEmpty()) {
                super.putStack(stack);
                return;
            }

            // Prevent duplicate filters across ALL pages
            ItemStackKey newKey = ItemStackKey.of(stack);
            if (newKey == null) return;

            int actualSlot = ((PagedItemHandler) this.getItemHandler()).getActualSlotIndex(this.localSlot);
            for (int i = 0; i < host.getFilterInventory().getSlots(); i++) {
                if (i == actualSlot) continue;

                ItemStackKey otherKey = ItemStackKey.of(host.getFilterInventory().getStackInSlot(i));
                if (otherKey != null && otherKey.equals(newKey)) {
                    if (!player.world.isRemote) {
                        String msgKey = this.isExport
                            ? "message.cells.export_interface.filter_duplicate"
                            : "message.cells.import_interface.filter_duplicate";
                        player.sendMessage(new TextComponentTranslation(msgKey));
                    }
                    return;
                }
            }

            super.putStack(stack);
        }
    }

    /**
     * Storage slot for both import and export interfaces.
     * <p>
     * Import mode: validates items against the filter, uses host's max slot size.
     * Export mode: read-only display of items from network, allows extraction but not insertion.
     */
    private static class SlotStorage extends SlotNormal {
        private final IItemInterfaceHost host;
        private final int localSlot;
        private final boolean isExport;

        public SlotStorage(PagedItemHandler storageHandler, int idx, int x, int y,
                           IItemInterfaceHost host, int localSlot, boolean isExport) {
            super(storageHandler, idx, x, y);
            this.host = host;
            this.localSlot = localSlot;
            this.isExport = isExport;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            if (this.isExport) return false; // Export: insertion blocked, items come from network

            // Import: validate against filter
            int actualSlot = ((PagedItemHandler) this.getItemHandler()).getActualSlotIndex(this.localSlot);
            return host.isItemValidForSlot(actualSlot, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return host.getMaxSlotSize();
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            // Always return Valid to prevent red background rendering
            // for custom upgrade cards (export) or filter-validated slots (import).
            // Insertion is already blocked at the IItemHandler level.
            return hasCalculatedValidness.Valid;
        }
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     * Uses the same icon as AE2 UPGRADES (13 * 16 + 15 = 223) for empty slot background.
     * Always reports as Valid to prevent red background rendering for custom upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IItemInterfaceHost host;

        public SlotUpgrade(IItemHandler inv, int idx, int x, int y, IItemInterfaceHost host) {
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
            return hasCalculatedValidness.Valid;
        }
    }
}
