package com.cells.blocks.importinterface;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotNormal;

import com.cells.util.ItemStackKey;


/**
 * Container for the Import Interface GUI.
 * Layout: 4 rows of (9 filter slots on top + 9 storage slots below)
 * Plus 4 upgrade slots on the right side.
 */
public class ContainerImportInterface extends AEBaseContainer {

    private final TileImportInterface tile;

    @GuiSync(0)
    public long maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    public ContainerImportInterface(final InventoryPlayer ip, final TileImportInterface tile) {
        super(ip, tile, null);
        this.tile = tile;

        // Create slot pairs only up to the smallest inventory size to avoid creating
        // out-of-range slots (some tiles may expose fewer than 36 slots).
        final int filterSlots = tile.getFilterInventory().getSlots();
        final int storageSlots = tile.getStorageInventory().getSlots();
        final int maxSlots = Math.min(filterSlots, storageSlots);

        // Add filter slots (ghost/fake slots) and storage slots
        // 4 rows of 9 pairs (filter on top, storage below)
        int slotIndex = 0;
        outer: for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                if (slotIndex >= maxSlots) break outer;

                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;
                int storageY = filterY + 18;

                // Filter slot (ghost item) - locked when storage slot has items
                this.addSlotToContainer(new SlotFilterLocked(tile.getFilterInventory(), slotIndex, xPos, filterY, tile, slotIndex));

                // Storage slot (actual items, bottom part)
                this.addSlotToContainer(new SlotImportStorage(tile.getStorageInventory(), slotIndex, xPos, storageY, tile, slotIndex));

                slotIndex++;
            }
        }

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < TileImportInterface.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                tile.getUpgradeInventory(),
                i,
                186,
                25 + i * 18,
                tile
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (this.maxSlotSize != this.tile.getMaxSlotSize()) this.maxSlotSize = this.tile.getMaxSlotSize();
        if (this.pollingRate != this.tile.getPollingRate()) this.pollingRate = this.tile.getPollingRate();
    }

    public TileImportInterface getTile() {
        return this.tile;
    }

    public void setMaxSlotSize(int size) {
        this.tile.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.tile.setPollingRate(ticks);
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        // Refresh upgrade status when an upgrade slot changes
        if (s instanceof SlotUpgrade) tile.refreshUpgrades();

        // Refresh filter map when a filter slot changes (for slotless storage lookups)
        if (s instanceof SlotFilterLocked) tile.refreshFilterMap();
    }

    /**
     * Filter slot that prevents changes when the corresponding storage slot has items.
     * This prevents orphaning items that no longer match any filter.
     */
    private static class SlotFilterLocked extends SlotFake {
        private final TileImportInterface tile;
        private final int storageSlot;

        public SlotFilterLocked(IItemHandler inv, int idx, int x, int y,
                                TileImportInterface tile, int storageSlot) {
            super(inv, idx, x, y);
            this.tile = tile;
            this.storageSlot = storageSlot;
        }

        @Override
        public void putStack(ItemStack stack) {
            // Prevent filter changes if there are items in the corresponding storage slot
            ItemStack storageStack = tile.getStorageInventory().getStackInSlot(this.storageSlot);
            if (!storageStack.isEmpty()) return;

            // Allow clearing the filter slot by clicking with an empty hand
            if (stack.isEmpty()) {
                super.putStack(stack);
                return;
            }

            // Prevent duplicate filters by checking if the new filter item already exists in another slot
            // Must use ItemStackKey (item + meta + NBT) rather than ItemStack.areItemsEqual (item + meta only),
            // otherwise items with the same id/meta but different NBT are incorrectly rejected as duplicates.
            ItemStackKey newKey = ItemStackKey.of(stack);
            if (newKey == null) return;

            for (int i = 0; i < tile.getFilterInventory().getSlots(); i++) {
                if (i == this.getSlotIndex()) continue; // Skip current slot

                ItemStackKey otherKey = ItemStackKey.of(tile.getFilterInventory().getStackInSlot(i));
                if (otherKey != null && otherKey.equals(newKey)) return; // Duplicate found, do not allow
            }

            super.putStack(stack);
        }
    }

    /**
     * Custom slot for storage that respects the filter.
     */
    private static class SlotImportStorage extends SlotNormal {
        private final TileImportInterface tile;
        private final int filterSlot;

        public SlotImportStorage(IItemHandler inv, int idx, int x, int y,
                                  TileImportInterface tile, int filterSlot) {
            super(inv, idx, x, y);
            this.tile = tile;
            this.filterSlot = filterSlot;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return tile.isItemValidForSlot(filterSlot, stack);
        }

        @Override
        public int getSlotStackLimit() {
            return tile.getMaxSlotSize();
        }
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final TileImportInterface tile;

        public SlotUpgrade(IItemHandler inv, int idx, int x, int y, TileImportInterface tile) {
            super(inv, idx, x, y);
            this.tile = tile;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return tile.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
