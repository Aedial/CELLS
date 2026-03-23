package com.cells.blocks.interfacebase.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.item.AEItemStack;

import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.util.InventoryMigrationHelper;
import com.cells.util.ItemStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Shared logic delegate for Item Interface implementations (both import and export,
 * both tile and part). Contains all business logic that is identical across all
 * four item interface variants.
 * <p>
 * The host provides platform-specific operations (grid proxy, marking dirty, etc.)
 * via the {@link Host} callback interface. The {@link Host#isExport()} flag
 * parameterizes import vs export behavioral differences.
 * <p>
 * Both TileImportInterface/TileExportInterface and PartImportInterface/PartExportInterface
 * instantiate an ItemInterfaceLogic in their constructor and delegate business logic to it.
 */
public class ItemInterfaceLogic {

    // ============================== Host callback interface ==============================

    /**
     * Callback interface that the host (tile or part) implements to provide
     * platform-specific operations to the logic delegate.
     */
    public interface Host extends IAEAppEngInventory {

        /** Get the AE2 grid proxy for network access. */
        AENetworkProxy getGridProxy();

        /** Get the action source for ME network operations. */
        IActionSource getActionSource();

        /** Whether this is an export interface (true) or import interface (false). */
        boolean isExport();

        /**
         * Mark this host as dirty and save its state.
         * Tile: markDirty(). Part: getHost().markForSave().
         */
        void markDirtyAndSave();

        /**
         * Mark this host for client update.
         * Tile: markForUpdate(). Part: getHost().markForUpdate().
         */
        void markForNetworkUpdate();

        /** Get the world this host is in (may be null during loading). */
        @Nullable
        World getHostWorld();

        /** Get the position of this host in the world. */
        BlockPos getHostPos();

        /** Get the IGridTickable to re-register with tick manager. */
        IGridTickable getTickable();
    }

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int STORAGE_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int UPGRADE_SLOTS = 4;
    public static final int DEFAULT_MAX_SLOT_SIZE = 64;
    public static final int MIN_MAX_SLOT_SIZE = 1;

    // Polling rate constants (in ticks, 20 ticks = 1 second)
    public static final int DEFAULT_POLLING_RATE = 0; // 0 means adaptive (AE2 default)
    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    public static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;
    public static final int TICKS_PER_DAY = TICKS_PER_HOUR * 24;

    private final Host host;

    // Inventories
    private final AppEngInternalInventory filterInventory;
    private final AppEngInternalInventory storageInventory;
    private final AppEngInternalInventory upgradeInventory;

    // External handler exposed via capabilities
    private final IItemHandler externalHandler;

    // Config
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
    private int pollingRate = DEFAULT_POLLING_RATE;

    // Upgrade cache (import-only upgrades are always false for export)
    private boolean installedOverflowUpgrade = false;
    private boolean installedTrashUnselectedUpgrade = false;
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Mapping of filter items to their corresponding storage slot index for quick lookup
    final Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();

    // Reverse mapping: slot index -> cached ItemStackKey for the filter in that slot
    final Map<Integer, ItemStackKey> slotToFilterMap = new HashMap<>();

    // List of slot indices that have filters, in slot order
    // This ensures external systems see slots in the correct order regardless of filter insertion order
    private List<Integer> filterSlotList = new ArrayList<>();

    public ItemInterfaceLogic(Host host) {
        this.host = host;

        // Create filter inventory - ghost items only (1 stack size each)
        this.filterInventory = new AppEngInternalInventory(host, FILTER_SLOTS, 1);

        // Create storage inventory with filter and unlimited stack size support
        this.storageInventory = new AppEngInternalInventory(host, STORAGE_SLOTS, DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return ItemInterfaceLogic.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return ItemInterfaceLogic.this.isItemValidForSlot(slot, stack);
            }

            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                // Import: slotless insertion that ignores item's maxStackSize,
                // allowing slots to hold more than 64 items of any type.
                // The slot parameter is ignored; the correct slot is found via filterToSlotMap.
                // Export: standard insertion behavior (external insertion is already blocked
                // by ExportStorageHandler, so this path is only reached by internal code).
                if (!host.isExport()) return ItemInterfaceLogic.this.slotlessInsertItem(stack, simulate);

                return super.insertItem(slot, stack, simulate);
            }

            @Override
            @Nonnull
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                // Export: bypass the item's maxStackSize limit (typically 64) to allow
                // extracting up to maxSlotSize items at once. This is necessary because
                // Forge's ItemStackHandler.extractItem() limits extraction to maxStackSize.
                if (!host.isExport()) return super.extractItem(slot, amount, simulate);

                // Custom extraction logic that respects slot limit instead of item stack limit
                ItemStack existing = this.getStackInSlot(slot);
                if (existing.isEmpty()) return ItemStack.EMPTY;

                // Don't limit by maxStackSize - limit by slot limit and available amount
                int toExtract = Math.min(amount, existing.getCount());
                if (toExtract <= 0) return ItemStack.EMPTY;

                if (simulate) {
                    ItemStack result = existing.copy();
                    result.setCount(toExtract);
                    return result;
                }

                ItemStack result = existing.copy();
                result.setCount(toExtract);
                existing.shrink(toExtract);

                if (existing.isEmpty()) this.setStackInSlot(slot, ItemStack.EMPTY);

                this.onContentsChanged(slot);
                return result;
            }
        };

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(host, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return ItemInterfaceLogic.this.isValidUpgrade(stack);
            }
        };

        // Create appropriate external handler based on direction
        if (host.isExport()) {
            this.externalHandler = new ExportStorageHandler(this);
        } else {
            this.externalHandler = new FilteredStorageHandler(this);
        }

        refreshUpgrades();
        refreshFilterMap();
    }

    public String getTypeName() {
        return "item";
    }

    /**
     * Slotless insertion logic that ignores item's maxStackSize.
     * Finds the correct slot via {@link #filterToSlotMap} using {@link ItemStackKey},
     * ignoring any slot parameter passed by callers. This is the insertion entry
     * point for import interfaces.
     * <p>
     * Handles overflow and trash-unselected upgrade cards:
     * - If no filter matches and trash-unselected is installed, the item is voided.
     * - If the slot is full and overflow is installed, excess items are voided.
     *
     * FIXME: AppEngInternalInventory is mostly bypassed here (getSlotLimit, isItemValid,
     *        insertItem are all overridden). Consider replacing it with a plain IItemHandler
     *        or static array, similar to FluidInterfaceLogic's fluid tank array. This would
     *        also eliminate the need for InventoryMigrationHelper.readFromNBTWithoutShrinking
     *        since a static array wouldn't try to resize from NBT data. Blocked by AE2's
     *        AENetworkInvTile expecting an AppEngInternalInventory from getInternalInventory().
     */
    private ItemStack slotlessInsertItem(@Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // Find the correct slot from the filter map
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return stack;

        // No matching filter — void if trash-unselected upgrade is installed, otherwise reject
        int targetSlot = this.filterToSlotMap.getOrDefault(key, -1);
        if (targetSlot == -1) return this.hasTrashUnselectedUpgrade() ? ItemStack.EMPTY : stack;

        int limit = this.maxSlotSize;
        ItemStack existing = this.storageInventory.getStackInSlot(targetSlot);

        if (!existing.isEmpty()) {
            // Verify the existing stack matches (guards against orphaned items in the slot)
            if (!key.equals(ItemStackKey.of(existing))) return stack;

            // Slot is full — void excess if overflow upgrade is installed
            int space = limit - existing.getCount();
            if (space <= 0) return this.hasOverflowUpgrade() ? ItemStack.EMPTY : stack;

            int toInsert = Math.min(stack.getCount(), space);
            if (!simulate) {
                ItemStack newStack = existing.copy();
                newStack.grow(toInsert);
                this.storageInventory.setStackInSlot(targetSlot, newStack);
            }

            if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

            ItemStack remainder = stack.copy();
            remainder.shrink(toInsert);
            // Void any remainder if overflow upgrade is installed
            return this.hasOverflowUpgrade() ? ItemStack.EMPTY : remainder;
        } else {
            int toInsert = Math.min(stack.getCount(), limit);
            if (!simulate) {
                ItemStack newStack = stack.copy();
                newStack.setCount(toInsert);
                this.storageInventory.setStackInSlot(targetSlot, newStack);
            }

            if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

            ItemStack remainder = stack.copy();
            remainder.shrink(toInsert);
            // Void any remainder if overflow upgrade is installed
            return this.hasOverflowUpgrade() ? ItemStack.EMPTY : remainder;
        }
    }


    public AppEngInternalInventory getFilterInventory() {
        return this.filterInventory;
    }

    public AppEngInternalInventory getStorageInventory() {
        return this.storageInventory;
    }

    public AppEngInternalInventory getUpgradeInventory() {
        return this.upgradeInventory;
    }

    /**
     * @return The external handler to expose via capabilities (filtered for import, extraction-only for export).
     */
    public IItemHandler getExternalHandler() {
        return this.externalHandler;
    }

    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    /**
     * Set the maximum number of items per slot.
     * For export interfaces, returns overflow items if the slot size was reduced.
     */
    public void setMaxSlotSize(int size) {
        int oldSize = this.maxSlotSize;
        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, size);

        // Update slot limits in the underlying inventory
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            this.storageInventory.setMaxStackSize(i, this.maxSlotSize);
        }

        this.host.markDirtyAndSave();

        if (this.host.isExport()) {
            // If slot size was reduced, return overflow items to the network
            if (oldSize > this.maxSlotSize) returnOverflowToNetwork();

            // Slots may now have room for more items after increasing the limit
            if (oldSize < this.maxSlotSize) this.wakeUpIfAdaptive();
        }
    }

    public int getPollingRate() {
        return this.pollingRate;
    }

    public void setPollingRate(int ticks) {
        this.setPollingRate(ticks, null);
    }

    /**
     * Set the polling rate with optional player notification on failure.
     * @param ticks Polling rate in ticks (0 = adaptive)
     * @param player Player to notify if re-registration fails, or null to skip notification
     */
    public void setPollingRate(int ticks, EntityPlayer player) {
        this.pollingRate = Math.max(0, ticks);
        this.host.markDirtyAndSave();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        // Only attempt on server side when the proxy is ready - if not ready yet
        // (e.g., during onPlacement/uploadSettings), the tick manager will pick up
        // the correct rate when the node is first added to the grid.
        AENetworkProxy proxy = this.host.getGridProxy();
        if (proxy.isReady()) {
            // Uses TickManagerHelper to purge stale TickTrackers from AE2's internal
            // PriorityQueue before re-registering (see TickManagerHelper for details).
            if (!TickManagerHelper.reRegisterTickable(proxy.getNode(), this.host.getTickable())) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.polling_rate_delayed"));
                }
            }
        }
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

    /**
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
        if (!this.host.isExport()) {
            this.installedOverflowUpgrade = countUpgrade(ItemOverflowCard.class) > 0;
            this.installedTrashUnselectedUpgrade = countUpgrade(ItemTrashUnselectedCard.class) > 0;
        }

        int oldCapacityCount = this.installedCapacityUpgrades;
        this.installedCapacityUpgrades = countCapacityUpgrades();

        // Handle capacity card removal - shrink pages
        if (this.installedCapacityUpgrades < oldCapacityCount) {
            handleCapacityReduction(oldCapacityCount, this.installedCapacityUpgrades);
        }

        // Clamp current page to valid range
        int maxPage = this.installedCapacityUpgrades;
        if (this.currentPage > maxPage) this.currentPage = maxPage;
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

    public boolean hasOverflowUpgrade() {
        return this.installedOverflowUpgrade;
    }

    public boolean hasTrashUnselectedUpgrade() {
        return this.installedTrashUnselectedUpgrade;
    }

    /**
     * Count the number of installed capacity upgrades.
     */
    public int countCapacityUpgrades() {
        int count = 0;

        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof IUpgradeModule)) continue;

            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) count++;
        }

        return count;
    }

    /**
     * Check if an item is a valid upgrade for this interface.
     * Import: Accepts Overflow Card, Trash Unselected Card (max 1 each), and Capacity Card (max 4).
     * Export: Accepts Capacity Card (max 4) only.
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Import-only upgrades
        if (!this.host.isExport()) {
            if (stack.getItem() instanceof ItemOverflowCard) {
                return countUpgrade(ItemOverflowCard.class) < 1;
            }
            if (stack.getItem() instanceof ItemTrashUnselectedCard) {
                return countUpgrade(ItemTrashUnselectedCard.class) < 1;
            }
        }

        // Capacity card (both import and export) - limited by the number of upgrade slots
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) return true;
        }

        return false;
    }

    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    public int getCurrentPage() {
        return this.currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.installedCapacityUpgrades));
    }

    public int getCurrentPageStartSlot() {
        return this.currentPage * SLOTS_PER_PAGE;
    }

    /**
     * Handle capacity reduction by clearing filters and returning/dropping items from removed pages.
     */
    private void handleCapacityReduction(int oldCount, int newCount) {
        int newTotalSlots = (1 + newCount) * SLOTS_PER_PAGE;
        int oldTotalSlots = (1 + oldCount) * SLOTS_PER_PAGE;

        // Process slots that are being removed (from newTotalSlots to oldTotalSlots-1)
        for (int slot = newTotalSlots; slot < oldTotalSlots && slot < this.storageInventory.getSlots(); slot++) {
            // Clear the filter
            if (slot < this.filterInventory.getSlots()) {
                this.filterInventory.setStackInSlot(slot, ItemStack.EMPTY);
            }

            // Return items to network or drop on floor
            returnSlotToNetwork(slot, true);
        }

        this.refreshFilterMap();
    }

    /**
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();
        this.slotToFilterMap.clear();

        final int filterSlots = this.filterInventory.getSlots();
        final int storageSlots = this.storageInventory.getSlots();
        final int maxSlots = Math.min(filterSlots, storageSlots);

        // Build list of valid (internal) slot indices for quick access
        // (because AE2 expects slots matching)
        List<Integer> validSlots = new ArrayList<>();

        for (int i = 0; i < maxSlots; i++) {
            ItemStack filterStack = this.filterInventory.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                ItemStackKey key = ItemStackKey.of(filterStack);
                this.filterToSlotMap.put(key, i);
                this.slotToFilterMap.put(i, key);
                validSlots.add(i);
            }
        }

        this.filterSlotList = validSlots;
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     * Import: uses filter-to-slot map (an item maps to exactly one slot).
     * Export: checks if the item directly matches the filter in that slot.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        if (this.host.isExport()) {
            ItemStackKey filterKey = this.slotToFilterMap.get(slot);
            return filterKey != null && filterKey.matches(stack);
        } else {
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            int filterSlot = this.filterToSlotMap.getOrDefault(key, -1);
            return filterSlot == slot;
        }
    }

    /**
     * Clear filter slots. Import only clears filters where the corresponding
     * storage slot is empty (to prevent orphaning items). Export clears all filters.
     */
    public void clearFilters() {
        if (this.host.isExport()) {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        } else {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                // Only clear filter if the corresponding storage slot is empty
                if (i >= STORAGE_SLOTS || this.storageInventory.getStackInSlot(i).isEmpty()) {
                    this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }

        this.refreshFilterMap();
        this.host.markDirtyAndSave();
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots are available
     */
    public int findEmptyFilterSlot() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            if (this.filterInventory.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
    }

    /**
     * Read logic state from NBT. Call from host's readFromNBT.
     * @param isTile true if called from a tile entity (uses "inv" tag for storage via parent), false for parts
     */
    public void readFromNBT(NBTTagCompound data, boolean isTile) {
        // FIXME: Should disappear with the inventory refactor
        InventoryMigrationHelper.readFromNBTWithoutShrinking(this.filterInventory, data, "filter");

        // Parts store storage explicitly; tiles use getInternalInventory() via parent
        if (!isTile) {
            InventoryMigrationHelper.readFromNBTWithoutShrinking(this.storageInventory, data, "storage");
        }

        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < MIN_MAX_SLOT_SIZE) this.maxSlotSize = MIN_MAX_SLOT_SIZE;
        if (this.pollingRate < 0) this.pollingRate = DEFAULT_POLLING_RATE;

        // Update slot limits in the underlying inventory to match maxSlotSize
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            this.storageInventory.setMaxStackSize(i, this.maxSlotSize);
        }

        // Rebuild caches after loading inventories
        this.refreshFilterMap();
        this.refreshUpgrades();
    }

    /**
     * Write logic state to NBT. Call from host's writeToNBT.
     * @param isTile true if called from a tile entity (storage saved via parent), false for parts
     */
    public void writeToNBT(NBTTagCompound data, boolean isTile) {
        this.filterInventory.writeToNBT(data, "filter");

        // Parts store storage explicitly; tiles use getInternalInventory() via parent
        if (!isTile) this.storageInventory.writeToNBT(data, "storage");

        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);
    }

    /**
     * Download settings to NBT for memory cards and dismantling.
     */
    public NBTTagCompound downloadSettings() {
        NBTTagCompound output = new NBTTagCompound();

        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        return output;
    }

    /**
     * Download settings with filters for memory card + keybind.
     * Does NOT save upgrades to avoid duplication (upgrades stay in the interface).
     */
    public NBTTagCompound downloadSettingsWithFilter() {
        NBTTagCompound output = downloadSettings();
        this.filterInventory.writeToNBT(output, "filter");

        return output;
    }

    /**
     * Download settings with filters AND upgrades for disassembly.
     * Upgrades are saved because the interface is being broken and dropped as an item.
     */
    public NBTTagCompound downloadSettingsForDismantle() {
        NBTTagCompound output = downloadSettingsWithFilter();
        this.upgradeInventory.writeToNBT(output, "upgrades");

        return output;
    }

    /**
     * Upload settings from NBT (memory card or dismantle).
     * Upgrades are restored BEFORE filters to ensure capacity cards are in place,
     * enabling extra filter pages before filter restoration.
     */
    public void uploadSettings(NBTTagCompound compound, EntityPlayer player) {
        if (compound == null) return;

        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getInteger("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Merge upgrades FIRST (capacity cards enable extra pages for filters)
        if (compound.hasKey("upgrades")) {
            mergeUpgradesFromNBT(compound, "upgrades");
        }

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey("filter")) {
            mergeFiltersFromNBT(compound, "filter", player);
        }
    }

    /**
     * Merge upgrades from NBT into the current upgrade inventory.
     * Only adds upgrades to empty slots to avoid duplication.
     */
    private void mergeUpgradesFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name)) return;

        AppEngInternalInventory sourceUpgrades = new AppEngInternalInventory(null, UPGRADE_SLOTS, 1);
        sourceUpgrades.readFromNBT(data, name);

        for (int i = 0; i < sourceUpgrades.getSlots(); i++) {
            ItemStack sourceUpgrade = sourceUpgrades.getStackInSlot(i);
            if (sourceUpgrade.isEmpty()) continue;

            // Find an empty slot for this upgrade
            int targetSlot = -1;
            for (int j = 0; j < this.upgradeInventory.getSlots(); j++) {
                if (this.upgradeInventory.getStackInSlot(j).isEmpty()) {
                    targetSlot = j;
                    break;
                }
            }

            if (targetSlot >= 0) {
                this.upgradeInventory.setStackInSlot(targetSlot, sourceUpgrade.copy());
            }
            // If no empty slots, silently skip (upgrades are full)
        }

        this.refreshUpgrades();
    }

    /**
     * Merge filters from NBT into the current filter inventory.
     * Only adds filters to empty slots; skips filters that already exist.
     * Reports to the player which filters couldn't be added if slots were full.
     */
    private void mergeFiltersFromNBT(NBTTagCompound data, String name, @Nullable EntityPlayer player) {
        if (!data.hasKey(name)) return;

        // Create a temporary inventory to load the source filters
        AppEngInternalInventory sourceFilters = new AppEngInternalInventory(null, FILTER_SLOTS, 1);
        sourceFilters.readFromNBT(data, name);

        List<ItemStack> skippedFilters = new ArrayList<>();

        for (int i = 0; i < sourceFilters.getSlots(); i++) {
            ItemStack sourceFilter = sourceFilters.getStackInSlot(i);
            if (sourceFilter.isEmpty()) continue;

            ItemStackKey sourceKey = ItemStackKey.of(sourceFilter);
            if (sourceKey == null) continue;

            // Skip if this filter already exists in the target
            if (this.filterToSlotMap.containsKey(sourceKey)) continue;

            // Find an empty slot to add this filter
            int targetSlot = findEmptyFilterSlot();
            if (targetSlot < 0) {
                // No empty slots - track this filter as skipped
                skippedFilters.add(sourceFilter.copy());
                continue;
            }

            // Add the filter to the empty slot
            this.filterInventory.setStackInSlot(targetSlot, sourceFilter.copy());
            this.filterToSlotMap.put(sourceKey, targetSlot);
        }

        this.refreshFilterMap();

        // Notify the player about skipped filters
        if (player != null && !skippedFilters.isEmpty()) {
            String filters = skippedFilters.stream()
                .map(ItemStack::getDisplayName)
                .reduce((a, b) -> a + "\n- " + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation("message.cells.filters_not_added", skippedFilters.size(), filters));
        }
    }

    /**
     * Handle inventory changes. Call from host's onChangeInventory.
     */
    public void onChangeInventory(IItemHandler inv, int slot, ItemStack removed, ItemStack added) {
        if (inv == this.filterInventory) {
            this.refreshFilterMap();

            if (this.host.isExport()) {
                // Export: if filter was removed or changed, return orphaned items in that slot to network
                if (!removed.isEmpty()) returnSlotToNetwork(slot, false);
            }

            this.wakeUpIfAdaptive();
        } else if (inv == this.upgradeInventory) {
            this.refreshUpgrades();
        } else if (inv == this.storageInventory) {
            // TODO: may be pretty bad for performance if we have a lot of item changes
            ItemStack is = this.host.isExport() ? removed : added;
            if (!is.isEmpty()) this.wakeUpIfAdaptive();
        }

        this.host.markDirtyAndSave();
    }

    // ============================== Tick handling ==============================

    /**
     * Create a TickingRequest based on current configuration.
     */
    public TickingRequest getTickingRequest() {
        if (this.pollingRate > 0) {
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

    /**
     * Handle a tick. Returns the appropriate rate modulation.
     */
    public TickRateModulation onTick() {
        if (!this.host.getGridProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = this.host.isExport() ? exportItems() : importItems();

        // If using fixed polling rate, always use SAME to maintain interval
        if (this.pollingRate > 0) return TickRateModulation.SAME;
        // If using adaptive polling, try to get work done faster
        if (didWork) return TickRateModulation.FASTER;

        return hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    /**
     * Check if there's work to do based on direction.
     * Import: any filtered slot has items to push.
     * Export: any filtered slot needs items from the network.
     */
    public boolean hasWorkToDo() {
        if (this.host.isExport()) {
            // Check if any configured slot needs items from the network
            for (int i : this.filterSlotList) {
                ItemStack current = this.storageInventory.getStackInSlot(i);
                if (current.isEmpty() || current.getCount() < this.maxSlotSize) return true;
            }
        } else {
            // Check if any filtered slot has items to import
            // TODO: could probably optimize that by adding a dirty flag
            for (int i : this.filterToSlotMap.values()) {
                if (!this.storageInventory.getStackInSlot(i).isEmpty()) return true;
            }
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     * Called when network state changes to ensure the device starts ticking.
     */
    public void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.host.getGridProxy().getTick().alertDevice(this.host.getGridProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
    }

    /**
     * Import items from storage slots into the ME network.
     * @return true if any items were imported
     */
    private boolean importItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            for (int i : this.filterToSlotMap.values()) {
                ItemStack stack = this.storageInventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                if (aeStack == null) continue;

                // Try to insert into network
                IAEItemStack remaining = itemStorage.injectItems(aeStack, Actionable.MODULATE, this.host.getActionSource());
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

    /**
     * Export items from the ME network into storage slots.
     * First returns any orphaned or overflow items to the network,
     * then requests items from the network for slots that need them.
     * @return true if any items were exported
     */
    private boolean exportItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            // First, return any orphaned or overflow items to the network
            returnOrphanedItemsToNetwork();
            returnOverflowToNetwork();

            for (int i : this.filterSlotList) {
                ItemStack filterStack = this.filterInventory.getStackInSlot(i);
                if (filterStack.isEmpty()) continue;

                ItemStack current = this.storageInventory.getStackInSlot(i);

                // Skip slots where current items don't match filter (orphaned items)
                if (!current.isEmpty()) {
                    ItemStackKey filterKey = this.slotToFilterMap.get(i);
                    if (filterKey == null || !filterKey.matches(current)) continue;
                }

                int currentCount = current.isEmpty() ? 0 : current.getCount();
                int space = this.maxSlotSize - currentCount;
                if (space <= 0) continue;

                // Request items from network
                IAEItemStack request = AEItemStack.fromItemStack(filterStack);
                if (request == null) continue;

                request.setStackSize(space);

                // Try to extract from network
                IAEItemStack extracted = itemStorage.extractItems(request, Actionable.MODULATE, this.host.getActionSource());
                if (extracted == null || extracted.getStackSize() <= 0) continue;

                // Add to storage slot
                if (current.isEmpty()) {
                    ItemStack newStack = extracted.createItemStack();
                    this.storageInventory.setStackInSlot(i, newStack);
                } else {
                    current.grow((int) extracted.getStackSize());
                    this.storageInventory.setStackInSlot(i, current);
                }

                didWork = true;
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        return didWork;
    }

    /**
     * Try to insert items into the ME network.
     * @return Items that couldn't be inserted (empty if all inserted)
     */
    public ItemStack insertItemsIntoNetwork(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        try {
            IStorageGrid storage = this.host.getGridProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            IAEItemStack toInsert = AEItemStack.fromItemStack(stack);
            if (toInsert == null) return stack;

            IAEItemStack notInserted = itemStorage.injectItems(toInsert, Actionable.MODULATE, this.host.getActionSource());
            if (notInserted == null || notInserted.getStackSize() == 0) return ItemStack.EMPTY;

            return notInserted.createItemStack();
        } catch (GridAccessException e) {
            return stack;
        }
    }

    /**
     * Return all items in a specific storage slot back to the ME network.
     * Items that cannot be returned are dropped on the ground if force is true.
     */
    public void returnSlotToNetwork(int slot, boolean force) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return;

        ItemStack stack = this.storageInventory.getStackInSlot(slot);
        if (stack.isEmpty()) return;

        ItemStack remaining = insertItemsIntoNetwork(stack);

        // Drop remaining items on the ground if force is true
        if (force && !remaining.isEmpty()) {
            dropItemsOnGround(remaining);
            remaining = ItemStack.EMPTY;
        }

        this.storageInventory.setStackInSlot(slot, remaining);
        this.host.markDirtyAndSave();
    }

    /**
     * Return overflow items (items exceeding maxSlotSize) back to the ME network.
     */
    private void returnOverflowToNetwork() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int overflow = stack.getCount() - this.maxSlotSize;
            if (overflow <= 0) continue;

            ItemStack overflowStack = stack.copy();
            overflowStack.setCount(overflow);

            ItemStack remaining = insertItemsIntoNetwork(overflowStack);

            // Reduce the stack in the slot
            stack.shrink(overflow - remaining.getCount());
            this.storageInventory.setStackInSlot(i, stack);
        }

        this.host.markDirtyAndSave();
    }

    /**
     * Return all orphaned items (items that don't match their filter) to the ME network.
     */
    public void returnOrphanedItemsToNetwork() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack storage = this.storageInventory.getStackInSlot(i);
            if (storage.isEmpty()) continue;

            // If no filter or items don't match filter, return them to network
            ItemStackKey filterKey = this.slotToFilterMap.get(i);
            boolean isOrphaned = filterKey == null || !filterKey.matches(storage);

            if (isOrphaned) returnSlotToNetwork(i, false);
        }
    }

    /**
     * Drop items on the ground at the host's position.
     */
    private void dropItemsOnGround(ItemStack stack) {
        if (stack.isEmpty()) return;

        World world = this.host.getHostWorld();
        BlockPos pos = this.host.getHostPos();
        if (world == null || pos == null) return;

        EntityItem entity = new EntityItem(
            world,
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            stack
        );
        world.spawnEntity(entity);
    }

    /**
     * Collect stored items that should be dropped (not upgrades).
     * Used during wrench dismantling where upgrades are saved to NBT.
     */
    public void getStorageDrops(List<ItemStack> drops) {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    /**
     * Collect all items that should be dropped when this interface is broken normally.
     * This method is NOT called during wrench dismantling (tiles use disableDrops(),
     * parts check the wrenched flag) - in that case, upgrades are saved to NBT instead.
     */
    public void getDrops(List<ItemStack> drops) {
        // Drop stored items
        getStorageDrops(drops);

        // Drop upgrades (only during normal breaking - wrench path saves them to NBT)
        for (int i = 0; i < this.upgradeInventory.getSlots(); i++) {
            ItemStack stack = this.upgradeInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    // ============================== External handlers ==============================

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
    // FIXME: inventory could be unified with FluidInterfaceLogic's filtered handler if not for
    //        AppEngInternalInventory. Item interfaces use AppEngInternalInventory (required by
    //        AENetworkInvTile.getInternalInventory()), while fluid interfaces use a plain
    //        AEFluidTank array. Unifying would require either moving both to a common
    //        IItemHandler abstraction, or using the static inventory approach from the L216 FIXME.
    private static class FilteredStorageHandler implements IItemHandler {
        private final ItemInterfaceLogic logic;

        public FilteredStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            // Expose 1 dummy slot so hoppers see an empty slot and don't think we're full.
            return 1 + logic.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            // Slot 0 is the dummy slot - always empty
            if (slot <= 0) return ItemStack.EMPTY;

            // Slots 1 through filterSlotList.size() are actual filter slots
            int filterIndex = slot - 1;
            if (filterIndex >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(filterIndex);
            return logic.storageInventory.getStackInSlot(storageSlot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Delegate to the logic's slotless insertion, which handles filter matching,
            // overflow/trash-unselected upgrades, and maxStackSize bypass
            return logic.slotlessInsertItem(stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Import interface does not allow external extraction
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return logic.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // For slotless operation, we check if ANY filter accepts this item
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            return logic.filterToSlotMap.containsKey(key);
        }
    }

    /**
     * Wrapper handler that exposes storage slots for extraction only.
     * Does not allow external insertion (export-only interface).
     */
    private static class ExportStorageHandler implements IItemHandler {
        private final ItemInterfaceLogic logic;

        public ExportStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            return logic.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(slot);
            return logic.storageInventory.getStackInSlot(storageSlot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // Export interface does not allow external insertion
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(slot);
            return logic.storageInventory.extractItem(storageSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return logic.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // External insertion not allowed
            return false;
        }
    }
}
