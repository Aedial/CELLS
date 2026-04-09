package com.cells.cells.normal.compacting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import com.cells.cells.compacting.CompactingHelper;
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.ItemStackKey;
import com.cells.util.DeferredCellOperations;
import com.cells.util.OreDictValidator;


/**
 * Inventory implementation for compacting storage cells.
 * <p>
 * This cell REQUIRES partitioning before it can accept items. The partitioned item
 * defines the compression chain (e.g., iron ingot -> iron block / iron nugget).
 * <p>
 * Storage is exposed as compression tiers based on installed upgrade cards:
 * - Default: 1 tier up, 1 tier down (3 total tiers)
 * - Compression Tier Card: X tiers up only (no tiers down)
 * - Decompression Tier Card: X tiers down only (no tiers up)
 * <p>
 * Only the partitioned item tier counts toward storage capacity.
 * Compressed/decompressed forms are virtual utilities for network access.
 */
public class CompactingCellInventory implements ICellInventory<IAEItemStack> {

    private static final String NBT_STORED_BASE_UNITS = "storedBaseUnits";
    private static final String NBT_CONV_RATES = "convRates";
    private static final String NBT_PROTO_ITEMS = "protoItems";
    private static final String NBT_MAIN_TIER = "mainTier";
    private static final String NBT_CACHED_PARTITION = "cachedPartition";
    private static final String NBT_TIERS_UP = "tiersUp";
    private static final String NBT_TIERS_DOWN = "tiersDown";
    private static final String NBT_CHAIN_VERSION = "chainVersion";

    /** Default tiers when no tier card is installed. */
    private static final int DEFAULT_TIERS_UP = 1;
    private static final int DEFAULT_TIERS_DOWN = 1;

    // Cached partition item for detecting changes
    private ItemStack cachedPartitionItem = ItemStack.EMPTY;

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final IInternalCompactingCell cellType;

    private final NBTTagCompound tagCompound;

    // Compression chain data (dynamically sized)
    private ItemStack[] protoStack;
    private long[] convRate;
    private int currentMaxTiers;

    // Cached tier configuration from upgrade cards
    private int cachedTiersUp;
    private int cachedTiersDown;

    // Single storage pool in base units (lowest tier, rate=1)
    private long storedBaseUnits = 0;

    // The tier that matches the partitioned item (the "main" tier for storage counting)
    private int mainTier = -1;

    // Cached upgrade card state
    private boolean cachedHasOverflowCard = false;
    private boolean cachedHasOreDictCard = false;

    // Cached state to avoid repeated checks during normal operation
    // These are set after initialization and don't change until the cell is removed from the drive
    private boolean cachedHasPartition = false;
    private boolean chainFullyInitialized = false;

    /**
     * Version counter for the compression chain. Incremented when the chain is initialized or replaced.
     * Used to detect external chain changes (e.g., from API calls on another handler instance).
     */
    private int chainVersion = 0;

    /**
     * Local copy of the chain version from NBT at construction/load time.
     * Compared against NBT to detect external changes.
     */
    private int localChainVersion = 0;

    /**
     * Cached mapping from valid ore dictionary ID to slot indice.
     * Built during chain initialization for O(1) slot lookup during ore dict matching.
     * Empty map if no ore dict card is installed or chain has no valid ore entries.
     */
    private Map<Integer, Integer> oreIdToSlot = Collections.emptyMap();

    /**
     * Cached result of isCompressionChainEmpty() to avoid looping protoStack on every call.
     * Invalidated whenever the chain is modified (init, reset, load from NBT).
     */
    private boolean cachedChainEmpty = true;

    /**
     * Cached IAEItemStack prototypes for each tier, built from protoStack during chain init.
     * Used in getAvailableItems() and queueCrossTierNotification() to avoid calling
     * channel.createStack() (which allocates a new AEItemStack + does registry lookup) on every call.
     */
    private IAEItemStack[] cachedAEStacks;

    /**
     * Cached ItemStackKey per protoStack tier, built alongside cachedAEStacks.
     * Used in getSlotForItem(), isInCompressionChain(), isAllowedByPartition(), and
     * updateMainTier() to replace CellMathHelper.areItemsEqual() with a null-check
     * + ItemStackKey.matches(), avoiding repeated isEmpty() + areItemStackTagsEqual() overhead.
     */
    private ItemStackKey[] cachedProtoKeys;

    /**
     * Cached max capacity in base units. This is a pure function of totalBytes, bytesPerType,
     * unitsPerByte, and convRate[mainTier], all immutable after chain init.
     * Invalidated on chain rebuild only.
     */
    private long cachedMaxCapacityInBaseUnits = -1;

    /**
     * Set by getSlotForItem() to indicate whether the last match was a direct proto match
     * (true) or an ore dict equivalent match (false). Read by injectItems() to avoid the
     * redundant isDirectMatch() call that would repeat the same areItemsEqual comparison.
     */
    private boolean lastSlotWasDirectMatch = false;


    public CompactingCellInventory(IInternalCompactingCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

        this.tagCompound = Platform.openNbtData(cellStack);

        // Read current upgrade state (what the upgrades cards are NOW)
        updateCachedUpgradeState();
        int currentTiersUp = cachedTiersUp;
        int currentTiersDown = cachedTiersDown;

        // IMPORTANT: Use the SAVED tier configuration to size arrays, not current upgrades.
        // This prevents losing chain data when upgrade cards are removed.
        // The chain will be rebuilt with the correct tier config when items are inserted/extracted
        // (when we have access to a World for recipe lookups).
        int savedTiersUp = tagCompound.hasKey(NBT_TIERS_UP) ? tagCompound.getInteger(NBT_TIERS_UP) : DEFAULT_TIERS_UP;
        int savedTiersDown = tagCompound.hasKey(NBT_TIERS_DOWN) ? tagCompound.getInteger(NBT_TIERS_DOWN) : DEFAULT_TIERS_DOWN;

        // If no saved config, fall back to current upgrade config
        if (!tagCompound.hasKey(NBT_TIERS_UP) && !tagCompound.hasKey(NBT_TIERS_DOWN)) {
            savedTiersUp = currentTiersUp;
            savedTiersDown = currentTiersDown;
        }

        this.currentMaxTiers = savedTiersUp + 1 + savedTiersDown;
        initializeArrays();

        // Set cached values to match the SAVED config so array indices are consistent
        // hasTierConfigChanged() will later detect if current upgrades differ from cached
        cachedTiersUp = savedTiersUp;
        cachedTiersDown = savedTiersDown;

        loadFromNBT();

        // Cache partition state after loading NBT
        cachedHasPartition = checkHasPartition();

        // Check if tier config is different from current upgrade cards.
        // If so, chain needs rebuild but keep existing data for display.
        // The rebuild will happen in updateCompressionChainIfNeeded() when we have World access.
        // DO NOT set mainTier = -1 here, as that breaks getAvailableItems/tooltip display.
        boolean tierConfigMatches = (savedTiersUp == currentTiersUp && savedTiersDown == currentTiersDown);
        chainFullyInitialized = tierConfigMatches && cachedHasPartition && !cachedChainEmpty && mainTier >= 0;
    }

    /**
     * Initialize or reinitialize arrays based on current tier configuration.
     */
    private void initializeArrays() {
        protoStack = new ItemStack[currentMaxTiers];
        convRate = new long[currentMaxTiers];
        cachedAEStacks = new IAEItemStack[currentMaxTiers];
        cachedProtoKeys = new ItemStackKey[currentMaxTiers];

        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
            cachedAEStacks[i] = null;
            cachedProtoKeys[i] = null;
        }

        // Invalidate derived caches
        cachedChainEmpty = true;
        cachedMaxCapacityInBaseUnits = -1;
    }

    /**
     * Update cached upgrade card state.
     */
    private void updateCachedUpgradeState() {
        IItemHandler upgrades = getUpgradesInventory();
        cachedHasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);
        cachedHasOreDictCard = CellUpgradeHelper.hasOreDictCard(upgrades);

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
     * Queue cross-tier notifications for deferred execution at end of tick.
     * <p>
     * This is necessary because extracting/injecting one tier affects all tiers.
     * Notifications are batched and merged to reduce grid notification overhead.
     * </p>
     * <p>
     * When an ore dict equivalent item is inserted, we need to emit correction deltas:
     * <ul>
     *   <li>Negative delta for the input item (AE2 thinks it was stored, but it wasn't)</li>
     *   <li>Positive delta for the proto item (the actual stored item)</li>
     * </ul>
     * </p>
     * 
     * @param src The action source
     * @param oldBaseUnits The base units before the operation
     * @param operatedSlot The slot that was directly operated on (-1 to notify all tiers)
     * @param oreDictInput The ore dict equivalent input item, or null if direct match
     * @param oreDictCount The count of ore dict items inserted (for correction delta)
     */
    private void queueCrossTierNotification(@Nullable IActionSource src, long oldBaseUnits, int operatedSlot,
                                            @Nullable IAEItemStack oreDictInput, long oreDictCount) {
        // Calculate changes for each tier
        // Skip the operated slot ONLY if it was a direct match (not ore dict equivalent)
        // Note: AE2 does NOT deduplicate notifications, so including the operated slot causes double-counting
        // for direct matches. But for ore dict equivalents, AE2 doesn't know about the conversion.
        List<IAEItemStack> changes = new ArrayList<>();

        // If ore dict conversion occurred, we need to correct the deltas:
        // 1. AE2 thinks the input item was stored - emit negative delta to cancel
        // 2. The proto item was actually stored - emit positive delta
        boolean isOreDictConversion = (oreDictInput != null && oreDictCount > 0);

        for (int i = 0; i < currentMaxTiers; i++) {
            // Skip operated slot only for direct matches
            if (i == operatedSlot && !isOreDictConversion) continue;
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            long oldCount = oldBaseUnits / convRate[i];
            long newCount = storedBaseUnits / convRate[i];
            long delta = newCount - oldCount;

            if (delta == 0) continue;

            IAEItemStack cached = (cachedAEStacks != null && i < cachedAEStacks.length) ? cachedAEStacks[i] : null;
            IAEItemStack stack = (cached != null) ? cached.copy() : channel.createStack(protoStack[i]);
            if (stack != null) {
                stack.setStackSize(delta);
                changes.add(stack);
            }
        }

        // Add ore dict correction: negate the input item since AE2 thinks it was stored
        if (isOreDictConversion) {
            IAEItemStack correction = oreDictInput.copy();
            correction.setStackSize(-oreDictCount);
            changes.add(correction);
        }

        if (changes.isEmpty()) return;

        // Queue for deferred execution - merges with other notifications for same cell
        DeferredCellOperations.queueCrossTierNotification(this, container, channel, changes, src);
    }

    private void loadFromNBT() {
        // Load stored base units
        storedBaseUnits = CellMathHelper.loadLong(tagCompound, NBT_STORED_BASE_UNITS);

        // Load chain version for external change detection
        chainVersion = tagCompound.hasKey(NBT_CHAIN_VERSION) ? tagCompound.getInteger(NBT_CHAIN_VERSION) : 0;
        localChainVersion = chainVersion;

        // Clear arrays before loading to ensure old data doesn't persist
        // when a new, potentially shorter, chain is loaded from NBT
        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }

        if (tagCompound.hasKey(NBT_CONV_RATES)) {
            long[] rates = CellMathHelper.loadLongArray(tagCompound, NBT_CONV_RATES, currentMaxTiers);
            System.arraycopy(rates, 0, convRate, 0, Math.min(rates.length, currentMaxTiers));
        }

        if (tagCompound.hasKey(NBT_PROTO_ITEMS)) {
            NBTTagCompound protoNbt = tagCompound.getCompoundTag(NBT_PROTO_ITEMS);
            for (int i = 0; i < currentMaxTiers; i++) {
                if (protoNbt.hasKey("item" + i)) {
                    protoStack[i] = new ItemStack(protoNbt.getCompoundTag("item" + i));
                }
            }
        }

        if (tagCompound.hasKey(NBT_MAIN_TIER)) mainTier = tagCompound.getInteger(NBT_MAIN_TIER);

        // Validate mainTier against current array bounds.
        // When a tier card is removed, the array shrinks but NBT may still have
        // a mainTier from the old (larger) configuration. Without this check,
        // mainTier can point past the end of the array, causing crashes.
        if (mainTier >= currentMaxTiers) mainTier = -1;

        if (tagCompound.hasKey(NBT_CACHED_PARTITION)) {
            cachedPartitionItem = new ItemStack(tagCompound.getCompoundTag(NBT_CACHED_PARTITION));
        }

        // Recalculate mainTier if it's invalid, but we have chain data
        // This handles cases where old NBT had mainTier saved incorrectly
        // Recompute cachedChainEmpty first since protoStack was just reloaded
        updateCachedChainEmpty();
        if (mainTier < 0 && !cachedChainEmpty && !cachedPartitionItem.isEmpty()) {
            recalculateMainTierFromCachedPartition();
        }

        // Rebuild ore dictionary mapping from loaded protoStack
        rebuildCachedOreIds();

        // Recompute remaining derived caches after chain data loaded from NBT
        rebuildCachedAEStacks();
        cachedMaxCapacityInBaseUnits = -1;
    }

    /**
     * Save only the stored base units to NBT.
     * This is the hot path - called on every inject/extract operation.
     */
    private void saveBaseUnitsOnly() {
        CellMathHelper.saveLong(tagCompound, NBT_STORED_BASE_UNITS, storedBaseUnits);
    }

    /**
     * Save all cell state to NBT including chain data.
     * This is the cold path - called when chain is initialized or modified.
     */
    private void saveToNBT() {
        saveBaseUnitsOnly();

        // Check if NBT has a newer chain version than us - if so, don't overwrite
        int nbtChainVersion = tagCompound.hasKey(NBT_CHAIN_VERSION) ? tagCompound.getInteger(NBT_CHAIN_VERSION) : 0;
        boolean canSaveChain = (chainVersion >= nbtChainVersion);

        // Only save chain data if:
        // 1. We have a chain initialized
        // 2. Our chain version is >= NBT version (we're not stale)
        if (!cachedChainEmpty && canSaveChain) {
            tagCompound.setInteger(NBT_MAIN_TIER, mainTier);
            tagCompound.setInteger(NBT_CHAIN_VERSION, chainVersion);
            tagCompound.setInteger(NBT_TIERS_UP, cachedTiersUp);
            tagCompound.setInteger(NBT_TIERS_DOWN, cachedTiersDown);
            CellMathHelper.saveLongArray(tagCompound, NBT_CONV_RATES, convRate);

            NBTTagCompound protoNbt = new NBTTagCompound();
            for (int i = 0; i < currentMaxTiers; i++) {
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
        } else if (storedBaseUnits == 0 && !hasPartition() && canSaveChain) {
            // Cell is truly empty and unpartitioned - clear chain data
            // Only clear if we're not stale (another handler might have valid data)
            tagCompound.removeTag(NBT_CONV_RATES);
            tagCompound.removeTag(NBT_PROTO_ITEMS);
            tagCompound.removeTag(NBT_CACHED_PARTITION);
            tagCompound.removeTag(NBT_CHAIN_VERSION);
            tagCompound.removeTag(NBT_TIERS_UP);
            tagCompound.removeTag(NBT_TIERS_DOWN);
        }
        // If chain is empty but partition exists, or if we're stale, don't touch chain NBT
    }

    /**
     * Save changes and notify container - deferred to end of tick for efficiency.
     * This is the hot path for inject/extract operations.
     */
    private void saveChangesDeferred() {
        saveBaseUnitsOnly();
        DeferredCellOperations.markDirty(this, container);
    }

    /**
     * Save all changes immediately including chain data.
     * Used when chain is modified (initialization, tier card changes).
     */
    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    /**
     * Initialize the compression chain for the given partitioned item.
     * The partitioned item becomes the "main" tier for storage counting.
     */
    private void initializeCompressionChain(@Nonnull ItemStack inputItem, @Nullable World world) {
        // Update tier configuration from upgrade cards
        updateCachedUpgradeState();
        int newMaxTiers = cachedTiersUp + 1 + cachedTiersDown;
        if (newMaxTiers != currentMaxTiers) {
            currentMaxTiers = newMaxTiers;
            initializeArrays();
        }

        if (world == null) {
            // Fallback: just use the single item without compression
            protoStack[0] = inputItem.copy();
            protoStack[0].setCount(1);
            convRate[0] = 1;
            mainTier = 0;
            rebuildCachedOreIds();

            // Recompute derived caches for fallback single-tier chain
            updateCachedChainEmpty();
            rebuildCachedAEStacks();
            cachedMaxCapacityInBaseUnits = -1;

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

        // Pre-compute valid ore IDs for the compression chain
        rebuildCachedOreIds();

        // Recompute derived caches after chain rebuild
        updateCachedChainEmpty();
        rebuildCachedAEStacks();
        cachedMaxCapacityInBaseUnits = -1;
    }

    /**
     * Rebuild the cached ore dictionary ID to slot mapping for the compression chain.
     * Called after chain initialization or when ore dict card state changes.
     */
    private void rebuildCachedOreIds() {
        if (!cachedHasOreDictCard) {
            oreIdToSlot = Collections.emptyMap();
            return;
        }

        oreIdToSlot = OreDictValidator.getOreIdToSlotMapping(protoStack);
    }

    /**
     * Recalculate mainTier from the cached partition item.
     * Used when loading NBT with invalid mainTier but valid chain data.
     */
    private void recalculateMainTierFromCachedPartition() {
        if (cachedPartitionItem.isEmpty()) return;

        // Default to -1 (invalid) if no match is found in the chain.
        // This signals that the chain needs rebuilding rather than silently
        // defaulting to tier 0 which could misinterpret stored base units.
        mainTier = -1;
        for (int i = 0; i < currentMaxTiers; i++) {
            if (cachedProtoKeys != null && cachedProtoKeys[i] != null
                    && cachedProtoKeys[i].matches(cachedPartitionItem)) {
                mainTier = i;
                return;
            }
        }
    }

    /**
     * Get the slot index for the given item, or -1 if not matching.
     * <p>
     * When ore dictionary card is installed, also matches ore dictionary
     * equivalent items to their corresponding proto stack slot.
     * Uses cached ore ID to slot mapping for O(1) candidate lookup.
     * </p>
     */
    private int getSlotForItem(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        // First try direct match using cached keys (avoids repeated isEmpty + areItemStackTagsEqual)
        for (int i = 0; i < currentMaxTiers; i++) {
            if (cachedProtoKeys != null && cachedProtoKeys[i] != null
                    && cachedProtoKeys[i].matches(definition)) {
                lastSlotWasDirectMatch = true;
                return i;
            }
        }

        // If ore dict card installed, try ore dictionary equivalence
        if (cachedHasOreDictCard && !oreIdToSlot.isEmpty()) {
            int slot = OreDictValidator.findFirstMatchingSlot(definition, oreIdToSlot, protoStack);
            if (slot >= 0) {
                lastSlotWasDirectMatch = false;
                return slot;
            }
        }

        lastSlotWasDirectMatch = false;
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

        // If compression chain is empty, reject - the caller should have called
        // updateCompressionChainIfNeeded() before this. If the chain is still empty
        // after that, it means initialization failed (no World, no recipes, etc.)
        if (cachedChainEmpty) return -1;

        return getSlotForItem(stack);
    }

    /**
     * Recompute the isCompressionChainEmpty result by looping protoStack.
     * Only called when the chain is modified, callers should read cachedChainEmpty instead.
     */
    private void updateCachedChainEmpty() {
        for (int i = 0; i < currentMaxTiers; i++) {
            if (!protoStack[i].isEmpty()) {
                cachedChainEmpty = false;
                return;
            }
        }

        cachedChainEmpty = true;
    }

    /**
     * Rebuild the cached IAEItemStack prototypes from protoStack.
     * Called after chain initialization or NBT load so getAvailableItems()
     * and queueCrossTierNotification() can use copy() instead of channel.createStack().
     */
    private void rebuildCachedAEStacks() {
        if (cachedAEStacks == null || cachedAEStacks.length != currentMaxTiers) {
            cachedAEStacks = new IAEItemStack[currentMaxTiers];
        }
        if (cachedProtoKeys == null || cachedProtoKeys.length != currentMaxTiers) {
            cachedProtoKeys = new ItemStackKey[currentMaxTiers];
        }

        for (int i = 0; i < currentMaxTiers; i++) {
            if (protoStack[i].isEmpty()) {
                cachedAEStacks[i] = null;
                cachedProtoKeys[i] = null;
            } else {
                cachedAEStacks[i] = channel.createStack(protoStack[i]);
                cachedProtoKeys[i] = ItemStackKey.of(protoStack[i]);
            }
        }
    }

    /**
     * Check if the compression chain has been initialized.
     * Public method for tooltip use - returns true if protoStack has items.
     */
    public boolean isChainInitialized() {
        return !cachedChainEmpty;
    }

    /**
     * Check if the cell has any items stored (in base units).
     * This is more accurate than getStoredItemCount() which uses main tier.
     */
    public boolean hasStoredItems() {
        return storedBaseUnits > 0;
    }

    /**
     * Check if NBT has chain data that differs from our local cache.
     * This can happen when another handler (e.g., from API call) writes to NBT
     * after this handler was constructed.
     * <p>
     * Checks the chain version number to detect external changes, even if
     * our local cache is not empty.
     * </p>
     *
     * @return true if NBT has chain data that we need to reload
     */
    private boolean hasUnloadedNBTChainData() {
        // Check if NBT has chain data
        if (!tagCompound.hasKey(NBT_PROTO_ITEMS) || !tagCompound.hasKey(NBT_CONV_RATES)) {
            return false;
        }

        // If local cache is empty, check if NBT data is for the CURRENT tier config.
        // When reloadFromNBTIfNeeded() resizes arrays for a tier card change, the local
        // chain becomes empty but NBT still has data from the OLD tier config. Loading
        // that stale data would corrupt the chain (wrong items, out-of-bounds mainTier).
        // Only reload if the NBT tier config matches what we expect.
        if (cachedChainEmpty) {
            int nbtTiersUp = tagCompound.hasKey(NBT_TIERS_UP) ? tagCompound.getInteger(NBT_TIERS_UP) : DEFAULT_TIERS_UP;
            int nbtTiersDown = tagCompound.hasKey(NBT_TIERS_DOWN) ? tagCompound.getInteger(NBT_TIERS_DOWN) : DEFAULT_TIERS_DOWN;

            // If the NBT tier config doesn't match our current cached config,
            // the data is stale and should NOT be loaded - the chain will be
            // rebuilt from scratch by updateCompressionChainIfNeeded() instead
            if (nbtTiersUp != cachedTiersUp || nbtTiersDown != cachedTiersDown) return false;

            return true;
        }

        // Check if NBT chain version differs from our local version
        // This detects when another handler instance has replaced the chain
        int nbtVersion = tagCompound.hasKey(NBT_CHAIN_VERSION) ? tagCompound.getInteger(NBT_CHAIN_VERSION) : 0;

        return nbtVersion != localChainVersion;
    }

    /**
     * Quick check if the chain version in NBT differs from our local version.
     * <p>
     * This detects when another handler instance has replaced the chain (e.g., via
     * Cell Terminal API call). Used in the fast path to invalidate stale chain state
     * without performing a full NBT reload.
     * <p>
     * The cost is a single HashMap lookup on the NBT compound, which is negligible
     * compared to the cost of operating on a stale chain.
     *
     * @return true if the chain has been externally modified since we last loaded it
     */
    private boolean hasChainVersionChanged() {
        int nbtVersion = tagCompound.hasKey(NBT_CHAIN_VERSION) ? tagCompound.getInteger(NBT_CHAIN_VERSION) : 0;

        return nbtVersion != localChainVersion;
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
     * Reload chain data from NBT if it has been updated externally,
     * or resize arrays if tier card configuration has changed.
     * <p>
     * Call this before operations that depend on the compression chain.
     * </p>
     * <p>
     * Handles the following cases:
     * <ul>
     *   <li>NBT has chain data that local cache doesn't have</li>
     *   <li>Tier card was added or removed (arrays need resizing, chain needs recomputing)</li>
     * </ul>
     * </p>
     * <p>
     * Note: Does NOT rebuild the chain here - that requires a World and happens in
     * updateCompressionChainIfNeeded(). This method only resizes arrays and sets
     * mainTier = -1 to signal that a rebuild is needed.
     * </p>
     */
    private void reloadFromNBTIfNeeded() {
        // Check if NBT has data we haven't loaded
        if (hasUnloadedNBTChainData()) {
            loadFromNBT();
            // Update cached state after reloading
            cachedHasPartition = checkHasPartition();
            chainFullyInitialized = cachedHasPartition && !cachedChainEmpty && mainTier >= 0;
        }

        // Check if tier card configuration has changed
        // IMPORTANT: Do NOT update cached tier values here - hasTierConfigChanged() compares
        // current upgrades against cached values. If we update cached values first,
        // hasTierConfigChanged() will always return false.
        if (!hasTierConfigChanged()) return;

        // Tier config changed - need to resize arrays and mark for rebuild
        // The actual chain rebuild happens in updateCompressionChainIfNeeded() which has World access
        if (!cachedPartitionItem.isEmpty()) {
            // Read the new tier configuration from upgrade cards
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

            // Calculate new max tiers
            int newMaxTiers = newTiersUp + 1 + newTiersDown;

            // Save current data
            long savedBaseUnits = storedBaseUnits;
            ItemStack savedPartition = cachedPartitionItem.copy();

            // Reset and resize arrays
            currentMaxTiers = newMaxTiers;
            initializeArrays();
            storedBaseUnits = savedBaseUnits;
            cachedPartitionItem = savedPartition;

            // Mark chain for rebuild - mainTier = -1 signals that chain needs rebuilding
            // The actual rebuild happens in updateCompressionChainIfNeeded() with World access
            mainTier = -1;
            chainFullyInitialized = false;

            // Update cached tier values AFTER resizing, so subsequent calls to
            // hasTierConfigChanged() return false (config is now synced)
            cachedTiersUp = newTiersUp;
            cachedTiersDown = newTiersDown;
        }
    }

    /**
     * Check if an item is part of the compression chain.
     * Used by the handler to allow chain items through the filter.
     * <p>
     * When ore dictionary card is installed, also matches ore dictionary
     * equivalent items to their corresponding proto stack slot.
     * Uses cached ore ID to slot mapping for O(1) candidate lookup.
     * </p>
     */
    public boolean isInCompressionChain(@Nonnull IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        // First try direct match using cached keys
        for (int i = 0; i < currentMaxTiers; i++) {
            if (cachedProtoKeys != null && cachedProtoKeys[i] != null
                    && cachedProtoKeys[i].matches(definition)) return true;
        }

        // If ore dict card installed, try ore dictionary equivalence
        if (cachedHasOreDictCard && !oreIdToSlot.isEmpty()) {
            return OreDictValidator.hasMatchingSlot(definition, oreIdToSlot, protoStack);
        }

        return false;
    }

    /**
     * Check if the cell has a partition configured.
     * Uses cached value for performance during normal operation.
     */
    public boolean hasPartition() {
        return cachedHasPartition;
    }

    /**
     * Actually check if the cell has a partition configured by reading the config inventory.
     * Used during initialization and when the cache needs to be refreshed.
     */
    private boolean checkHasPartition() {
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
        return !CellMathHelper.areItemsEqual(currentPartition, cachedPartitionItem);
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
     * <p>
     * This method forces chain initialization even if a chain already exists,
     * as long as the cell has no stored items.
     */
    public void initializeChainForItem(@Nonnull ItemStack partitionItem, @Nonnull World world) {
        if (partitionItem.isEmpty()) return;
        if (storedBaseUnits > 0) return; // Has items, don't change chain

        // Force initialize the chain with the given partition item
        reset();
        initializeCompressionChain(partitionItem, world);

        // Fallback: if chain is still empty after initialization (shouldn't happen),
        // create a single-tier chain with just the partition item
        if (cachedChainEmpty) {
            protoStack[0] = partitionItem.copy();
            protoStack[0].setCount(1);
            convRate[0] = 1;
            mainTier = 0;

            // Update caches for the fallback single-tier chain
            updateCachedChainEmpty();
            rebuildCachedAEStacks();
            cachedMaxCapacityInBaseUnits = -1;
        }

        cachedPartitionItem = partitionItem.copy();
        cachedPartitionItem.setCount(1);
        cachedHasPartition = true;
        chainFullyInitialized = mainTier >= 0;

        // Increment chain version so other handler instances detect the change
        chainVersion++;
        localChainVersion = chainVersion;

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
     * Update the compression chain if the partition has changed, tier cards changed,
     * or if chain needs initialization/rebuilding.
     * Call this before any operation that depends on the compression chain.
     * <p>
     * If the cell contains items, partition changes are blocked to prevent data loss.
     * The partition will be reverted to match the stored items.
     * However, tier card changes ARE allowed even with items, as they only change
     * the compression depth, not the base item.
     */
    private void updateCompressionChainIfNeeded(@Nullable World world) {
        // First, check if NBT has chain data written by another handler (e.g., API call)
        // that we haven't loaded yet. Also checks for tier card changes and resizes arrays.
        // If tier config changed, mainTier will be set to -1 to signal rebuild needed.
        reloadFromNBTIfNeeded();

        // Check if chain needs rebuilding due to tier card change
        // mainTier == -1 with non-empty cachedPartitionItem means reloadFromNBTIfNeeded()
        // detected a tier config change and resized arrays, but chain needs rebuilding
        boolean needsTierRebuild = (mainTier < 0) && !cachedPartitionItem.isEmpty();

        // Check if chain needs to be built (partition exists but chain is empty)
        boolean needsInitialization = hasPartition() && cachedChainEmpty;

        // Handle tier card change - rebuild chain even with items
        if (needsTierRebuild) {
            // Save the base units
            long savedBaseUnits = storedBaseUnits;

            // Rebuild chain with new tier configuration
            reset();
            storedBaseUnits = savedBaseUnits;
            initializeCompressionChain(cachedPartitionItem, world);
            chainFullyInitialized = mainTier >= 0 && !cachedChainEmpty;
            saveChanges();

            return;
        }

        // Handle partition-based initialization
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
            cachedHasPartition = false;
            chainFullyInitialized = false;
            saveChanges();

            return;
        }

        // Partition set or changed - initialize/reinitialize the chain
        reset();
        initializeCompressionChain(currentPartition, world);
        cachedPartitionItem = currentPartition.copy();
        cachedPartitionItem.setCount(1);
        cachedHasPartition = true;
        chainFullyInitialized = mainTier >= 0 && !cachedChainEmpty;

        // Increment chain version so other handler instances detect the change
        chainVersion++;
        localChainVersion = chainVersion;

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

        // First check if it's in our compression chain (exact match, using cached keys)
        for (int i = 0; i < currentMaxTiers; i++) {
            if (cachedProtoKeys != null && cachedProtoKeys[i] != null
                    && cachedProtoKeys[i].matches(definition)) return true;
        }

        // Check partition slots directly (for initial setup)
        for (int i = 0; i < configInv.getSlots(); i++) {
            ItemStack partItem = configInv.getStackInSlot(i);
            if (!partItem.isEmpty() && CellMathHelper.areItemsEqual(partItem, definition)) return true;
        }

        // If ore dict card is installed, also allow ore dictionary equivalent items
        if (cachedHasOreDictCard) {
            if (oreIdToSlot.isEmpty()) {
                // Fallback: check partition slots via ore dict if the ore ID map is empty (shouldn't happen)
                for (int i = 0; i < configInv.getSlots(); i++) {
                    ItemStack partItem = configInv.getStackInSlot(i);
                    if (!partItem.isEmpty() && OreDictValidator.areOreDictEquivalent(partItem, definition)) {
                        return true;
                    }
                }
            } else {
                return OreDictValidator.hasMatchingSlot(definition, oreIdToSlot, protoStack);
            }
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
        if (mainTier < 0 || mainTier >= currentMaxTiers) return 0;
        if (convRate[mainTier] <= 0) return 0;

        // Convert base units to main tier units (floor - actual count)
        return storedBaseUnits / convRate[mainTier];
    }

    /**
     * Calculate stored items in main tier with ceiling division for byte calculation.
     * This ensures we don't undercount storage usage (8 nuggets = 1 ingot for byte purposes).
     */
    private long getStoredInMainTierCeiling() {
        if (mainTier < 0 || mainTier >= currentMaxTiers) return 0;
        if (convRate[mainTier] <= 0) return 0;

        // Ceiling division: (a + b - 1) / b
        return (storedBaseUnits + convRate[mainTier] - 1) / convRate[mainTier];
    }

    /**
     * Get the maximum capacity in base units.
     * Accounts for type overhead in bytes.
     * <p>
     * Uses conservative calculation to ensure usedBytes never exceeds totalBytes.
     * The conversion chain between bytes <-> items <-> base units uses ceiling
     * divisions for display, so we must be slightly conservative here.
     */
    private long getMaxCapacityInBaseUnits() {
        if (cachedMaxCapacityInBaseUnits >= 0) return cachedMaxCapacityInBaseUnits;

        long totalBytes = getTotalBytes();
        long typeBytes = cachedChainEmpty ? 0 : getBytesPerType();
        long availableBytes = totalBytes - typeBytes;

        if (availableBytes <= 0 || mainTier < 0 || mainTier >= currentMaxTiers || convRate[mainTier] <= 0) {
            cachedMaxCapacityInBaseUnits = 0;
            return 0;
        }

        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate max main tier items that fit in available bytes
        // Use floor division - this is the actual number of complete items
        long maxMainTierItems = availableBytes * itemsPerByte;

        // Convert to base units
        // Subtract (convRate - 1) to ensure ceiling division in usedBytes doesn't overflow
        // This ensures: ceil(stored / convRate) / itemsPerByte <= availableBytes
        long maxBaseUnits = CellMathHelper.multiplyWithOverflowProtection(maxMainTierItems, convRate[mainTier]);

        // Reserve space for ceiling rounding: at most (convRate - 1) base units
        // plus (itemsPerByte - 1) items worth of rounding.
        // Use overflow-protected arithmetic since convRate can be very large with tier cards.
        long reserveForRounding = CellMathHelper.addWithOverflowProtection(
            convRate[mainTier] - 1,
            CellMathHelper.multiplyWithOverflowProtection(itemsPerByte - 1, convRate[mainTier])
        );
        if (maxBaseUnits > reserveForRounding) {
            maxBaseUnits -= reserveForRounding;
        } else {
            maxBaseUnits = 0;
        }

        cachedMaxCapacityInBaseUnits = maxBaseUnits;
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
        if (mainTier >= 0 && mainTier < currentMaxTiers && !protoStack[mainTier].isEmpty()) {
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

        for (int i = mainTier + 1; i < currentMaxTiers; i++) {
            if (!protoStack[i].isEmpty()) return protoStack[i];
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get all higher tier (compressed) items, from closest to main tier to most compressed.
     *
     * @return List of compressed tier items, may be empty
     */
    @Nonnull
    public List<ItemStack> getAllHigherTierItems() {
        List<ItemStack> items = new ArrayList<>();
        if (mainTier <= 0) return items;

        for (int i = mainTier - 1; i >= 0; i--) {
            if (!protoStack[i].isEmpty()) items.add(protoStack[i]);
        }

        return items;
    }

    /**
     * Get all lower tier (decompressed) items, from closest to main tier to least compressed.
     *
     * @return List of decompressed tier items, may be empty
     */
    @Nonnull
    public List<ItemStack> getAllLowerTierItems() {
        List<ItemStack> items = new ArrayList<>();
        if (mainTier < 0) return items;

        for (int i = mainTier + 1; i < currentMaxTiers; i++) {
            if (!protoStack[i].isEmpty()) items.add(protoStack[i]);
        }

        return items;
    }

    private void reset() {
        for (int i = 0; i < currentMaxTiers; i++) {
            protoStack[i] = ItemStack.EMPTY;
            convRate[i] = 0;
        }

        storedBaseUnits = 0;
        mainTier = -1;
        chainFullyInitialized = false;

        // Invalidate derived caches
        cachedChainEmpty = true;
        cachedMaxCapacityInBaseUnits = -1;
        if (cachedAEStacks != null) {
            for (int i = 0; i < cachedAEStacks.length; i++) cachedAEStacks[i] = null;
        }
        if (cachedProtoKeys != null) {
            for (int i = 0; i < cachedProtoKeys.length; i++) cachedProtoKeys[i] = null;
        }
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        // Fast path: if chain is fully initialized and hasn't been replaced externally,
        // skip all the validation checks. The hasChainVersionChanged() check detects when
        // another handler (e.g., Cell Terminal API) has replaced the chain since we loaded it.
        int slot;
        if (chainFullyInitialized && !hasChainVersionChanged()) {
            slot = getSlotForItem(input);
        } else {
            // Slow path: need to initialize or validate the chain
            chainFullyInitialized = false;
            reloadFromNBTIfNeeded();

            if (!hasPartition()) return input;

            World world = CellMathHelper.getWorldFromSource(src);
            updateCompressionChainIfNeeded(world);

            slot = canAcceptItem(input);
        }

        // Item not in compression chain - reject
        if (slot < 0) return input;

        long rate = convRate[slot];
        if (rate <= 0) return input;

        // Calculate how many items can fit
        // With tier cards, convRates can be very large (up to 9^15 for 15 decompression tiers),
        // so overflow protection is needed even for normal cells
        long inputCount = input.getStackSize();
        long inputInBaseUnits = CellMathHelper.multiplyWithOverflowProtection(inputCount, rate);

        // Get remaining capacity - this is the expensive call, so we do it once
        long remainingCapacity = getMaxCapacityInBaseUnits() - storedBaseUnits;
        if (remainingCapacity < 0) remainingCapacity = 0;

        // Check if input is an ore dict equivalent (not direct match)
        // If so, we need to emit correction deltas
        // getSlotForItem already determined this, reuse the cached result
        boolean directMatch = lastSlotWasDirectMatch;

        // Fast path: all items fit
        if (inputInBaseUnits <= remainingCapacity) {
            if (mode == Actionable.MODULATE) {
                long oldBaseUnits = storedBaseUnits;
                storedBaseUnits += inputInBaseUnits;
                saveChangesDeferred();
                queueCrossTierNotification(src, oldBaseUnits, slot,
                    directMatch ? null : input, directMatch ? 0 : inputCount);
            }

            return null;
        }

        // Partial insert or cell full
        long canInsert = remainingCapacity / rate;

        if (canInsert <= 0) {
            // Cell is full - check overflow card
            if (cachedHasOverflowCard) return null;

            return input;
        }

        long actualBaseUnits = canInsert * rate;

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;
            storedBaseUnits += actualBaseUnits;
            saveChangesDeferred();
            queueCrossTierNotification(src, oldBaseUnits, slot,
                directMatch ? null : input, directMatch ? 0 : canInsert);
        }

        // Overflow card voids the remainder
        if (cachedHasOverflowCard) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(inputCount - canInsert);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        if (!chainFullyInitialized || hasChainVersionChanged()) {
            // Slow path: need to initialize or validate the chain
            chainFullyInitialized = false;
            reloadFromNBTIfNeeded();
            updateCompressionChainIfNeeded(CellMathHelper.getWorldFromSource(src));
        }

        int slot = getSlotForItem(request);
        if (slot < 0) return null;

        long rate = convRate[slot];
        if (rate <= 0) return null;

        // Calculate available at this tier using integer division
        long availableInThisTier = storedBaseUnits / rate;
        if (availableInThisTier <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), availableInThisTier);

        if (mode == Actionable.MODULATE) {
            long oldBaseUnits = storedBaseUnits;
            // toExtract <= storedBaseUnits / rate, so toExtract * rate <= storedBaseUnits (no overflow).
            // Safety clamp in case of any edge case arithmetic issue.
            long baseUnitsToRemove = CellMathHelper.multiplyWithOverflowProtection(toExtract, rate);
            storedBaseUnits = Math.max(0, storedBaseUnits - baseUnitsToRemove);
            saveChangesDeferred();
            // For extraction, we always return the proto item, so no ore dict correction needed
            queueCrossTierNotification(src, oldBaseUnits, slot, null, 0);
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        // Reload from NBT if needed, check for tier card changes, or detect external chain replacement
        if (!chainFullyInitialized || hasChainVersionChanged()) {
            reloadFromNBTIfNeeded();

            // If mainTier == -1, the chain needs rebuilding (tier card changed)
            // Try to get World from container to rebuild the chain
            if (mainTier < 0 && !cachedPartitionItem.isEmpty()) {
                World world = CellMathHelper.getWorldFromContainer(container);
                if (world != null) updateCompressionChainIfNeeded(world);
            }
        }

        if (storedBaseUnits <= 0) return out;

        // Report available items for each tier based on the shared pool
        // Each tier shows how many of that item could be fully extracted
        for (int i = 0; i < currentMaxTiers; i++) {
            if (protoStack[i].isEmpty() || convRate[i] <= 0) continue;

            // Calculate how many of this tier can be extracted from the pool
            long availableCount = storedBaseUnits / convRate[i];
            if (availableCount <= 0) continue;

            // Use pre-built AE stack prototype + copy instead of channel.createStack
            IAEItemStack cached = (cachedAEStacks != null && i < cachedAEStacks.length) ? cachedAEStacks[i] : null;
            IAEItemStack stack = (cached != null) ? cached.copy() : channel.createStack(protoStack[i]);
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
        return cachedChainEmpty && getRemainingItemCount() > 0;
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
        return cachedChainEmpty ? 0 : 1;
    }

    @Override
    public long getRemainingItemTypes() {
        return cachedChainEmpty ? 1 : 0;
    }

    @Override
    public long getUsedBytes() {
        int itemsPerByte = channel.getUnitsPerByte();

        // Use ceiling division for main tier count - 8 nuggets counts as 1 ingot for byte purposes
        long storedItemsCeiling = getStoredInMainTierCeiling();

        // Add type overhead
        long typeBytes = cachedChainEmpty ? 0 : getBytesPerType();

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
}
