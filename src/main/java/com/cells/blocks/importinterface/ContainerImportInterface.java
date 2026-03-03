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

import com.cells.util.ItemStackKey;

import javax.annotation.Nonnull;


/**
 * Container for the Import Interface GUI.
 * Layout: 4 rows of (9 filter slots on top + 9 storage slots below)
 * Plus 4 upgrade slots on the right side.
 * <p>
 * Works with both TileImportInterface (block) and PartImportInterface (part).
 */
public class ContainerImportInterface extends AEBaseContainer {

    private final IImportInterfaceInventoryHost host;

    @GuiSync(0)
    public long maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

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

        // Create slot pairs only up to the smallest inventory size to avoid creating
        // out-of-range slots (some tiles may expose fewer than 36 slots).
        final int filterSlots = host.getFilterInventory().getSlots();
        final int storageSlots = host.getStorageInventory().getSlots();
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
                this.addSlotToContainer(new SlotFilterLocked(host.getFilterInventory(), slotIndex, xPos, filterY, host, slotIndex, ip.player));

                // Storage slot (actual items, bottom part)
                this.addSlotToContainer(new SlotImportStorage(host.getStorageInventory(), slotIndex, xPos, storageY, host, slotIndex));

                slotIndex++;
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
     */
    private static class SlotFilterLocked extends SlotFake {
        private final IImportInterfaceInventoryHost host;
        private final int storageSlot;
        private final EntityPlayer player;

        public SlotFilterLocked(IItemHandler inv, int idx, int x, int y,
                                IImportInterfaceInventoryHost host, int storageSlot, EntityPlayer player) {
            super(inv, idx, x, y);
            this.host = host;
            this.storageSlot = storageSlot;
            this.player = player;
        }

        @Override
        public void putStack(ItemStack stack) {
            // Prevent filter changes if there are items in the corresponding storage slot
            ItemStack storageStack = host.getStorageInventory().getStackInSlot(this.storageSlot);
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
            // Must use ItemStackKey (item + meta + NBT) rather than ItemStack.areItemsEqual (item + meta only),
            // otherwise items with the same id/meta but different NBT are incorrectly rejected as duplicates.
            ItemStackKey newKey = ItemStackKey.of(stack);
            if (newKey == null) return;

            for (int i = 0; i < host.getFilterInventory().getSlots(); i++) {
                if (i == this.getSlotIndex()) continue; // Skip current slot

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
     */
    private static class SlotImportStorage extends SlotNormal {
        private final IImportInterfaceInventoryHost host;
        private final int filterSlot;

        public SlotImportStorage(IItemHandler inv, int idx, int x, int y,
                                  IImportInterfaceInventoryHost host, int filterSlot) {
            super(inv, idx, x, y);
            this.host = host;
            this.filterSlot = filterSlot;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return host.isItemValidForSlot(filterSlot, stack);
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
