package com.cells.blocks.importinterface;

import java.io.IOException;
import java.util.ArrayList;
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
import appeng.api.networking.ticking.ITickManager;
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
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.ItemStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Tile entity for the Import Interface block.
 * Provides 36 filter slots (ghost items) and 36 storage slots.
 * Only accepts items that match the filter in the corresponding slot.
 * Automatically imports stored items into the ME network.
 */
public class TileImportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory, IImportInterfaceHost {

    public static final int FILTER_SLOTS = 36; // 36 filter slots (ghost items)
    public static final int STORAGE_SLOTS = 36; // 36 storage slots (actual items)
    public static final int UPGRADE_SLOTS = 4;  // 4 upgrade slots
    public static final int DEFAULT_MAX_SLOT_SIZE = 64;
    public static final int MIN_MAX_SLOT_SIZE = 1;
    public static final int MAX_MAX_SLOT_SIZE = Integer.MAX_VALUE;

    // Polling rate constants (in ticks, 20 ticks = 1 second)
    public static final int DEFAULT_POLLING_RATE = 0; // 0 means adaptive (AE2 default)
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    public static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    public static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

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

    // Polling rate in ticks (0 = adaptive AE2 default, nonzero = fixed interval)
    private int pollingRate = DEFAULT_POLLING_RATE;

    // Has Void Overflow Upgrade installed
    private boolean installedOverflowUpgrade = false;

     // Has Trash Unselected Upgrade installed
    private boolean installedTrashUnselectedUpgrade = false;

    // Mapping of filter items to their corresponding storage slot index for quick lookup
    final private Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();

    // List of filter items for quick access (ith index corresponds to ith valid storage slot)
    // Initialized to empty list to avoid NPE before refreshFilterMap() is called
    private List<ItemStackKey> filterItemList = new ArrayList<>();

    // Action source for network operations
    private final IActionSource actionSource;

    public TileImportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with filter and unlimited stack size support
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return TileImportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileImportInterface.this.isItemValidForSlot(slot, stack);
            }

            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) return ItemStack.EMPTY;

                // Custom insertion logic that ignores item's maxStackSize
                // This allows slots to hold more than 64 items of any type
                // Note: Item validity is checked by FilteredStorageHandler.insertItemSlotless()
                // before calling this method, so we skip redundant validation here.
                ItemStack existing = this.getStackInSlot(slot);
                int limit = TileImportInterface.this.maxSlotSize;

                if (!existing.isEmpty()) {
                    if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                        return stack;
                    }

                    int space = limit - existing.getCount();
                    if (space <= 0) return stack;

                    int toInsert = Math.min(stack.getCount(), space);
                    if (!simulate) {
                        ItemStack newStack = existing.copy();
                        newStack.grow(toInsert);
                        this.setStackInSlot(slot, newStack);
                    }

                    if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

                    ItemStack remainder = stack.copy();
                    remainder.shrink(toInsert);
                    return remainder;
                } else {
                    int toInsert = Math.min(stack.getCount(), limit);
                    if (!simulate) {
                        ItemStack newStack = stack.copy();
                        newStack.setCount(toInsert);
                        this.setStackInSlot(slot, newStack);
                    }

                    if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

                    ItemStack remainder = stack.copy();
                    remainder.shrink(toInsert);
                    return remainder;
                }
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
        // Note: storageInventory is saved by AEBaseInvTile via getInternalInventory() as "inv"
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < MIN_MAX_SLOT_SIZE) this.maxSlotSize = MIN_MAX_SLOT_SIZE;
        if (this.pollingRate < 0) this.pollingRate = 0;

        // Update slot limits in the underlying inventory to match maxSlotSize
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            this.storageInventory.setMaxStackSize(i, this.maxSlotSize);
        }

        // Rebuild caches after loading inventories
        this.refreshFilterMap();
        this.refreshUpgrades();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "filter");
        // Note: storageInventory is saved by AEBaseInvTile via getInternalInventory() as "inv"
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);

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

        // Update slot limits in the underlying inventory
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            this.storageInventory.setMaxStackSize(i, this.maxSlotSize);
        }

        this.markDirty();
    }

    public int getPollingRate() {
        return this.pollingRate;
    }

    public void setPollingRate(int ticks) {
        this.pollingRate = Math.max(0, ticks);
        this.markDirty();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        // Uses TickManagerHelper to purge stale TickTrackers from AE2's internal
        // PriorityQueue before re-registering (see TickManagerHelper for details).
        TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this);
    }

    /**
     * Format a tick count as a human-readable time string.
     * Format: "1d 2h 3m 4s" (skipping zero parts) or "0" if zero.
     */
    public static String formatPollingRate(long ticks) {
        if (ticks <= 0) return "0";

        long days = ticks / TICKS_PER_DAY;
        ticks %= TICKS_PER_DAY;

        long hours = ticks / TICKS_PER_HOUR;
        ticks %= TICKS_PER_HOUR;

        long minutes = ticks / TICKS_PER_MINUTE;
        ticks %= TICKS_PER_MINUTE;

        long seconds = ticks / TICKS_PER_SECOND;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.filterInventory) {
            // Filter changed - rebuild the filter-to-slot mapping so external systems
            // (like hoppers) can see the correct slots and item validity
            this.refreshFilterMap();
        } else if (inv == this.upgradeInventory) {
            // Upgrade changed - refresh cached upgrade flags
            this.refreshUpgrades();
        } else if (inv == this.storageInventory && !added.isEmpty()) {
            // Wake up the tile to import items, but only if using adaptive polling
            // Fixed polling rate should not be interrupted by inventory changes
            if (this.pollingRate <= 0) {
                // FIXME: may be pretty bad for performance if we have a lot of item changes
                try {
                    this.getProxy().getTick().alertDevice(this.getProxy().getNode());
                } catch (GridAccessException e) {
                    // Not connected to grid
                }
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

    // IImportInterfaceHost implementation

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_IMPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.import_interface.title";
    }

    // IGridTickable implementation

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        // If polling rate is set, use fixed interval; otherwise use adaptive AE2 rates
        if (this.pollingRate > 0) {
            // For fixed polling, never start sleeping - always tick at the fixed rate
            // This ensures consistent polling regardless of inventory state
            return new TickingRequest(
                this.pollingRate,
                this.pollingRate,
                false, // Never start sleeping with fixed polling
                true
            );
        }

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

        // If using fixed polling rate, always use SAME to maintain interval
        if (this.pollingRate > 0) return TickRateModulation.SAME;

        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        // TODO: could probably optimize that by adding a dirty flag that's set when storage changes from empty to non-empty and vice versa.
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
     * <p>
     * Note: Exposes 1 dummy slot that's always empty because Forge's hopper code
     * uses a broken "isFull" check that compares stack count to ItemStack.getMaxStackSize()
     * instead of IItemHandler.getSlotLimit(). The dummy slot ensures hoppers see the
     * inventory as "not full" and attempt insertion, which our slotless logic handles.
     */
    private static class FilteredStorageHandler implements IItemHandler {
        private final TileImportInterface tile;

        public FilteredStorageHandler(TileImportInterface tile) {
            this.tile = tile;
        }

        @Override
        public int getSlots() {
            // Expose 1 dummy slot so hoppers see an empty slot and don't think we're full.
            // Forge 1.12.x hopper code uses stackInSlot.getCount() != stackInSlot.getMaxStackSize()
            // which incorrectly caps at 64 instead of using our getSlotLimit().
            return 1 + tile.filterToSlotMap.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            // Slot 0 is the dummy slot - always empty
            if (slot <= 0) return ItemStack.EMPTY;

            // Slots 1 through filterItemList.size() are actual filter slots
            int filterIndex = slot - 1;
            if (filterIndex >= tile.filterItemList.size()) return ItemStack.EMPTY;

            ItemStackKey key = tile.filterItemList.get(filterIndex);
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
         * <p>
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
