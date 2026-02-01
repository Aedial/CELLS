package com.cells.cells.compacting;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import com.cells.Cells;
import com.cells.ItemRegistry;


/**
 * Inventory implementation for compacting storage cells.
 * 
 * This cell REQUIRES partitioning before it can accept items. The partitioned item
 * defines the compression chain (e.g., iron ingot -> iron block / iron nugget).
 * 
 * Storage is exposed as up to 3 compression tiers:
 * - Tier 0: Most compressed form (e.g., iron block) - virtual utility
 * - Tier 1: Middle form / partitioned item (e.g., iron ingot) - actual storage
 * - Tier 2: Least compressed form (e.g., iron nugget) - virtual utility
 * 
 * Only the partitioned item tier counts toward storage capacity.
 * Compressed/decompressed forms are virtual utilities for network access.
 */
public class CompactingCellInventory implements ICellInventory<IAEItemStack> {

    private static final String NBT_STORED_BASE_UNITS = "storedBaseUnits";
    private static final String NBT_CONV_RATES = "convRates";
    private static final String NBT_PROTO_ITEMS = "protoItems";
    private static final String NBT_MAIN_TIER = "mainTier";
    private static final String NBT_CACHED_PARTITION = "cachedPartition";

    private static final int MAX_TIERS = 3;

    // Cached partition item for detecting changes
    private ItemStack cachedPartitionItem = ItemStack.EMPTY;

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final IInternalCompactingCell cellType;

    private final NBTTagCompound tagCompound;

    // Compression chain data
    private final ItemStack[] protoStack = new ItemStack[MAX_TIERS];
    private final int[] convRate = new int[MAX_TIERS];

    // Single storage pool in base units (lowest tier, rate=1)
    private long storedBaseUnits = 0;

    // The tier that matches the partitioned item (the "main" tier for storage counting)
    private int mainTier = -1;

    public CompactingCellInventory(IInternalCompactingCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

        this.tagCompound = Platform.openNbtData(cellStack);

        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }

        loadFromNBT();
    }

    /**
     * Get the world from the action source for recipe lookups.
     */
    @Nullable
    private static World getWorldFromSource(@Nullable IActionSource src) {
        if (src == null) return null;

        // Try player first
        if (src.player().isPresent()) return src.player().get().world;

        // Try machine
        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getWorld();
        }

        return null;
    }

    /**
     * Get the grid from the action source for posting alterations.
     */
    @Nullable
    private static IGrid getGridFromSource(@Nullable IActionSource src) {
        if (src == null) return null;

        // Try machine first
        if (src.machine().isPresent()) {
            IActionHost host = src.machine().get();
            IGridNode node = host.getActionableNode();
            if (node != null) return node.getGrid();
        }

        return null;
    }

    /**
     * Notify the grid that all chain tiers have changed.
     * This is necessary because extracting/injecting one tier affects all tiers.
     * 
     * @param src The action source
     * @param oldBaseUnits The base units before the operation
     * @param operatedSlot The slot that was directly operated on (-1 if none to exclude)
     */
    private void notifyGridOfAllTierChanges(@Nullable IActionSource src, long oldBaseUnits, int operatedSlot) {
        IGrid grid = getGridFromSource(src);
        if (grid == null) return;

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return;

        // Calculate changes for each tier and post them
        // Skip the operated slot since the grid already knows about that change
        List<IAEItemStack> changes = new ArrayList<>();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (i == operatedSlot) continue; // Grid already knows about this one
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            long oldCount = oldBaseUnits / convRate[i];
            long newCount = storedBaseUnits / convRate[i];
            long delta = newCount - oldCount;

            if (delta == 0) continue;

            IAEItemStack stack = channel.createStack(protoStack[i]);
            if (stack != null) {
                stack.setStackSize(delta);
                changes.add(stack);
            }
        }

        if (!changes.isEmpty()) {
            storageGrid.postAlterationOfStoredItems(channel, changes, src);
        }
    }

    private void loadFromNBT() {
        // Load stored base units (saved as _hi and _lo suffixed keys)
        if (tagCompound.hasKey(NBT_STORED_BASE_UNITS + "_hi")) {
            storedBaseUnits = loadLong(tagCompound, NBT_STORED_BASE_UNITS);
        }

        if (tagCompound.hasKey(NBT_CONV_RATES)) {
            int[] rates = tagCompound.getIntArray(NBT_CONV_RATES);
            for (int i = 0; i < Math.min(rates.length, MAX_TIERS); i++) convRate[i] = rates[i];
        }

        if (tagCompound.hasKey(NBT_PROTO_ITEMS)) {
            NBTTagCompound protoNbt = tagCompound.getCompoundTag(NBT_PROTO_ITEMS);
            for (int i = 0; i < MAX_TIERS; i++) {
                if (protoNbt.hasKey("item" + i)) {
                    protoStack[i] = new ItemStack(protoNbt.getCompoundTag("item" + i));
                }
            }
        }

        if (tagCompound.hasKey(NBT_MAIN_TIER)) mainTier = tagCompound.getInteger(NBT_MAIN_TIER);

        if (tagCompound.hasKey(NBT_CACHED_PARTITION)) {
            cachedPartitionItem = new ItemStack(tagCompound.getCompoundTag(NBT_CACHED_PARTITION));
        }

        // Recalculate mainTier if it's invalid but we have chain data
        // This handles cases where old NBT had mainTier saved incorrectly
        if (mainTier < 0 && !isCompressionChainEmpty() && !cachedPartitionItem.isEmpty()) {
            recalculateMainTierFromCachedPartition();
        }
    }

    private static long loadLong(NBTTagCompound tag, String key) {
        int hi = tag.getInteger(key + "_hi");
        int lo = tag.getInteger(key + "_lo");

        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    private static void saveLong(NBTTagCompound tag, String key, long value) {
        tag.setInteger(key + "_hi", (int) (value >> 32));
        tag.setInteger(key + "_lo", (int) value);
    }

    /**
     * Multiply two longs with overflow protection.
     * Returns Long.MAX_VALUE if overflow would occur.
     */
    private static long multiplyWithOverflowProtection(long a, long b) {
        if (a == 0 || b == 0) return 0;
        if (a < 0 || b < 0) return 0; // We don't deal with negatives

        // Check for overflow: if a > MAX / b, then a * b would overflow
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;

        return a * b;
    }

    /**
     * Add two longs with overflow protection.
     * Returns Long.MAX_VALUE if overflow would occur.
     */
    private static long addWithOverflowProtection(long a, long b) {
        if (a < 0 || b < 0) return Math.max(a, b); // We don't deal with negatives

        // Check for overflow
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE;

        return a + b;
    }

    private void saveToNBT() {
        saveLong(tagCompound, NBT_STORED_BASE_UNITS, storedBaseUnits);

        // Only save chain data (including mainTier) if we actually have a chain initialized.
        // This prevents an old handler with empty chain from overwriting
        // valid chain data that was written by another handler instance.
        if (!isCompressionChainEmpty()) {
            tagCompound.setInteger(NBT_MAIN_TIER, mainTier);
            tagCompound.setIntArray(NBT_CONV_RATES, convRate);

            NBTTagCompound protoNbt = new NBTTagCompound();
            for (int i = 0; i < MAX_TIERS; i++) {
                if (!protoStack[i].isEmpty()) {
                    NBTTagCompound itemNbt = new NBTTagCompound();
                    protoStack[i].writeToNBT(itemNbt);
                    protoNbt.setTag("item" + i, itemNbt);
                }
            }
            tagCompound.setTag(NBT_PROTO_ITEMS, protoNbt);

            if (!cachedPartitionItem.isEmpty()) {
                NBTTagCompound partNbt = new NBTTagCompound();
                cachedPartitionItem.writeToNBT(partNbt);
                tagCompound.setTag(NBT_CACHED_PARTITION, partNbt);
            }
        } else if (storedBaseUnits == 0 && !hasPartition()) {
            // Cell is truly empty and unpartitioned - clear chain data
            tagCompound.removeTag(NBT_CONV_RATES);
            tagCompound.removeTag(NBT_PROTO_ITEMS);
            tagCompound.removeTag(NBT_CACHED_PARTITION);
        }
        // If chain is empty but partition exists, don't touch chain NBT -
        // another handler may have written valid data that we haven't loaded yet
    }

    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    /**
     * Initialize the compression chain for the given partitioned item.
     * The partitioned item becomes the "main" tier for storage counting.
     */
    private void initializeCompressionChain(@Nonnull ItemStack inputItem, @Nullable World world) {
        if (world == null) {
            // Fallback: just use the single item without compression
            protoStack[0] = inputItem.copy();
            protoStack[0].setCount(1);
            convRate[0] = 1;
            mainTier = 0;

            return;
        }

        CompactingHelper helper = new CompactingHelper(world);
        CompactingHelper.CompressionChain chain = helper.getCompressionChain(inputItem);

        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = chain.getStack(i);
            convRate[i] = chain.getRate(i);
        }

        // Find which tier matches the partitioned item (this is the main tier)
        mainTier = 0;
        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], inputItem)) {
                mainTier = i;
                break;
            }
        }
    }

    /**
     * Recalculate mainTier from the cached partition item.
     * Used when loading NBT with invalid mainTier but valid chain data.
     */
    private void recalculateMainTierFromCachedPartition() {
        if (cachedPartitionItem.isEmpty()) return;

        mainTier = 0;
        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], cachedPartitionItem)) {
                mainTier = i;
                break;
            }
        }

        Cells.LOGGER.info("Recalculated mainTier to " + mainTier + " for partition item: " + cachedPartitionItem);
    }

    /**
     * Get the slot index for the given item, or -1 if not matching.
     */
    private int getSlotForItem(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return i;
        }

        return -1;
    }

    /**
     * Check if this cell can accept the given item.
     * Returns the slot index or -1 if not acceptable.
     */
    private int canAcceptItem(@Nonnull IAEItemStack stack) {
        // Compacting cells REQUIRE partitioning
        if (!hasPartition()) return -1;

        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        // Check partition
        if (!isAllowedByPartition(stack)) return -1;

        // If compression chain is empty, we need to initialize it first
        if (isCompressionChainEmpty()) return 0;

        return getSlotForItem(stack);
    }

    private boolean isCompressionChainEmpty() {
        for (int i = 0; i < MAX_TIERS; i++) {
            if (!protoStack[i].isEmpty()) return false;
        }

        return true;
    }

    /**
     * Check if the compression chain has been initialized.
     * Public method for tooltip use - returns true if protoStack has items.
     */
    public boolean isChainInitialized() {
        return !isCompressionChainEmpty();
    }

    /**
     * Check if the cell has any items stored (in base units).
     * This is more accurate than getStoredItemCount() which uses main tier.
     */
    public boolean hasStoredItems() {
        return storedBaseUnits > 0;
    }

    /**
     * Check if NBT has chain data that hasn't been loaded into our local cache.
     * This can happen when another handler (e.g., from API call) writes to NBT
     * after this handler was constructed.
     *
     * @return true if NBT has chain data but local cache is empty
     */
    private boolean hasUnloadedNBTChainData() {
        if (!isCompressionChainEmpty()) return false;

        return tagCompound.hasKey(NBT_PROTO_ITEMS) && tagCompound.hasKey(NBT_CONV_RATES);
    }

    /**
     * Reload chain data from NBT if it has been updated externally.
     * Call this before operations that depend on the compression chain.
     */
    private void reloadFromNBTIfNeeded() {
        if (!hasUnloadedNBTChainData()) return;

        loadFromNBT();
    }

    /**
     * Check if an item is part of the compression chain.
     * Used by the handler to allow chain items through the filter.
     */
    public boolean isInCompressionChain(@Nonnull IAEItemStack stack) {
        if (stack == null) return false;

        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return true;
        }

        return false;
    }

    /**
     * Check if the cell has a partition configured.
     */
    public boolean hasPartition() {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return false;

        for (int i = 0; i < configInv.getSlots(); i++) {
            if (!configInv.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    /**
     * Check if the partition has changed since the compression chain was initialized.
     * Returns true if the chain needs to be reinitialized.
     */
    private boolean hasPartitionChanged() {
        ItemStack currentPartition = getFirstPartitionedItem();

        // Both empty = no change
        if ((currentPartition == null || currentPartition.isEmpty()) && cachedPartitionItem.isEmpty()) {
            return false;
        }

        // One empty, other not = changed
        if (currentPartition == null || currentPartition.isEmpty() || cachedPartitionItem.isEmpty()) {
            return true;
        }

        // Compare items
        return !areItemsEqual(currentPartition, cachedPartitionItem);
    }

    /**
     * Initialize the compression chain from the current partition setting.
     * Called from Cell Terminal when partition is set to prepare the chain immediately.
     * This allows the chain to be ready without needing to insert items first.
     */
    public void initializeChainFromPartition(@Nonnull World world) {
        updateCompressionChainIfNeeded(world);
    }

    /**
     * Initialize the compression chain for a specific item.
     * Called from Cell Terminal when partition is set, bypassing the config read
     * to avoid timing issues where the config NBT hasn't been flushed yet.
     * 
     * This method forces chain initialization even if a chain already exists,
     * as long as the cell has no stored items.
     */
    public void initializeChainForItem(@Nonnull ItemStack partitionItem, @Nonnull World world) {
        if (partitionItem.isEmpty()) return;
        if (storedBaseUnits > 0) return; // Has items, don't change chain

        // Force initialize the chain with the given partition item
        reset();
        initializeCompressionChain(partitionItem, world);
        cachedPartitionItem = partitionItem.copy();
        cachedPartitionItem.setCount(1);

        // Also set the partition in the config inventory so hasPartition() returns true
        setPartitionInConfig(partitionItem);

        // Use saveChanges() to notify container, not just saveToNBT()
        saveChanges();
    }

    /**
     * Sets the partition item in the config inventory.
     * <p>
     * Used by the API to ensure the config inventory is in sync with
     * the compression chain that was built.
     * </p>
     *
     * @param partitionItem The item to set as the partition
     */
    private void setPartitionInConfig(@Nonnull ItemStack partitionItem) {
        IItemHandler configInv = getConfigInventory();
        if (!(configInv instanceof IItemHandlerModifiable)) return;

        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) configInv;

        ItemStack partition = partitionItem.copy();
        partition.setCount(1);
        modifiable.setStackInSlot(0, partition);

        // Clear other slots
        for (int i = 1; i < modifiable.getSlots(); i++) {
            modifiable.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /**
     * Update the compression chain if the partition has changed or if chain needs initialization.
     * Call this before any operation that depends on the compression chain.
     * 
     * If the cell contains items, partition changes are blocked to prevent data loss.
     * The partition will be reverted to match the stored items.
     */
    private void updateCompressionChainIfNeeded(@Nullable World world) {
        // First, check if NBT has chain data written by another handler (e.g., API call)
        // that we haven't loaded yet. This handles the case where initializeChainForItem
        // was called from the API after this handler was constructed.
        reloadFromNBTIfNeeded();

        // Check if chain needs to be built (partition exists but chain is empty)
        boolean needsInitialization = hasPartition() && isCompressionChainEmpty();

        if (!needsInitialization && !hasPartitionChanged()) return;

        ItemStack currentPartition = getFirstPartitionedItem();

        // If cell has items, do not allow partition changes
        // Revert the partition back to the cached (correct) partition
        if (storedBaseUnits > 0) {
            revertPartitionToCached();

            return;
        }

        // Partition was removed - reset everything
        if (currentPartition == null || currentPartition.isEmpty()) {
            reset();
            cachedPartitionItem = ItemStack.EMPTY;
            saveChanges();

            return;
        }

        // Partition set or changed - initialize/reinitialize the chain
        reset();
        initializeCompressionChain(currentPartition, world);
        cachedPartitionItem = currentPartition.copy();
        cachedPartitionItem.setCount(1);
        saveChanges();
    }

    /**
     * Revert the partition config back to the cached partition item.
     * Used when someone tries to change partition while items are stored.
     */
    private void revertPartitionToCached() {
        if (cachedPartitionItem.isEmpty()) return;

        IItemHandler configInv = getConfigInventory();
        if (!(configInv instanceof IItemHandlerModifiable)) return;

        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) configInv;

        // Set first slot to cached partition, clear others
        modifiable.setStackInSlot(0, cachedPartitionItem.copy());
        for (int i = 1; i < modifiable.getSlots(); i++) modifiable.setStackInSlot(i, ItemStack.EMPTY);
    }

    private boolean isAllowedByPartition(@Nonnull IAEItemStack stack) {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return false;

        // Check if item matches any partition slot (or is in the compression chain)
        ItemStack definition = stack.getDefinition();

        // First check if it's in our compression chain
        for (int i = 0; i < MAX_TIERS; i++) {
            if (areItemsEqual(protoStack[i], definition)) return true;
        }

        // Check partition slots directly (for initial setup)
        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty() && areItemsEqual(partItem, definition)) return true;
        }

        return false;
    }

    /**
     * Get the first partitioned item, used to initialize compression chain.
     */
    @Nullable
    private ItemStack getFirstPartitionedItem() {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return null;

        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty()) return partItem.copy();
        }

        return null;
    }

    /**
     * Calculate total stored items in terms of the main tier (partitioned item).
     * Storage is held in base units and converted to main tier for capacity calculations.
     * Uses floor division - this is the actual extractable count.
     */
    private long getStoredInMainTier() {
        if (mainTier < 0 || mainTier >= MAX_TIERS) return 0;
        if (convRate[mainTier] <= 0) return 0;

        // Convert base units to main tier units (floor - actual count)
        return storedBaseUnits / convRate[mainTier];
    }

    /**
     * Calculate stored items in main tier with ceiling division for byte calculation.
     * This ensures we don't undercount storage usage (8 nuggets = 1 ingot for byte purposes).
     */
    private long getStoredInMainTierCeiling() {
        if (mainTier < 0 || mainTier >= MAX_TIERS) return 0;
        if (convRate[mainTier] <= 0) return 0;

        // Ceiling division: (a + b - 1) / b
        return (storedBaseUnits + convRate[mainTier] - 1) / convRate[mainTier];
    }

    /**
     * Get the maximum capacity in base units.
     * Accounts for type overhead in bytes.
     * 
     * Uses conservative calculation to ensure usedBytes never exceeds totalBytes.
     * The conversion chain between bytes <-> items <-> base units uses ceiling
     * divisions for display, so we must be slightly conservative here.
     */
    private long getMaxCapacityInBaseUnits() {
        long totalBytes = getTotalBytes();
        long typeBytes = isCompressionChainEmpty() ? 0 : getBytesPerType();
        long availableBytes = totalBytes - typeBytes;

        if (availableBytes <= 0) return 0;
        if (mainTier < 0 || mainTier >= MAX_TIERS || convRate[mainTier] <= 0) return 0;

        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate max main tier items that fit in available bytes
        // Use floor division - this is the actual number of complete items
        long maxMainTierItems = availableBytes * itemsPerByte;

        // Convert to base units
        // Subtract (convRate - 1) to ensure ceiling division in usedBytes doesn't overflow
        // This ensures: ceil(stored / convRate) / itemsPerByte <= availableBytes
        long maxBaseUnits = multiplyWithOverflowProtection(maxMainTierItems, convRate[mainTier]);

        // Reserve space for ceiling rounding: at most (convRate - 1) base units
        // plus (itemsPerByte - 1) items worth of rounding
        long reserveForRounding = (long)(convRate[mainTier] - 1) + (long)(itemsPerByte - 1) * convRate[mainTier];
        if (maxBaseUnits > reserveForRounding) {
            maxBaseUnits -= reserveForRounding;
        } else {
            maxBaseUnits = 0;
        }

        return maxBaseUnits;
    }

    /**
     * Get remaining capacity in base units.
     * This is the proper way to check capacity - directly compare base units.
     */
    private long getRemainingCapacityInBaseUnits() {
        return Math.max(0, getMaxCapacityInBaseUnits() - storedBaseUnits);
    }

    // =====================
    // Public getters for tooltip information
    // =====================

    /**
     * Get the partitioned (main) item stack.
     */
    @Nonnull
    public ItemStack getPartitionedItem() {
        if (mainTier >= 0 && mainTier < MAX_TIERS && !protoStack[mainTier].isEmpty()) {
            return protoStack[mainTier];
        }

        ItemStack firstPart = getFirstPartitionedItem();

        return firstPart != null ? firstPart : ItemStack.EMPTY;
    }

    /**
     * Get the higher tier (compressed) item, or empty if none.
     */
    @Nonnull
    public ItemStack getHigherTierItem() {
        if (mainTier <= 0) return ItemStack.EMPTY;

        for (int i = mainTier - 1; i >= 0; i--) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get the lower tier (decompressed) item, or empty if none.
     */
    @Nonnull
    public ItemStack getLowerTierItem() {
        if (mainTier < 0) return ItemStack.EMPTY;

        for (int i = mainTier + 1; i < MAX_TIERS; i++) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    private void reset() {
        for (int i = 0; i < MAX_TIERS; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }
        storedBaseUnits = 0;
        mainTier = -1;
    }

    /**
     * Check if the cell has an overflow card installed.
     * This upgrade causes excess items to be voided instead of rejected.
     */
    private boolean hasOverflowCard() {
        IItemHandler upgrades = getUpgradesInventory();
        if (upgrades == null) return false;

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof com.cells.items.ItemOverflowCard) return true;
        }

        return false;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        // Compacting cells require partitioning
        if (!hasPartition()) return input;

        // Check for partition changes and update compression chain if needed
        World world = getWorldFromSource(src);
        updateCompressionChainIfNeeded(world);

        int slot = canAcceptItem(input);

        // Item not acceptable - check overflow card for voiding
        if (slot < 0 || convRate[slot] <= 0) {
            if (hasOverflowCard()) return null;

            return input;
        }

        // Calculate how many items can fit
        // Storage is in base units; convert input to base units with overflow protection
        long inputCount = input.getStackSize();
        long inputInBaseUnits = multiplyWithOverflowProtection(inputCount, convRate[slot]);

        // Get remaining capacity directly in base units
        long remainingCapacityBaseUnits = getRemainingCapacityInBaseUnits();

        long canInsertBaseUnits = Math.min(inputInBaseUnits, remainingCapacityBaseUnits);

        // Convert back to input tier to get how many items we can accept
        long canInsert = canInsertBaseUnits / convRate[slot];

        // If cell is full, check for overflow card
        if (canInsert <= 0) {
            // Overflow card: accept all items but store nothing
            if (hasOverflowCard()) return null;

            return input;
        }

        // Recalculate actual base units to add (may be less due to rounding)
        long actualBaseUnits = canInsert * convRate[slot];

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            // Overflow protection for storage
            storedBaseUnits = addWithOverflowProtection(storedBaseUnits, actualBaseUnits);
            saveChanges();

            // Notify grid about all tier changes (not just the inserted item)
            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        // All items fit
        if (canInsert >= inputCount) return null;

        // Overflow card: void the remainder
        if (hasOverflowCard()) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(inputCount - canInsert);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        // Check for partition changes
        updateCompressionChainIfNeeded(getWorldFromSource(src));

        int slot = getSlotForItem(request);
        if (slot < 0) return null;
        if (convRate[slot] <= 0) return null;

        // Calculate how many of this tier we can extract from the pool
        // Each item of this tier costs convRate[slot] base units
        long requestedCount = request.getStackSize();
        long availableInThisTier = storedBaseUnits / convRate[slot];
        long toExtract = Math.min(requestedCount, availableInThisTier);

        if (toExtract <= 0) return null;

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            storedBaseUnits -= toExtract * convRate[slot];
            saveChanges();

            // Notify grid about all tier changes (not just the extracted item)
            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        if (storedBaseUnits <= 0) return out;

        // Report available items for each tier based on the shared pool
        // Each tier shows how many of that item could be fully extracted
        for (int i = 0; i < MAX_TIERS; i++) {
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            // Calculate how many of this tier can be extracted from the pool
            long availableCount = storedBaseUnits / convRate[i];
            if (availableCount <= 0) continue;

            IAEItemStack stack = channel.createStack(protoStack[i]);
            if (stack != null) {
                stack.setStackSize(availableCount);
                out.add(stack);
            }
        }

        return out;
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }

    @Override
    public ItemStack getItemStack() {
        return cellStack;
    }

    @Override
    public double getIdleDrain() {
        return cellType.getIdleDrain();
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return cellType.getFuzzyMode(cellStack);
    }

    @Override
    public IItemHandler getConfigInventory() {
        return cellType.getConfigInventory(cellStack);
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return cellType.getUpgradesInventory(cellStack);
    }

    @Override
    public int getBytesPerType() {
        // Cast is safe - bytesPerType is always within int range
        return (int) cellType.getBytesPerType(cellStack);
    }

    @Override
    public boolean canHoldNewItem() {
        // Compacting cells only hold one item type (with compression tiers)
        return isCompressionChainEmpty() && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        return cellType.getBytes(cellStack);
    }

    @Override
    public long getFreeBytes() {
        return getTotalBytes() - getUsedBytes();
    }

    @Override
    public long getTotalItemTypes() {
        // Compacting cells effectively have 1 type (but expose 3 tiers)
        return 1;
    }

    @Override
    public long getStoredItemCount() {
        // Return count in terms of the main tier (partitioned item)
        return getStoredInMainTier();
    }

    @Override
    public long getStoredItemTypes() {
        return isCompressionChainEmpty() ? 0 : 1;
    }

    @Override
    public long getRemainingItemTypes() {
        return isCompressionChainEmpty() ? 1 : 0;
    }

    @Override
    public long getUsedBytes() {
        int itemsPerByte = channel.getUnitsPerByte();

        // Use ceiling division for main tier count - 8 nuggets counts as 1 ingot for byte purposes
        long storedItemsCeiling = getStoredInMainTierCeiling();

        // Add type overhead
        long typeBytes = isCompressionChainEmpty() ? 0 : getBytesPerType();

        // Use ceiling division for bytes as well
        return typeBytes + (storedItemsCeiling + itemsPerByte - 1) / itemsPerByte;
    }

    @Override
    public long getRemainingItemCount() {
        // Calculate remaining based on base units for accuracy
        long remainingBaseUnits = getRemainingCapacityInBaseUnits();
        if (mainTier < 0 || convRate[mainTier] <= 0) return 0;

        // Convert to main tier items (floor - actual insertable count)
        return remainingBaseUnits / convRate[mainTier];
    }

    @Override
    public int getUnusedItemCount() {
        // This represents the fractional items that don't fill a full byte
        // With ceiling-based byte calculation, this is handled differently
        int itemsPerByte = channel.getUnitsPerByte();
        long storedCeiling = getStoredInMainTierCeiling();
        long usedForItems = (storedCeiling + itemsPerByte - 1) / itemsPerByte * itemsPerByte;

        return (int) (usedForItems - storedCeiling);
    }

    @Override
    public int getStatusForCell() {
        if (getUsedBytes() == 0) return 4; // Empty
        if (canHoldNewItem()) return 1;    // Has space for new types
        if (getRemainingItemCount() > 0) return 2; // Has space for more of existing

        return 3; // Full
    }

    @Override
    public void persist() {
        saveToNBT();
    }

    private static boolean areItemsEqual(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;

        return a.getItem() == b.getItem() && a.getMetadata() == b.getMetadata()
            && ItemStack.areItemStackTagsEqual(a, b);
    }
}
