package com.cells.blocks.importinterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.ItemStackKey;


/**
 * Tile entity for the Import Interface block.
 * Provides 36 filter slots (ghost items) and 36 storage slots.
 * Only accepts items that match the filter in the corresponding slot.
 * Automatically imports stored items into the ME network.
 */
public class TileImportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory {

    public static final int FILTER_SLOTS = 36; // 36 filter slots (ghost items)
    public static final int STORAGE_SLOTS = 36; // 36 storage slots (actual items)
    public static final int UPGRADE_SLOTS = 4;  // 4 upgrade slots
    public static final int DEFAULT_MAX_SLOT_SIZE = 64;
    public static final int MIN_MAX_SLOT_SIZE = 1;
    public static final int MAX_MAX_SLOT_SIZE = Integer.MAX_VALUE;

    // Filter inventory - ghost items only (1 stack size each)
    private final AppEngInternalInventory filterInventory = new AppEngInternalInventory(this, FILTER_SLOTS, 1);

    // Storage inventory - actual items
    private final AppEngInternalInventory storageInventory;

    // Upgrade inventory - accepts only specific upgrade cards
    private final AppEngInternalInventory upgradeInventory;

    // Wrapper that exposes storage slots with filter checking
    private final FilteredStorageHandler filteredHandler;

    // Max slot size for all storage slots
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;

    // Has Void Overflow Upgrade installed
    private boolean installedOverflowUpgrade = false;

     // Has Trash Unselected Upgrade installed
    private boolean installedTrashUnselectedUpgrade = false;

    // Mapping of filter items to their corresponding storage slot index for quick lookup
    private Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();

    // List of filter items for quick access (ith index corresponds to ith valid storage slot)
    // Initialized to empty list to avoid NPE before refreshFilterMap() is called
    private List<ItemStackKey> filterItemList = new ArrayList<>();

    // Action source for network operations
    private final IActionSource actionSource;

    public TileImportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with filter
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return TileImportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileImportInterface.this.isItemValidForSlot(slot, stack);
            }
        };

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileImportInterface.this.isValidUpgrade(stack);
            }
        };

        this.filteredHandler = new FilteredStorageHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    /**
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
        this.installedOverflowUpgrade = hasOverflowUpgrade();
        this.installedTrashUnselectedUpgrade = hasTrashUnselectedUpgrade();
    }

    /**
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();

        final int filterSlots = this.filterInventory.getSlots();
        final int storageSlots = this.storageInventory.getSlots();
        final int maxSlots = Math.min(filterSlots, storageSlots);

        for (int i = 0; i < maxSlots; i++) {
            ItemStack filterStack = this.filterInventory.getStackInSlot(i);
            if (!filterStack.isEmpty()) this.filterToSlotMap.put(ItemStackKey.of(filterStack), i);
        }

        // Also build the filter item list for quick access
        this.filterItemList = new ArrayList<>(filterToSlotMap.keySet());
    }

    /**
     * Check if an item is a valid upgrade for this interface.
     * Only accepts Overflow Card and Trash Unselected Card, max 1 of each.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (stack.getItem() instanceof ItemOverflowCard) {
            return countUpgrade(ItemOverflowCard.class) < 1;
        }

        if (stack.getItem() instanceof ItemTrashUnselectedCard) {
            return countUpgrade(ItemTrashUnselectedCard.class) < 1;
        }

        return false;
    }

    /**
     * Count how many upgrades of a specific type are installed.
     */
    private int countUpgrade(Class<?> itemClass) {
        int count = 0;
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack existing = this.upgradeInventory.getStackInSlot(i);
            if (!existing.isEmpty() && itemClass.isInstance(existing.getItem())) count++;
        }

        return count;
    }

    /**
     * Check if the overflow upgrade is installed.
     */
    public boolean hasOverflowUpgrade() {
        return countUpgrade(ItemOverflowCard.class) > 0;
    }

    /**
     * Check if the trash unselected upgrade is installed.
     */
    public boolean hasTrashUnselectedUpgrade() {
        return countUpgrade(ItemTrashUnselectedCard.class) > 0;
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return false;

        int filterSlot = this.filterToSlotMap.getOrDefault(key, -1);
        return filterSlot == slot;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.filterInventory.readFromNBT(data, "filter");
        this.storageInventory.readFromNBT(data, "storage");
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");

        if (this.maxSlotSize < MIN_MAX_SLOT_SIZE) this.maxSlotSize = MIN_MAX_SLOT_SIZE;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "filter");
        this.storageInventory.writeToNBT(data, "storage");
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);

        return data;
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        return super.readFromStream(data);
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return this.storageInventory;
    }

    /**
     * Get the filter inventory (ghost items).
     */
    public AppEngInternalInventory getFilterInventory() {
        return this.filterInventory;
    }

    /**
     * Get the storage inventory.
     */
    public AppEngInternalInventory getStorageInventory() {
        return this.storageInventory;
    }

    /**
     * Get the upgrade inventory.
     */
    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    public void setMaxSlotSize(int size) {
        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, size);
        this.markDirty();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.storageInventory && !added.isEmpty()) {
            // Wake up the tile to import items
            try {
                this.getProxy().getTick().alertDevice(this.getProxy().getNode());
            } catch (GridAccessException e) {
                // Not connected to grid
            }
        }

        this.markDirty();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // Drop all stored items
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }

        // Drop all upgrades
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // IGridTickable implementation

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
            TickRates.Interface.getMin(),
            TickRates.Interface.getMax(),
            !hasWorkToDo(),
            true
        );
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = importItems();
        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        for (int i : this.filterToSlotMap.values()) {
            if (!this.storageInventory.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    /**
     * Import items from storage slots into the ME network.
     * @return true if any items were imported
     */
    private boolean importItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            for (int i : this.filterToSlotMap.values()) {
                ItemStack stack = this.storageInventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                if (aeStack == null) continue;

                // Try to insert into network
                IAEItemStack remaining = itemStorage.injectItems(aeStack, Actionable.MODULATE, this.actionSource);

                if (remaining == null) {
                    // All items inserted
                    this.storageInventory.setStackInSlot(i, ItemStack.EMPTY);
                    didWork = true;
                } else if (remaining.getStackSize() < stack.getCount()) {
                    // Some items inserted
                    ItemStack newStack = stack.copy();
                    newStack.setCount((int) remaining.getStackSize());
                    this.storageInventory.setStackInSlot(i, newStack);
                    didWork = true;
                }
                // else: nothing inserted, network full
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        return didWork;
    }

    // Capability handling

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;

        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.filteredHandler);
        }

        return super.getCapability(capability, facing);
    }

    /**
     * Wrapper handler that provides slotless insertion of filtered items.
     * Items are automatically routed to the appropriate slot based on filters.
     * Does not allow extraction (import-only interface).
     *
     * Note: Only the filtered slots are exposed through this handler.
     *       This allows external systems to exit early when we have few filters set, without needing to try every slot.
     */
    private static class FilteredStorageHandler implements IItemHandler {
        private final TileImportInterface tile;

        public FilteredStorageHandler(TileImportInterface tile) {
            this.tile = tile;
        }

        @Override
        public int getSlots() {
            // Since we are doing slotless insertion, we can report just as many slots as there are filters.
            return tile.filterItemList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= tile.filterItemList.size()) return ItemStack.EMPTY;

            ItemStackKey key = tile.filterItemList.get(slot);
            Integer storageSlot = tile.filterToSlotMap.get(key);

            // Safety check: key should always be in map, but handle edge case
            if (storageSlot == null) return ItemStack.EMPTY;

            return tile.storageInventory.getStackInSlot(storageSlot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Slotless insertion: find any slot that can accept this item based on filters
            // This is more flexible than slot-specific insertion and works better with pipes
            return insertItemSlotless(stack, simulate);
        }

        /**
         * Insert an item into the slot that matches its filter.
         * If no filter matches, either void the item (if trash unselected upgrade is installed) or reject it.
         * If the item exceeds the max slot size, either void the excess (if overflow upgrade is installed) or return the remainder.
         *
         * NOTE: We have anti-duplication and anti-orphaning logic in the filter slot handler,
         *       so the mapping from item to slot should always be consistent and valid.
         */
        private ItemStack insertItemSlotless(@Nonnull ItemStack stack, boolean simulate) {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return stack; // Invalid item

            // No match, delete if trash unselected upgrade is installed, otherwise reject
            int slot = tile.filterToSlotMap.getOrDefault(key, -1);
            if (slot == -1) return tile.installedTrashUnselectedUpgrade ? ItemStack.EMPTY : stack;

            // Try to insert into the matched slot
            ItemStack remaining = tile.storageInventory.insertItem(slot, stack, simulate);

            // Void any excess items if overflow upgrade is installed
            if (tile.installedOverflowUpgrade) return ItemStack.EMPTY;

            return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Import interface does not allow external extraction
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return tile.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // For slotless operation, we check if ANY filter accepts this item
            // To an external system, all items are valid for slot 0.
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            return tile.filterToSlotMap.containsKey(key);
        }
    }
}
