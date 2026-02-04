package com.cells.cells.hyperdensity.compacting;

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
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import com.cells.cells.compacting.CompactingHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CellMathHelper;


/**
 * Inventory implementation for Hyper-Density Compacting Storage Cells.
 * 
 * <h2>Overview</h2>
 * This class combines two powerful storage mechanics:
 * <ul>
 *   <li><b>Hyper-Density (HD)</b>: Multiplies storage capacity by ~2.1 billion per displayed byte</li>
 *   <li><b>Compacting</b>: Exposes compressed/decompressed item forms to the ME network</li>
 * </ul>
 * 
 * <h2>Hyper-Density Behavior</h2>
 * <p>
 * Standard AE2 cells use int bytes count for storage calculations. HD cells multiply each displayed
 * byte by {@code Integer.MAX_VALUE} (~2.1 billion), effectively allowing massive storage
 * in cells that display familiar capacity values (1k, 4k, etc.).
 * </p>
 * <p>
 * For example, a "1k HD Compacting Cell" displays 1024 bytes but actually stores
 * 1024 * 2,147,483,647 = ~2.2 trillion base units of items.
 * </p>
 * 
 * <h2>Compacting Behavior</h2>
 * <p>
 * Compacting cells store items in a unified pool measured in "base units" (the smallest
 * compressible form). When you insert any tier of a compressible item, it's converted
 * to base units. When extracting, you can request any tier and it will be converted back.
 * </p>
 * 
 * <h3>Compression Chain Structure</h3>
 * <p>
 * Each compacting cell supports up to 3 tiers in a compression chain:
 * </p>
 * <ul>
 *   <li><b>Tier 0 (Highest)</b>: Most compressed form (e.g., Iron Block)</li>
 *   <li><b>Tier 1 (Middle)</b>: The partitioned item (e.g., Iron Ingot)</li>
 *   <li><b>Tier 2 (Lowest)</b>: Least compressed form (e.g., Iron Nugget) - base unit rate = 1</li>
 * </ul>
 * 
 * <h3>Conversion Rates</h3>
 * <p>
 * Each tier has a conversion rate relative to base units:
 * </p>
 * <ul>
 *   <li>Iron Nugget (lowest tier): convRate = 1 (base unit)</li>
 *   <li>Iron Ingot (middle tier): convRate = 9 (9 nuggets = 1 ingot)</li>
 *   <li>Iron Block (highest tier): convRate = 81 (81 nuggets = 1 block)</li>
 * </ul>
 * <p>
 * Inserting 1 Iron Block adds 81 base units. Extracting 9 Iron Ingots removes 81 base units.
 * </p>
 * 
 * <h3>Partition Requirement</h3>
 * <p>
 * Compacting cells <b>require</b> a partition to be set before accepting items.
 * The partition determines which compression chain is used. Once items are stored,
 * the partition cannot be changed (it will auto-revert to preserve data).
 * </p>
 * 
 * <h2>Overflow Protection</h2>
 * <p>
 * Due to the combination of HD multiplier (~2.1B) and compression rates (up to 81),
 * calculations can easily overflow 64-bit longs. All arithmetic uses overflow-protected
 * methods that return {@code Long.MAX_VALUE} on overflow instead of wrapping.
 * </p>
 * <p>
 * Because of these overflow concerns, HD Compacting Cells are limited to 16M tier maximum
 * (vs 1G for regular HD cells), providing adequate headroom for safe calculations.
 * </p>
 * 
 * <h2>Upgrade Support</h2>
 * <ul>
 *   <li><b>Overflow Card</b>: When installed, excess items are voided instead of rejected</li>
 * </ul>
 *
 * TODO: Add thorough overflow protection for compression/decompression card support
 * 
 * @see IItemHyperDensityCompactingCell
 * @see CompactingHelper
 */
public class HyperDensityCompactingCellInventory implements ICellInventory<IAEItemStack> {

    /** NBT key prefix for stored base units (saved as _hi and _lo integer pairs for long support). */
    private static final String NBT_STORED_BASE_UNITS = "storedBaseUnits";

    /** NBT key for the conversion rates array (int[] of rates for each tier). */
    private static final String NBT_CONV_RATES = "convRates";

    /** NBT key for the prototype items compound (contains item0, item1, item2 sub-tags). */
    private static final String NBT_PROTO_ITEMS = "protoItems";

    /** NBT key for the main tier index (which tier matches the partitioned item). */
    private static final String NBT_MAIN_TIER = "mainTier";

    /** NBT key for the cached partition item (used to detect partition changes). */
    private static final String NBT_CACHED_PARTITION = "cachedPartition";

    /** NBT key for cached tiers up value. */
    private static final String NBT_TIERS_UP = "tiersUp";

    /** NBT key for cached tiers down value. */
    private static final String NBT_TIERS_DOWN = "tiersDown";

    /** Default tiers when no tier card is installed. */
    private static final int DEFAULT_TIERS_UP = 1;
    private static final int DEFAULT_TIERS_DOWN = 1;

    /**
     * Cached copy of the partition item from the last chain initialization.
     * Used to detect when the partition has been changed in the cell workbench.
     * If the cell contains items and partition changes, we revert to this cached value.
     */
    private ItemStack cachedPartitionItem = ItemStack.EMPTY;

    /** The ItemStack representing this storage cell. */
    private final ItemStack cellStack;

    /** Provider for saving cell changes (typically the ME Drive tile entity). */
    private final ISaveProvider container;

    /** The AE2 storage channel (items in this case). */
    private final IStorageChannel<IAEItemStack> channel;

    /** Type interface providing cell-specific values (bytes, idle drain, etc.). */
    private final IItemHyperDensityCompactingCell cellType;

    /** Direct reference to the cell's NBT data for persistence. */
    private final NBTTagCompound tagCompound;

    /**
     * Prototype ItemStacks for each tier in the compression chain.
     * <ul>
     *   <li>protoStack[0]: Highest tier (most compressed, e.g., Iron Block)</li>
     *   <li>protoStack[1]: Middle tier (e.g., Iron Ingot)</li>
     *   <li>protoStack[2]: Lowest tier (least compressed, e.g., Iron Nugget)</li>
     * </ul>
     * Empty stacks indicate that tier is not available in the chain.
     */
    private ItemStack[] protoStack;

    /**
     * Conversion rates for each tier, relative to base units.
     * <p>
     * The lowest tier (tier 2) typically has rate = 1 (the base unit).
     * Higher tiers have larger rates representing how many base units they equal.
     * </p>
     * <p>
     * Example for iron: nugget=1, ingot=9, block=81
     * </p>
     */
    private int[] convRate;

    /** Current maximum number of tiers based on tier card configuration. */
    private int currentMaxTiers;

    /** Cached number of tiers up (toward compressed forms) from upgrade cards. */
    private int cachedTiersUp;

    /** Cached number of tiers down (toward decompressed forms) from upgrade cards. */
    private int cachedTiersDown;

    /**
     * The total number of stored items measured in base units (lowest tier).
     * <p>
     * This is the single source of truth for storage. All tier counts are derived
     * by dividing this by the appropriate conversion rate.
     * </p>
     * <p>
     * For example, if storedBaseUnits = 100 and iron ingot convRate = 9,
     * then we have 100/9 = 11 iron ingots available (with 1 base unit remainder).
     * </p>
     */
    private long storedBaseUnits = 0;

    /**
     * Index of the "main" tier - the tier matching the partitioned item.
     * <p>
     * Storage capacity and display are calculated based on this tier.
     * Valid values: 0, 1, or 2. A value of -1 indicates no chain is initialized.
     * </p>
     */
    private int mainTier = -1;

    /** Cached value for whether the Overflow Card is installed. */
    private boolean cachedHasOverflowCard = false;

    /**
     * Creates a new inventory wrapper for an HD Compacting Cell.
     * <p>
     * The constructor loads existing data from NBT and initializes the compression
     * chain arrays. It does NOT automatically initialize the chain - that happens
     * lazily when items are inserted or when {@link #initializeChainFromPartition}
     * is called explicitly.
     * </p>
     *
     * @param cellType  The cell type interface providing capacity and config
     * @param cellStack The ItemStack representing this cell
     * @param container Save provider for persisting changes (may be null for read-only access)
     */
    public HyperDensityCompactingCellInventory(IItemHyperDensityCompactingCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

        this.tagCompound = Platform.openNbtData(cellStack);

        // Initialize with default tier configuration
        this.cachedTiersUp = DEFAULT_TIERS_UP;
        this.cachedTiersDown = DEFAULT_TIERS_DOWN;
        this.currentMaxTiers = cachedTiersUp + 1 + cachedTiersDown;
        initializeArrays();

        // Load any existing data from the cell's NBT
        loadFromNBT();
        updateCachedUpgradeState();
    }

    /**
     * Initialize or reinitialize arrays based on current tier configuration.
     */
    private void initializeArrays() {
        protoStack = new ItemStack[currentMaxTiers];
        convRate = new int[currentMaxTiers];

        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }
    }

    /**
     * Update cached upgrade card state.
     */
    private void updateCachedUpgradeState() {
        IItemHandler upgrades = getUpgradesInventory();
        cachedHasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);

        int compressionTiers = CellUpgradeHelper.getCompressionTiers(upgrades);
        int decompressionTiers = CellUpgradeHelper.getDecompressionTiers(upgrades);

        if (compressionTiers > 0) {
            cachedTiersUp = compressionTiers;
            cachedTiersDown = 0;
        } else if (decompressionTiers > 0) {
            cachedTiersUp = 0;
            cachedTiersDown = decompressionTiers;
        } else {
            cachedTiersUp = DEFAULT_TIERS_UP;
            cachedTiersDown = DEFAULT_TIERS_DOWN;
        }
    }

    /**
     * Check if the tier card configuration has changed since the chain was built.
     * This detects when compression/decompression tier cards are added or removed.
     *
     * @return true if the tier configuration has changed
     */
    private boolean hasTierConfigChanged() {
        IItemHandler upgrades = getUpgradesInventory();
        int compressionTiers = CellUpgradeHelper.getCompressionTiers(upgrades);
        int decompressionTiers = CellUpgradeHelper.getDecompressionTiers(upgrades);

        int newTiersUp, newTiersDown;
        if (compressionTiers > 0) {
            newTiersUp = compressionTiers;
            newTiersDown = 0;
        } else if (decompressionTiers > 0) {
            newTiersUp = 0;
            newTiersDown = decompressionTiers;
        } else {
            newTiersUp = DEFAULT_TIERS_UP;
            newTiersDown = DEFAULT_TIERS_DOWN;
        }

        return newTiersUp != cachedTiersUp || newTiersDown != cachedTiersDown;
    }

    /**
     * Get the current number of tiers up (toward compressed forms).
     */
    public int getTiersUp() {
        return cachedTiersUp;
    }

    /**
     * Get the current number of tiers down (toward decompressed forms).
     */
    public int getTiersDown() {
        return cachedTiersDown;
    }

    /**
     * Notifies the ME network that item counts have changed across all compression tiers.
     * <p>
     * When items are inserted or extracted at one tier, the available counts at ALL tiers
     * change (since they share a common base unit pool). This method posts those changes
     * to the storage grid so autocrafting and terminals update correctly.
     * </p>
     * <p>
     * The tier that was directly operated on is skipped (operatedSlot) because AE2's
     * standard injection/extraction already handles that notification.
     * </p>
     *
     * @param src          The action source for the operation
     * @param oldBaseUnits The base unit count before the operation
     * @param operatedSlot The tier that was directly operated on (-1 to notify all)
     */
    private void notifyGridOfAllTierChanges(@Nullable IActionSource src, long oldBaseUnits, int operatedSlot) {
        IGrid grid = CellMathHelper.getGridFromSource(src);
        if (grid == null) return;

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return;

        List<IAEItemStack> changes = new ArrayList<>();

        for (int i = 0; i < currentMaxTiers; i++) {
            if (i == operatedSlot) continue;
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

        if (!changes.isEmpty()) storageGrid.postAlterationOfStoredItems(channel, changes, src);
    }

    /**
     * Loads all cell state from NBT data.
     * <p>
     * Called during construction to restore the cell's previous state.
     * Loads stored base units, compression chain, main tier, and cached partition.
     * </p>
     */
    private void loadFromNBT() {
        // Load stored base units
        storedBaseUnits = CellMathHelper.loadLong(tagCompound, NBT_STORED_BASE_UNITS);

        // Load conversion rates array
        if (tagCompound.hasKey(NBT_CONV_RATES)) {
            int[] rates = tagCompound.getIntArray(NBT_CONV_RATES);
            for (int i = 0; i < Math.min(rates.length, currentMaxTiers); i++) convRate[i] = rates[i];
        }

        // Load prototype items for each tier
        if (tagCompound.hasKey(NBT_PROTO_ITEMS)) {
            NBTTagCompound protoNbt = tagCompound.getCompoundTag(NBT_PROTO_ITEMS);
            for (int i = 0; i < currentMaxTiers; i++) {
                if (protoNbt.hasKey("item" + i)) {
                    protoStack[i] = new ItemStack(protoNbt.getCompoundTag("item" + i));
                }
            }
        }

        // Load main tier index
        if (tagCompound.hasKey(NBT_MAIN_TIER)) mainTier = tagCompound.getInteger(NBT_MAIN_TIER);

        // Load cached partition item
        if (tagCompound.hasKey(NBT_CACHED_PARTITION)) {
            cachedPartitionItem = new ItemStack(tagCompound.getCompoundTag(NBT_CACHED_PARTITION));
        }

        // Recalculate mainTier if it's invalid but we have chain data
        // This handles cases where old NBT had mainTier saved incorrectly
        if (mainTier < 0 && !isCompressionChainEmpty() && !cachedPartitionItem.isEmpty()) {
            recalculateMainTierFromCachedPartition();
        }
    }

    /**
     * Recalculate mainTier from the cached partition item.
     * Used when loading NBT with invalid mainTier but valid chain data.
     */
    private void recalculateMainTierFromCachedPartition() {
        if (cachedPartitionItem.isEmpty()) return;

        mainTier = 0;
        for (int i = 0; i < currentMaxTiers; i++) {
            if (CellMathHelper.areItemsEqual(protoStack[i], cachedPartitionItem)) {
                mainTier = i;
                break;
            }
        }
    }

    /**
     * Saves all cell state to NBT data.
     * <p>
     * Stores the base units, main tier, compression chain, and cached partition.
     * If the cell is empty and has no chain, cleans up unused NBT keys.
     * </p>
     */
    private void saveToNBT() {
        CellMathHelper.saveLong(tagCompound, NBT_STORED_BASE_UNITS, storedBaseUnits);

        // Only save chain data (including mainTier) if we actually have a chain initialized.
        // This prevents an old handler with empty chain from overwriting
        // valid chain data that was written by another handler instance.
        if (!isCompressionChainEmpty()) {
            tagCompound.setInteger(NBT_MAIN_TIER, mainTier);
            tagCompound.setIntArray(NBT_CONV_RATES, convRate);

            NBTTagCompound protoNbt = new NBTTagCompound();
            for (int i = 0; i < currentMaxTiers; i++) {
                if (!protoStack[i].isEmpty()) {
                    NBTTagCompound itemNbt = new NBTTagCompound();
                    protoStack[i].writeToNBT(itemNbt);
                    protoNbt.setTag("item" + i, itemNbt);
                }
            }
            tagCompound.setTag(NBT_PROTO_ITEMS, protoNbt);

            // Save cached partition for reversion protection
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

    /**
     * Saves cell state and notifies the container (ME Drive) of changes.
     */
    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    /**
     * Initializes the compression chain based on an input item.
     * <p>
     * Uses {@link CompactingHelper} to discover the compression relationships
     * for the given item. Populates protoStack and convRate arrays.
     * </p>
     * <p>
     * If no world is available (e.g., during NBT-only access), creates a
     * single-tier chain with just the input item.
     * </p>
     *
     * @param inputItem The item to build the compression chain around
     * @param world     The world for recipe lookups (may be null)
     */
    private void initializeCompressionChain(@Nonnull ItemStack inputItem, @Nullable World world) {
        // Update tier configuration from upgrade cards
        updateCachedUpgradeState();
        int newMaxTiers = cachedTiersUp + 1 + cachedTiersDown;
        if (newMaxTiers != currentMaxTiers) {
            currentMaxTiers = newMaxTiers;
            initializeArrays();
        }

        // Fallback: no world means no recipe access, create single-tier chain
        if (world == null) {
            protoStack[0] = inputItem.copy();
            protoStack[0].setCount(1);
            convRate[0] = 1;
            mainTier = 0;

            return;
        }

        CompactingHelper helper = new CompactingHelper(world);
        CompactingHelper.CompressionChain chain = helper.getCompressionChain(inputItem, cachedTiersUp, cachedTiersDown);

        // Resize arrays if chain has different tier count
        int chainTiers = chain.getMaxTiers();
        if (chainTiers != currentMaxTiers) {
            currentMaxTiers = chainTiers;
            initializeArrays();
        }

        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = chain.getStack(i);
            convRate[i] = chain.getRate(i);
        }

        // Use the main tier index from the chain
        mainTier = chain.getMainTierIndex();
    }

    /**
     * Finds which tier slot an item belongs to in the compression chain.
     *
     * @param stack The item to find
     * @return The tier index (0-2), or -1 if not in the chain
     */
    private int getSlotForItem(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < currentMaxTiers; i++) {
            if (CellMathHelper.areItemsEqual(protoStack[i], definition)) return i;
        }

        return -1;
    }

    /**
     * Checks if an item can be accepted by this cell and returns its tier slot.
     * <p>
     * Validates: partition exists, item is allowed, chain is initialized or can be.
     * </p>
     *
     * @param stack The item to check
     * @return The tier slot (0-2), 0 if chain needs init, or -1 if rejected
     */
    private int canAcceptItem(@Nonnull IAEItemStack stack) {
        if (!hasPartition()) return -1;

        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        if (!isAllowedByPartition(stack)) return -1;
        if (isCompressionChainEmpty()) return 0;

        // FIXME: 0 for uninitialized chain is ambiguous with actual slot 0

        return getSlotForItem(stack);
    }

    /**
     * Checks if no compression chain has been established yet.
     *
     * @return true if all protoStack slots are empty
     */
    private boolean isCompressionChainEmpty() {
        for (int i = 0; i < currentMaxTiers; i++) {
            if (!protoStack[i].isEmpty()) return false;
        }

        return true;
    }

    /**
     * Public accessor to check if the compression chain is initialized.
     *
     * @return true if the chain has at least one tier defined
     */
    public boolean isChainInitialized() {
        return !isCompressionChainEmpty();
    }

    /**
     * Checks if this cell contains any stored items.
     *
     * @return true if storedBaseUnits > 0
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
     * Reload chain data from NBT if it has been updated externally,
     * or recompute the chain if tier card configuration has changed.
     * <p>
     * Call this before operations that depend on the compression chain.
     * </p>
     * <p>
     * Handles the following cases:
     * <ul>
     *   <li>NBT has chain data that local cache doesn't have</li>
     *   <li>Tier card was added or removed (chain needs recomputing)</li>
     *   <li>Partition exists but chain is empty (needs initialization)</li>
     * </ul>
     * </p>
     */
    private void reloadFromNBTIfNeeded() {
        // Check if NBT has data we haven't loaded
        if (hasUnloadedNBTChainData()) {
            loadFromNBT();
        }

        // Check if tier card configuration has changed
        if (!hasTierConfigChanged()) return;

        // Tier config changed - need to recompute chain
        // This is safe even if cell has items, as we preserve the base unit count
        if (!cachedPartitionItem.isEmpty()) {
            // Recompute chain with new tier configuration
            // Note: We don't have a World here, so we need to defer this
            // The chain will be recomputed when updateCompressionChainIfNeeded is called
            // For now, just update the cached tier config
            updateCachedUpgradeState();

            // Resize arrays to new tier count
            int newMaxTiers = cachedTiersUp + 1 + cachedTiersDown;
            if (newMaxTiers != currentMaxTiers) {
                // Save current data
                long savedBaseUnits = storedBaseUnits;
                ItemStack savedPartition = cachedPartitionItem.copy();

                // Reset and resize
                currentMaxTiers = newMaxTiers;
                initializeArrays();
                storedBaseUnits = savedBaseUnits;
                cachedPartitionItem = savedPartition;

                // Chain will be rebuilt on next updateCompressionChainIfNeeded call with world access
                mainTier = -1;
            }
        }
    }

    /**
     * Checks if an item is part of the current compression chain.
     *
     * @param stack The item to check
     * @return true if the item matches any tier in the chain
     */
    public boolean isInCompressionChain(@Nonnull IAEItemStack stack) {
        if (stack == null) return false;

        ItemStack definition = stack.getDefinition();

        for (int i = 0; i < currentMaxTiers; i++) {
            if (CellMathHelper.areItemsEqual(protoStack[i], definition)) return true;
        }

        return false;
    }

    /**
     * Checks if this cell has a partition configured.
     * <p>
     * Compacting cells REQUIRE a partition to accept items.
     * </p>
     *
     * @return true if at least one config slot contains an item
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
     * Checks if the partition has changed since the chain was initialized.
     * <p>
     * Used to detect when a user modifies the partition in the Cell Workbench.
     * </p>
     *
     * @return true if the current partition differs from the cached one
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

        return !CellMathHelper.areItemsEqual(currentPartition, cachedPartitionItem);
    }

    /**
     * Initializes the compression chain from the current partition setting.
     * <p>
     * Called via APIs when partition is set, to prepare the chain
     * immediately without requiring item insertion first.
     * </p>
     *
     * @param world The world for recipe lookups
     */
    public void initializeChainFromPartition(@Nullable World world) {
        updateCompressionChainIfNeeded(world);
    }

    /**
     * Force-initializes the compression chain for a specific item.
     * <p>
     * Bypasses the config inventory read to avoid timing issues where
     * NBT hasn't been flushed yet. Only works if the cell is empty.
     * </p>
     *
     * @param partitionItem The item to build the chain around
     * @param world         The world for recipe lookups
     */
    public void initializeChainForItem(@Nonnull ItemStack partitionItem, @Nullable World world) {
        if (partitionItem.isEmpty()) return;
        if (storedBaseUnits > 0) return;

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
        for (int i = 1; i < modifiable.getSlots(); i++) modifiable.setStackInSlot(i, ItemStack.EMPTY);
    }

    /**
     * Updates the compression chain if needed (partition changed or needs init).
     * <p>
     * If the cell contains items and the partition was changed, the partition
     * is reverted to prevent data loss. This ensures the compression chain
     * always matches the stored items.
     * </p>
     *
     * @param world The world for recipe lookups
     */
    private void updateCompressionChainIfNeeded(@Nullable World world) {
        // First, check if NBT has chain data written by another handler (e.g., API call)
        // that we haven't loaded yet. Also checks for tier card changes.
        reloadFromNBTIfNeeded();

        // Check if tier card config changed and we need to rebuild chain
        boolean needsTierRebuild = hasTierConfigChanged();

        // Check if chain needs to be built (partition exists but chain is empty)
        boolean needsInitialization = hasPartition() && isCompressionChainEmpty();

        // Handle tier card change - rebuild chain even with items
        if (needsTierRebuild && !cachedPartitionItem.isEmpty()) {
            // Save the base units
            long savedBaseUnits = storedBaseUnits;

            // Rebuild chain with new tier configuration
            reset();
            storedBaseUnits = savedBaseUnits;
            initializeCompressionChain(cachedPartitionItem, world);
            saveChanges();

            return;
        }

        // Handle partition-based initialization
        if (!needsInitialization && !hasPartitionChanged()) return;

        // If partition changed but we have stored items, revert to cached partition
        // This prevents data loss due to changing partition while items are stored
        if (storedBaseUnits > 0) {
            revertPartitionToCached();

            return;
        }

        ItemStack currentPartition = getFirstPartitionedItem();
        if (currentPartition == null || currentPartition.isEmpty()) {
            reset();
            cachedPartitionItem = ItemStack.EMPTY;
            saveChanges();

            return;
        }

        reset();
        initializeCompressionChain(currentPartition, world);
        cachedPartitionItem = currentPartition.copy();
        cachedPartitionItem.setCount(1);
        saveChanges();
    }

    /**
     * Reverts the partition config back to the cached partition.
     * <p>
     * Called when a user tries to change partition while items are stored.
     * This protects against data loss by keeping the partition consistent
     * with the stored compression chain.
     * </p>
     */
    private void revertPartitionToCached() {
        if (cachedPartitionItem.isEmpty()) return;

        IItemHandler configInv = getConfigInventory();
        if (!(configInv instanceof IItemHandlerModifiable)) return;

        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) configInv;

        // Restore the first slot to the cached partition, clear others
        modifiable.setStackInSlot(0, cachedPartitionItem.copy());
        for (int i = 1; i < modifiable.getSlots(); i++) modifiable.setStackInSlot(i, ItemStack.EMPTY);
    }

    /**
     * Checks if an item is allowed by the current partition/compression chain.
     * <p>
     * An item is allowed if it matches any tier in the compression chain OR
     * if it matches the partition config directly.
     * </p>
     *
     * @param stack The item to check
     * @return true if the item can be stored in this cell
     */
    private boolean isAllowedByPartition(@Nonnull IAEItemStack stack) {
        IItemHandler configInv = getConfigInventory();
        if (configInv == null) return false;

        ItemStack definition = stack.getDefinition();

        // Check against compression chain first
        for (int i = 0; i < currentMaxTiers; i++) {
            if (CellMathHelper.areItemsEqual(protoStack[i], definition)) return true;
        }

        // Fall back to direct partition match
        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty() && CellMathHelper.areItemsEqual(partItem, definition)) return true;
        }

        return false;
    }

    /**
     * Gets the first item from the partition config.
     *
     * @return A copy of the first non-empty config slot, or null if empty
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
     * Gets the count of stored items in main tier units.
     * <p>
     * Uses a floor division to truncate remainders.
     * </p>
     *
     * @return Number of main-tier items that can be extracted
     */
    private long getStoredInMainTier() {
        if (mainTier < 0 || mainTier >= currentMaxTiers) return 0;
        if (convRate[mainTier] <= 0) return 0;

        return storedBaseUnits / convRate[mainTier];
    }

    /**
     * Gets the count of stored items in main tier units (ceiling).
     * <p>
     * Rounds up, so that byte calculations where partial items
     * still consumes a full item's worth of storage.
     * </p>
     *
     * @return Number of main-tier items (rounded up)
     */
    private long getStoredInMainTierCeiling() {
        if (mainTier < 0 || mainTier >= currentMaxTiers) return 0;
        if (convRate[mainTier] <= 0) return 0;

        return (storedBaseUnits + convRate[mainTier] - 1) / convRate[mainTier];
    }

    /**
     * Calculates the maximum storage capacity in base units.
     * <p>
     * This is the core HD calculation:
     * <ol>
     *   <li>Get total bytes (display * HD multiplier)</li>
     *   <li>Subtract bytes-per-type overhead</li>
     *   <li>Convert to main-tier items using items-per-byte</li>
     *   <li>Convert to base units using main tier's conversion rate</li>
     * </ol>
     * All steps use overflow protection.
     * </p>
     * <p>
     * Uses conservative calculation to ensure usedBytes never exceeds totalBytes.
     * The conversion chain between bytes <-> items <-> base units uses ceiling
     * divisions for display, so we must be slightly conservative here.
     * </p>
     *
     * @return Maximum base units, or Long.MAX_VALUE if overflow
     */
    private long getMaxCapacityInBaseUnits() {
        // Get actual total bytes (display * multiplier) - already multiplied in cellType
        long totalBytes = cellType.getBytes(cellStack);
        long typeBytes = isCompressionChainEmpty() ? 0 : cellType.getBytesPerType(cellStack);
        long availableBytes = totalBytes - typeBytes;

        if (availableBytes <= 0) return 0;
        if (mainTier < 0 || mainTier >= currentMaxTiers || convRate[mainTier] <= 0) return 0;

        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate max main tier items - overflow protected
        long maxMainTierItems = CellMathHelper.multiplyWithOverflowProtection(availableBytes, itemsPerByte);
        if (maxMainTierItems == Long.MAX_VALUE) return Long.MAX_VALUE;

        // Convert to base units - overflow protected
        long maxBaseUnits = CellMathHelper.multiplyWithOverflowProtection(maxMainTierItems, convRate[mainTier]);
        if (maxBaseUnits == Long.MAX_VALUE) return Long.MAX_VALUE;

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
     * Calculates remaining storage capacity in base units.
     *
     * @return Remaining base units that can be stored
     */
    private long getRemainingCapacityInBaseUnits() {
        long max = getMaxCapacityInBaseUnits();
        if (max == Long.MAX_VALUE) return Long.MAX_VALUE;

        return Math.max(0, max - storedBaseUnits);
    }

    /**
     * Gets the partitioned (main tier) item for display.
     *
     * @return The main tier item, or first partition config item, or empty
     */
    @Nonnull
    public ItemStack getPartitionedItem() {
        if (mainTier >= 0 && mainTier < currentMaxTiers && !protoStack[mainTier].isEmpty()) {
            return protoStack[mainTier];
        }

        ItemStack firstPart = getFirstPartitionedItem();

        return firstPart != null ? firstPart : ItemStack.EMPTY;
    }

    /**
     * Gets the higher (more compressed) tier item for tooltip display.
     *
     * @return The compressed form, or empty if none exists
     */
    @Nonnull
    public ItemStack getHigherTierItem() {
        if (mainTier <= 0 || mainTier >= currentMaxTiers) return ItemStack.EMPTY;

        for (int i = mainTier - 1; i >= 0; i--) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    /**
     * Gets the lower (less compressed) tier item for tooltip display.
     *
     * @return The decompressed form, or empty if none exists
     */
    @Nonnull
    public ItemStack getLowerTierItem() {
        if (mainTier < 0 || mainTier >= currentMaxTiers) return ItemStack.EMPTY;

        for (int i = mainTier + 1; i < currentMaxTiers; i++) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    /**
     * Resets all cell state to empty/uninitialized.
     */
    private void reset() {
        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }

        storedBaseUnits = 0;
        mainTier = -1;
    }

    /**
     * Checks if the cell has an Overflow Card installed.
     * <p>
     * When installed, items that exceed capacity are voided instead of
     * being rejected back to the network.
     * </p>
     *
     * @return true if an Overflow Card upgrade is installed
     */
    private boolean hasOverflowCard() {
        return cachedHasOverflowCard;
    }

    /**
     * Injects items into the cell.
     * <p>
     * Converts the input to base units using the appropriate tier's conversion
     * rate, then adds to the storage pool. Notifies the grid of changes to
     * all tiers since they share the same pool.
     * </p>
     * <p>
     * If the Overflow Card is installed, excess items are voided silently.
     * </p>
     *
     * @param input The items to inject
     * @param mode  SIMULATE or MODULATE
     * @param src   The action source
     * @return Remainder that couldn't fit, or null if all accepted
     */
    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        if (!hasPartition()) return input;

        World world = CellMathHelper.getWorldFromSource(src);
        updateCompressionChainIfNeeded(world);

        int slot = canAcceptItem(input);

        // Item not in compression chain - reject
        if (slot < 0 || convRate[slot] <= 0) return input;

        // Overflow card voids items that are in the compression chain
        boolean canVoidOverflow = hasOverflowCard();

        // Convert input count to base units
        long inputCount = input.getStackSize();
        long inputInBaseUnits = CellMathHelper.multiplyWithOverflowProtection(inputCount, convRate[slot]);
        long remainingCapacityBaseUnits = getRemainingCapacityInBaseUnits();
        long canInsertBaseUnits = Math.min(inputInBaseUnits, remainingCapacityBaseUnits);
        long canInsert = canInsertBaseUnits / convRate[slot];

        if (canInsert <= 0) {
            if (canVoidOverflow) return null;

            return input;
        }

        long actualBaseUnits = canInsert * convRate[slot];

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            storedBaseUnits = CellMathHelper.addWithOverflowProtection(storedBaseUnits, actualBaseUnits);
            saveChanges();

            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        if (canInsert >= inputCount) return null;
        if (canVoidOverflow) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(inputCount - canInsert);

        return remainder;
    }

    /**
     * Extracts items from the cell.
     * <p>
     * Items can be extracted at any tier in the compression chain. The base
     * units are converted to the requested tier's count. Notifies the grid
     * of changes to all tiers since they share the same pool.
     * </p>
     *
     * @param request The items to extract (type and max count)
     * @param mode    SIMULATE or MODULATE
     * @param src     The action source
     * @return The extracted items, or null if none available
     */
    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        updateCompressionChainIfNeeded(CellMathHelper.getWorldFromSource(src));

        int slot = getSlotForItem(request);
        if (slot < 0) return null;
        if (convRate[slot] <= 0) return null;

        // Calculate available count at this tier
        long requestedCount = request.getStackSize();
        long availableInThisTier = storedBaseUnits / convRate[slot];
        long toExtract = Math.min(requestedCount, availableInThisTier);

        if (toExtract <= 0) return null;

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;

            storedBaseUnits -= toExtract * convRate[slot];
            saveChanges();

            notifyGridOfAllTierChanges(src, oldBaseUnits, slot);
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    /**
     * Returns all available items in the cell across all compression tiers.
     * <p>
     * This is what the ME network uses to display available items and for
     * autocrafting calculations. Each tier shows its count based on the
     * shared base unit pool.
     * </p>
     *
     * @param out The list to add items to
     * @return The same list with items added
     */
    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        // Check if NBT has chain data written by another handler (e.g., API call)
        reloadFromNBTIfNeeded();

        if (storedBaseUnits <= 0) return out;

        // Add each tier's available count
        for (int i = 0; i < currentMaxTiers; i++) {
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

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
        // Return display bytes per type for UI (actual would overflow)
        int meta = cellStack.getMetadata();
        long[] displayBpt = {8L, 32L, 128L, 512L, 2048L, 8192L, 32768L, 131072L};
        long val = (meta >= 0 && meta < displayBpt.length) ? displayBpt[meta] : displayBpt[0];

        return (int) Math.min(val, Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return isCompressionChainEmpty() && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        // Return display bytes for UI
        return cellType.getDisplayBytes(cellStack);
    }

    @Override
    public long getFreeBytes() {
        long total = getTotalBytes();
        long used = getUsedBytes();

        // Safety: ensure we never return negative free bytes
        return Math.max(0, total - used);
    }

    @Override
    public long getTotalItemTypes() {
        return 1;
    }

    @Override
    public long getStoredItemCount() {
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
        long multiplier = cellType.getByteMultiplier();

        long storedItemsCeiling = getStoredInMainTierCeiling();

        // Items per display byte = itemsPerByte * multiplier
        long itemsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) itemsPerDisplayByte = 1;

        // usedDisplayBytes = storedItemsCeiling / itemsPerDisplayByte (ceiling)
        long usedForItems = (storedItemsCeiling + itemsPerDisplayByte - 1) / itemsPerDisplayByte;
        long typeBytes = isCompressionChainEmpty() ? 0 : getBytesPerType();

        return typeBytes + usedForItems;
    }

    @Override
    public long getRemainingItemCount() {
        long remainingBaseUnits = getRemainingCapacityInBaseUnits();
        if (mainTier < 0 || convRate[mainTier] <= 0) return 0;

        return remainingBaseUnits / convRate[mainTier];
    }

    @Override
    public int getUnusedItemCount() {
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long storedCeiling = getStoredInMainTierCeiling();

        long itemsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) return 0;

        long usedBytes = getUsedBytes() - (isCompressionChainEmpty() ? 0 : getBytesPerType());
        if (usedBytes <= 0) return 0;

        // Safety: ensure we don't return negative values
        long fullItems = CellMathHelper.multiplyWithOverflowProtection(usedBytes, itemsPerDisplayByte);
        long unused = fullItems - storedCeiling;
        if (unused < 0) unused = 0;

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (getUsedBytes() == 0) return 4;
        if (canHoldNewItem()) return 1;
        if (getRemainingItemCount() > 0) return 2;

        return 3;
    }

    @Override
    public void persist() {
        saveToNBT();
    }
}
