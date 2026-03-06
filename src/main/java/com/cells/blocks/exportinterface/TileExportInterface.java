package com.cells.blocks.exportinterface;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
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
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.ItemOverflowCard;
import com.cells.util.InventoryMigrationHelper;
import com.cells.util.ItemStackKey;
import com.cells.util.TickManagerHelper;


/**
 * Tile entity for the Export Interface block.
 * Provides filter slots and storage slots (expandable with Capacity Cards).
 * Requests items from the ME network that match the filter configuration.
 * Exposes stored items for external extraction.
 */
public class TileExportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory, IExportInterfaceInventoryHost {

    public static final int SLOTS_PER_PAGE = 36;
    public static final int MAX_CAPACITY_CARDS = 4;
    public static final int MAX_PAGES = 1 + MAX_CAPACITY_CARDS;
    public static final int FILTER_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int STORAGE_SLOTS = SLOTS_PER_PAGE * MAX_PAGES;
    public static final int UPGRADE_SLOTS = 4;

    // Filter inventory - ghost items only (1 stack size each)
    private final AppEngInternalInventory filterInventory = new AppEngInternalInventory(this, FILTER_SLOTS, 1);

    // Storage inventory - actual items
    private final AppEngInternalInventory storageInventory;

    // Upgrade inventory - accepts only specific upgrade cards
    private final AppEngInternalInventory upgradeInventory;

    // Wrapper that exposes storage slots for extraction only
    private final ExportStorageHandler exportHandler;

    // Max slot size for all storage slots
    private int maxSlotSize = TileImportInterface.DEFAULT_MAX_SLOT_SIZE;

    // Polling rate in ticks (0 = adaptive AE2 default, nonzero = fixed interval)
    private int pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    // Mapping of filter items to their corresponding storage slot index for quick lookup
    final private Map<ItemStackKey, Integer> filterToSlotMap = new HashMap<>();

    // List of slot indices that have filters, in slot order
    private List<Integer> filterSlotList = new ArrayList<>();

    // Number of installed capacity upgrades (adds pages)
    private int installedCapacityUpgrades = 0;

    // Current GUI page index (0-based)
    private int currentPage = 0;

    // Action source for network operations
    private final IActionSource actionSource;

    public TileExportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with large stack support
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, TileImportInterface.DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return TileExportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                // Only accept items that match the filter for this slot
                return TileExportInterface.this.isItemValidForSlot(slot, stack);
            }
        };

        // Create upgrade inventory with filtering for specific upgrade cards
        this.upgradeInventory = new AppEngInternalInventory(this, UPGRADE_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileExportInterface.this.isValidUpgrade(stack);
            }
        };

        this.exportHandler = new ExportStorageHandler(this);

        refreshUpgrades();
        refreshFilterMap();
    }

    /**
     * Refresh the status of installed upgrades. Should be called whenever upgrade slots change.
     */
    public void refreshUpgrades() {
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
     * Refresh the filter to slot mapping. Should be called whenever filter slots change.
     */
    public void refreshFilterMap() {
        this.filterToSlotMap.clear();

        final int filterSlots = this.filterInventory.getSlots();
        final int storageSlots = this.storageInventory.getSlots();
        final int maxSlots = Math.min(filterSlots, storageSlots);

        // Build list of valid (internal) slot indices for quick access
        List<Integer> validSlots = new ArrayList<>();

        for (int i = 0; i < maxSlots; i++) {
            ItemStack filterStack = this.filterInventory.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                this.filterToSlotMap.put(ItemStackKey.of(filterStack), i);
                validSlots.add(i);
            }
        }

        this.filterSlotList = validSlots;
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
     * Get the number of installed capacity upgrades.
     */
    public int getInstalledCapacityUpgrades() {
        return this.installedCapacityUpgrades;
    }

    /**
     * Get the total number of pages (1 base + 1 per capacity card).
     */
    public int getTotalPages() {
        return 1 + this.installedCapacityUpgrades;
    }

    /**
     * Get the current page index (0-based).
     */
    public int getCurrentPage() {
        return this.currentPage;
    }

    /**
     * Set the current page index (0-based), clamped to valid range.
     */
    public void setCurrentPage(int page) {
        this.currentPage = Math.max(0, Math.min(page, this.installedCapacityUpgrades));
    }

    /**
     * Get the starting slot index for the current page.
     */
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
     * Check if an item is a valid upgrade for this interface.
     * Accepts Capacity Card (max 4).
     */
    public boolean isValidUpgrade(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check for AE2 capacity card
        if (stack.getItem() instanceof IUpgradeModule) {
            IUpgradeModule module = (IUpgradeModule) stack.getItem();
            if (module.getType(stack) == Upgrades.CAPACITY) {
                return countCapacityUpgrades() < MAX_CAPACITY_CARDS;
            }
        }

        return false;
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        ItemStack filterStack = this.filterInventory.getStackInSlot(slot);
        if (filterStack.isEmpty()) return false;

        // Check if the item matches the filter
        return ItemStack.areItemsEqual(stack, filterStack) && ItemStack.areItemStackTagsEqual(stack, filterStack);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        // Use migration helper to prevent old saves from shrinking our inventory
        InventoryMigrationHelper.readFromNBTWithoutShrinking(this.filterInventory, data, "filter");
        // Note: storageInventory is saved by AEBaseInvTile via getInternalInventory() as "inv"
        this.upgradeInventory.readFromNBT(data, "upgrades");
        this.maxSlotSize = data.getInteger("maxSlotSize");
        this.pollingRate = data.getInteger("pollingRate");

        if (this.maxSlotSize < TileImportInterface.MIN_MAX_SLOT_SIZE) {
            this.maxSlotSize = TileImportInterface.MIN_MAX_SLOT_SIZE;
        }

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
    @Nonnull
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "filter");
        // Note: storageInventory is saved by AEBaseInvTile via getInternalInventory() as "inv"
        this.upgradeInventory.writeToNBT(data, "upgrades");
        data.setInteger("maxSlotSize", this.maxSlotSize);
        data.setInteger("pollingRate", this.pollingRate);

        return data;
    }

    @Nonnull
    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (output == null) output = new NBTTagCompound();

        // Save slot size and polling rate for both memory card and dismantle
        output.setInteger("maxSlotSize", this.maxSlotSize);
        output.setInteger("pollingRate", this.pollingRate);

        // Save filter inventory only when dismantling (not for memory card)
        if (from == SettingsFrom.DISMANTLE_ITEM) {
            this.filterInventory.writeToNBT(output, "filter");
        }

        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);

        if (compound == null) return;

        // Load slot size and polling rate for both memory card and dismantle
        if (compound.hasKey("maxSlotSize")) {
            this.setMaxSlotSize(compound.getInteger("maxSlotSize"));
        }
        if (compound.hasKey("pollingRate")) {
            this.setPollingRate(compound.getInteger("pollingRate"), player);
        }

        // Merge filter inventory from memory card instead of replacing
        if (compound.hasKey("filter")) {
            mergeFiltersFromNBT(compound, "filter", player);
        }
    }

    /**
     * Merge filters from NBT into the current filter inventory.
     * Only adds filters to empty slots; skips filters that already exist.
     * Reports to the player which filters couldn't be added if slots were full.
     *
     * @param data The NBT compound containing the filter data
     * @param name The key name for the filter tag list
     * @param player The player to notify about results, or null to skip notification
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
                .reduce((a, b) -> a + "\n -" + b)
                .orElse("");
            player.sendMessage(new TextComponentTranslation(
                "message.cells.export_interface.filters_not_added",
                skippedFilters.size(),
                filters
            ));
        }
    }

    /**
     * Find the first empty filter slot.
     * @return The slot index, or -1 if no empty slots are available
     */
    private int findEmptyFilterSlot() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            if (this.filterInventory.getStackInSlot(i).isEmpty()) return i;
        }

        return -1;
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
        int oldSize = this.maxSlotSize;
        this.maxSlotSize = Math.max(TileImportInterface.MIN_MAX_SLOT_SIZE, size);

        // Update slot limits in the underlying inventory
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            this.storageInventory.setMaxStackSize(i, this.maxSlotSize);
        }

        this.markDirty();

        // If slot size was reduced, return overflow items to the network
        if (oldSize > this.maxSlotSize) returnOverflowToNetwork();

        // Slots may now have room for more items after increasing the limit
        if (oldSize < this.maxSlotSize) this.wakeUpIfAdaptive();
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
        this.markDirty();

        // Re-register with the tick manager to apply the new TickingRequest bounds.
        if (!TickManagerHelper.reRegisterTickable(this.getProxy().getNode(), this)) {
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("chat.cells.polling_rate_delayed"));
            }
        }
    }

    @Override
    public BlockPos getHostPos() {
        return this.getPos();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.filterInventory) {
            // Filter changed - rebuild the filter-to-slot mapping and wake up
            // so the device starts pushing items matching the new filter
            this.refreshFilterMap();

            // If filter was removed or changed, return orphaned items in that slot to network
            if (!removed.isEmpty()) returnSlotToNetwork(slot, false);

            this.wakeUpIfAdaptive();
        } else if (inv == this.upgradeInventory) {
            // Upgrade changed - refresh cached upgrade flags
            this.refreshUpgrades();
        } else if (inv == this.storageInventory && !removed.isEmpty()) {
            // Item extracted - wake up to request more items
            this.wakeUpIfAdaptive();
        }

        this.markDirty();
    }

    /**
     * Return all items in a specific storage slot back to the ME network.
     * Items that cannot be returned are dropped on the ground.
     *
     * @param slot The slot index to return items from
     * @param force Whether to force items out of the interface, dropping them on the ground if necessary
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

        this.markDirty();
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

        this.markDirty();
    }

    /**
     * Return all orphaned items (items that don't match their filter) to the ME network.
     * This handles mismatched inventory slots where filter was changed after items were stored.
     */
    public void returnOrphanedItemsToNetwork() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack storage = this.storageInventory.getStackInSlot(i);
            if (storage.isEmpty()) continue;

            ItemStack filter = this.filterInventory.getStackInSlot(i);

            // If no filter or items don't match filter, return them to network
            boolean isOrphaned = filter.isEmpty() ||
                !ItemStack.areItemsEqual(storage, filter) ||
                !ItemStack.areItemStackTagsEqual(storage, filter);

            if (isOrphaned) returnSlotToNetwork(i, false);
        }
    }

    /**
     * Clear all filters.
     * Orphaned items will be returned to network on the next export.
     */
    public void clearFilters() {
        for (int i = 0; i < this.filterInventory.getSlots(); i++) {
            this.filterInventory.setStackInSlot(i, ItemStack.EMPTY);
        }

        this.refreshFilterMap();
        this.markDirty();
    }

    /**
     * Try to insert items into the ME network.
     *
     * @param stack The items to insert
     * @return Items that couldn't be inserted (empty if all inserted)
     */
    private ItemStack insertItemsIntoNetwork(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            IAEItemStack toInsert = AEItemStack.fromItemStack(stack);
            if (toInsert == null) return stack;

            IAEItemStack notInserted = itemStorage.injectItems(toInsert, Actionable.MODULATE, this.actionSource);

            if (notInserted == null || notInserted.getStackSize() == 0) return ItemStack.EMPTY;

            return notInserted.createItemStack();
        } catch (GridAccessException e) {
            // Not connected to grid, return all items
            return stack;
        }
    }

    /**
     * Drop items on the ground at the tile's position.
     */
    private void dropItemsOnGround(ItemStack stack) {
        if (stack.isEmpty() || this.world == null) return;

        EntityItem entity = new EntityItem(
            this.world,
            this.pos.getX() + 0.5,
            this.pos.getY() + 0.5,
            this.pos.getZ() + 0.5,
            stack
        );
        this.world.spawnEntity(entity);
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
    @Nonnull
    public AECableType getCableConnectionType(@Nonnull final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // IExportInterfaceHost implementation

    @Override
    public int getMainGuiId() {
        return CellsGuiHandler.GUI_EXPORT_INTERFACE;
    }

    @Override
    public String getGuiTitleLangKey() {
        return "gui.cells.export_interface.title";
    }

    @Override
    public ItemStack getBackButtonStack() {
        return new ItemStack(this.getBlockType());
    }

    // IGridTickable implementation

    @Override
    @Nonnull
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        // If polling rate is set, use fixed interval; otherwise use adaptive AE2 rates
        if (this.pollingRate > 0) {
            return new TickingRequest(
                this.pollingRate,
                this.pollingRate,
                false,
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
    @Nonnull
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = exportItems();

        // If using fixed polling rate, always use SAME to maintain interval
        if (this.pollingRate > 0) return TickRateModulation.SAME;

        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        // Check if any configured slot needs items from the network
        for (int i : this.filterSlotList) {
            ItemStack current = this.storageInventory.getStackInSlot(i);
            if (current.isEmpty() || current.getCount() < this.maxSlotSize) return true;
        }

        return false;
    }

    /**
     * Wake up the tick manager if using adaptive polling (rate=0).
     * Called when filters or settings change to ensure the device starts ticking
     * to pull items that now match the updated configuration.
     */
    private void wakeUpIfAdaptive() {
        if (this.pollingRate > 0) return;

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (GridAccessException e) {
            // Not connected to grid
        }
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
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            // First, return any orphaned or overflow items to the network
            // This must happen before requesting new items
            returnOrphanedItemsToNetwork();
            returnOverflowToNetwork();

            for (int i : this.filterSlotList) {
                ItemStack filterStack = this.filterInventory.getStackInSlot(i);
                if (filterStack.isEmpty()) continue;

                ItemStack current = this.storageInventory.getStackInSlot(i);

                // Skip slots where current items don't match filter (orphaned items)
                // This prevents requesting more items when the slot has mismatched content
                if (!current.isEmpty()) {
                    if (!ItemStack.areItemsEqual(current, filterStack) ||
                        !ItemStack.areItemStackTagsEqual(current, filterStack)) {
                        continue;
                    }
                }

                int currentCount = current.isEmpty() ? 0 : current.getCount();
                int space = this.maxSlotSize - currentCount;
                if (space <= 0) continue;

                // Request items from network
                IAEItemStack request = AEItemStack.fromItemStack(filterStack);
                if (request == null) continue;

                request.setStackSize(space);

                // Try to extract from network
                IAEItemStack extracted = itemStorage.extractItems(request, Actionable.MODULATE, this.actionSource);
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
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.exportHandler);
        }

        return super.getCapability(capability, facing);
    }

    /**
     * Wrapper handler that exposes storage slots for extraction only.
     * Does not allow external insertion (export-only interface).
     */
    private static class ExportStorageHandler implements IItemHandler {
        private final TileExportInterface tile;

        public ExportStorageHandler(TileExportInterface tile) {
            this.tile = tile;
        }

        @Override
        public int getSlots() {
            return tile.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= tile.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = tile.filterSlotList.get(slot);
            return tile.storageInventory.getStackInSlot(storageSlot);
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
            if (slot < 0 || slot >= tile.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = tile.filterSlotList.get(slot);
            return tile.storageInventory.extractItem(storageSlot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return tile.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // External insertion not allowed
            return false;
        }
    }
}
