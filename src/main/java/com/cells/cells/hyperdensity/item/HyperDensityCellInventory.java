package com.cells.cells.hyperdensity.item;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.networking.security.IActionSource;
import appeng.util.Platform;

import com.cells.util.CellBenchmark;
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;


/**
 * Inventory implementation for hyper-density storage cells.
 * 
 * This inventory handles the internal byte multiplier, ensuring all calculations
 * are overflow-safe. The display shows standard byte values (1k, 4k, etc.)
 * but internally stores vastly more.
 * 
 * Key overflow protection points:
 * - All capacity calculations use CellMathHelper.multiplyWithOverflowProtection
 * - Storage is tracked in a way that avoids overflow during item operations
 * - Division is preferred over multiplication where possible
 * 
 * <h2>Equal Distribution Upgrade</h2>
 * When an Equal Distribution Card is installed, this cell operates in a special mode:
 * <ul>
 *   <li>The type limit is reduced to the card's value (1, 2, 4, 8, 16, 32, or 63)</li>
 *   <li>The total capacity is divided equally among those types</li>
 *   <li>Each type can only store up to its allocated share</li>
 * </ul>
 */
public class HyperDensityCellInventory implements ICellInventory<IAEItemStack> {

    // NBT keys - use "Stored" prefix to avoid conflicts with ItemStack's "Count" tag
    private static final String NBT_STORED_ITEM_COUNT = "StoredItemCount";
    private static final String NBT_ITEM_TYPE = "itemType";
    private static final String NBT_STORED_COUNT = "StoredCount"; // Per-item count key

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final IItemHyperDensityCell cellType;

    private final NBTTagCompound tagCompound;

    // Storage tracking - count of stored items (not bytes)
    private long storedItemCount = 0;
    private int storedTypes = 0;

    // Cached upgrade card states
    private int equalDistributionLimit = 0;
    private boolean cachedHasOverflowCard = false;

    public HyperDensityCellInventory(IItemHyperDensityCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);

        loadFromNBT();

        IItemHandler upgrades = cellType.getUpgradesInventory(cellStack);
        equalDistributionLimit = CellUpgradeHelper.getEqualDistributionLimit(upgrades);
        cachedHasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);
    }

    /**
     * Get the effective maximum types this cell can hold.
     * If Equal Distribution is active, returns that limit; otherwise MAX_TYPES.
     */
    private int getEffectiveMaxTypes() {
        int maxTypes = cellType.getMaxTypes();

        if (equalDistributionLimit > 0) return Math.min(equalDistributionLimit, maxTypes);

        return maxTypes;
    }

    /**
     * Get the per-type capacity limit when Equal Distribution is active.
     * Returns Long.MAX_VALUE if Equal Distribution is not active.
     * 
     * When Equal Distribution is active, the total capacity must be divided
     * among N types, and each type consumes bytesPerType overhead. So:
     * - Total available = totalBytes - (N * bytesPerType)
     * - Per-type capacity = (Total available * itemsPerByte * multiplier) / N
     * 
     * To avoid overflow while maintaining precision, we use overflow-safe
     * division that handles the case where the numerator would overflow.
     */
    private long getPerTypeCapacity() {
        if (equalDistributionLimit <= 0) return Long.MAX_VALUE;

        int n = equalDistributionLimit;
        long displayBytes = cellType.getDisplayBytes(cellStack);
        long multiplier = cellType.getByteMultiplier();
        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate overhead for ALL N types (not just currently stored ones)
        long typeBytesDisplay = (long) n * getDisplayBytesPerType();
        long availableDisplayBytes = displayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        // Use overflow-safe division: (a * b * c) / n
        // We want to divide by n while preserving as much precision as possible
        return CellMathHelper.multiplyThenDivide(availableDisplayBytes, itemsPerByte, multiplier, n);
    }

    private void loadFromNBT() {
        storedItemCount = tagCompound.getLong(NBT_STORED_ITEM_COUNT);

        // Count stored types from NBT
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        storedTypes = 0;
        for (String key : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(key);
            if (itemTag.getLong(NBT_STORED_COUNT) > 0) storedTypes++;
        }
    }

    private long loadLongFromTag(NBTTagCompound tag) {
        return tag.getLong(NBT_STORED_COUNT);
    }

    private void saveLongToTag(NBTTagCompound tag, long value) {
        // Use native long with safe key name
        tag.setLong(NBT_STORED_COUNT, value);
    }

    private void saveToNBT() {
        // Save using native long
        tagCompound.setLong(NBT_STORED_ITEM_COUNT, storedItemCount);
    }

    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    /**
     * Get the total capacity in items (not bytes).
     * This is calculated by: (totalBytes - typeOverhead) * itemsPerByte
     * 
     * We use careful division to avoid overflow.
     */
    private long getTotalItemCapacity() {
        return getTotalItemCapacityForTypes(storedTypes);
    }

    /**
     * Get the total capacity assuming one additional type will be stored.
     * Used when inserting a new type to account for its overhead upfront.
     */
    private long getTotalItemCapacityWithExtraType() {
        return getTotalItemCapacityForTypes(storedTypes + 1);
    }

    /**
     * Get the total capacity in items for a given number of types.
     * 
     * When Equal Distribution is active, we always reserve overhead for ALL N types,
     * regardless of how many are currently stored. This ensures each type gets a fair
     * and consistent share of the capacity. The total is derived from perTypeCapacity * N
     * to ensure consistency between the two calculations.
     * 
     * @param typeCount The number of types to account for in overhead calculation
     *                  (ignored when Equal Distribution is active)
     */
    private long getTotalItemCapacityForTypes(int typeCount) {
        // When Equal Distribution is active, derive total from per-type to ensure consistency
        if (equalDistributionLimit > 0) {
            long perType = getPerTypeCapacity();
            return CellMathHelper.multiplyWithOverflowProtection(perType, equalDistributionLimit);
        }

        long displayBytes = cellType.getDisplayBytes(cellStack);
        long multiplier = cellType.getByteMultiplier();
        int itemsPerByte = channel.getUnitsPerByte();

        // Calculate type overhead in display bytes, then multiply
        long typeBytesDisplay = (long) typeCount * getDisplayBytesPerType();
        long availableDisplayBytes = displayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        // availableDisplayBytes * multiplier * itemsPerByte
        // Do this carefully to avoid overflow
        // First: availableDisplayBytes * itemsPerByte (usually safe)
        long itemsAtDisplayScale = CellMathHelper.multiplyWithOverflowProtection(availableDisplayBytes, itemsPerByte);
        if (itemsAtDisplayScale == Long.MAX_VALUE) return Long.MAX_VALUE;

        // Then multiply by the byte multiplier
        return CellMathHelper.multiplyWithOverflowProtection(itemsAtDisplayScale, multiplier);
    }

    /**
     * Get bytes per type in display units (before multiplier).
     * Calculates from cell type's multiplied value to avoid hardcoding.
     */
    private long getDisplayBytesPerType() {
        long multipliedBytesPerType = cellType.getBytesPerType(cellStack);
        long multiplier = cellType.getByteMultiplier();

        if (multiplier <= 0) return multipliedBytesPerType;

        return multipliedBytesPerType / multiplier;
    }

    /**
     * Get the stored item data for a specific item from NBT.
     */
    private long getStoredCount(IAEItemStack item) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        String key = getItemKey(item);

        if (!itemsTag.hasKey(key)) return 0;

        return loadLongFromTag(itemsTag.getCompoundTag(key));
    }

    /**
     * Set the stored count for a specific item in NBT.
     */
    private void setStoredCount(IAEItemStack item, long count) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        String key = getItemKey(item);

        if (count <= 0) {
            itemsTag.removeTag(key);
        } else {
            NBTTagCompound itemTag = new NBTTagCompound();
            item.getDefinition().writeToNBT(itemTag);
            saveLongToTag(itemTag, count);
            itemsTag.setTag(key, itemTag);
        }

        tagCompound.setTag(NBT_ITEM_TYPE, itemsTag);
    }

    /**
     * Generate a unique key for an item stack.
     */
    private String getItemKey(IAEItemStack item) {
        ItemStack def = item.getDefinition();

        return def.getItem().getRegistryName() + "@" + def.getMetadata();
    }

    /**
     * Check if the cell can accept this item (not blacklisted).
     */
    private boolean canAcceptItem(IAEItemStack item) {
        return !cellType.isBlackListed(cellStack, item);
    }

    /**
     * Check if the cell has an Overflow Card installed.
     * When installed, excess items are voided instead of rejected.
     */
    private boolean hasOverflowCard() {
        return cachedHasOverflowCard;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        long benchStart = CellBenchmark.start();
        long subStart;

        if (input == null || input.getStackSize() <= 0) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            return null;
        }

        // Blacklisted items are always rejected
        subStart = CellBenchmark.start();
        boolean canAccept = canAcceptItem(input);
        CellBenchmark.HYPER_CAN_ACCEPT.record(subStart);

        if (!canAccept) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            return input;
        }

        subStart = CellBenchmark.start();
        long existingCount = getStoredCount(input);
        CellBenchmark.HYPER_GET_STORED_COUNT.record(subStart);

        boolean isNewType = existingCount == 0;

        // Check if we can add a new type (respecting Equal Distribution limit)
        // New types beyond limit are rejected
        int maxTypes = getEffectiveMaxTypes();

        if (isNewType && storedTypes >= maxTypes) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            return input;
        }

        // Calculate available capacity
        subStart = CellBenchmark.start();
        // If this is a new type, we need to account for its overhead upfront
        long capacity = isNewType ? getTotalItemCapacityWithExtraType() : getTotalItemCapacity();
        long available = capacity - storedItemCount;
        CellBenchmark.HYPER_CAPACITY_CALC.record(subStart);

        // Overflow card voids items of types already stored in the cell
        boolean canVoidOverflow = hasOverflowCard() && !isNewType;

        if (available <= 0) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            if (canVoidOverflow) return null;

            return input;
        }

        long toInsert = Math.min(input.getStackSize(), available);

        // If Equal Distribution is active, limit per-type storage
        long perTypeLimit = getPerTypeCapacity();
        if (perTypeLimit < Long.MAX_VALUE) {
            long typeAvailable = perTypeLimit - existingCount;

            if (typeAvailable <= 0) {
                CellBenchmark.HYPER_INJECT.record(benchStart);

                if (canVoidOverflow) return null;

                return input;
            }

            toInsert = Math.min(toInsert, typeAvailable);
        }

        if (mode == Actionable.MODULATE) {
            if (isNewType) storedTypes++;

            subStart = CellBenchmark.start();
            setStoredCount(input, CellMathHelper.addWithOverflowProtection(existingCount, toInsert));
            CellBenchmark.HYPER_SET_STORED_COUNT.record(subStart);

            storedItemCount = CellMathHelper.addWithOverflowProtection(storedItemCount, toInsert);

            subStart = CellBenchmark.start();
            saveChanges();
            CellBenchmark.HYPER_SAVE_CHANGES.record(subStart);
        }

        // All items inserted successfully
        if (toInsert >= input.getStackSize()) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            return null;
        }

        // Overflow card voids the remainder
        if (canVoidOverflow) {
            CellBenchmark.HYPER_INJECT.record(benchStart);

            return null;
        }

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(CellMathHelper.subtractWithUnderflowProtection(input.getStackSize(), toInsert));

        CellBenchmark.HYPER_INJECT.record(benchStart);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        long benchStart = CellBenchmark.start();
        long subStart;

        if (request == null || request.getStackSize() <= 0) {
            CellBenchmark.HYPER_EXTRACT.record(benchStart);

            return null;
        }

        subStart = CellBenchmark.start();
        long existingCount = getStoredCount(request);
        CellBenchmark.HYPER_GET_STORED_COUNT.record(subStart);

        if (existingCount <= 0) {
            CellBenchmark.HYPER_EXTRACT.record(benchStart);

            return null;
        }

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;

            subStart = CellBenchmark.start();
            setStoredCount(request, newCount);
            CellBenchmark.HYPER_SET_STORED_COUNT.record(subStart);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedItemCount = Math.max(0, storedItemCount - toExtract);

            subStart = CellBenchmark.start();
            saveChanges();
            CellBenchmark.HYPER_SAVE_CHANGES.record(subStart);
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        CellBenchmark.HYPER_EXTRACT.record(benchStart);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        long benchStart = CellBenchmark.start();

        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);

        for (String key : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(key);
            ItemStack stack = new ItemStack(itemTag);
            if (stack.isEmpty()) continue;

            long count = loadLongFromTag(itemTag);
            if (count <= 0) continue;

            IAEItemStack aeStack = channel.createStack(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
            }
        }

        CellBenchmark.HYPER_GET_AVAILABLE.record(benchStart);

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
        // Return display bytes per type for AE2 display purposes
        // The actual multiplied value would overflow int
        return (int) Math.min(getDisplayBytesPerType(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return storedTypes < getEffectiveMaxTypes() && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        // Return display bytes for AE2 display
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
        return getEffectiveMaxTypes();
    }

    @Override
    public long getStoredItemCount() {
        return storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return storedTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        return getEffectiveMaxTypes() - storedTypes;
    }

    @Override
    public long getUsedBytes() {
        if (storedItemCount == 0 && storedTypes == 0) return 0;

        long totalBytes = getTotalBytes();
        long usedForTypes = storedTypes * getDisplayBytesPerType();

        // When Equal Distribution is active, calculate bytes based on the per-type ratio
        // to ensure each type shows as using exactly 1/n of the available space when full.
        // The formula is: usedBytes = (storedItemCount / perTypeCapacity) * (availableBytes / n)
        // Rewritten to avoid overflow: (storedItemCount * availableBytes) / (perTypeCapacity * n)
        // Which equals: storedItemCount * availableBytes / totalCapacity
        // But totalCapacity can overflow, so we compute: (storedItemCount / n) / perTypeCapacity * availableBytes
        if (equalDistributionLimit > 0) {
            int n = equalDistributionLimit;
            long perType = getPerTypeCapacity();
            long typeBytesDisplay = (long) n * getDisplayBytesPerType();
            long availableBytes = totalBytes - typeBytesDisplay;

            if (availableBytes <= 0 || perType <= 0) {
                return usedForTypes;
            }

            // Calculate usedForItems = storedItemCount * availableBytes / (perType * n)
            // Rewrite as: (storedItemCount / n) * availableBytes / perType
            // to avoid overflow in (perType * n)
            double storedPerN = (double) storedItemCount / n;
            double usedForItemsDouble = (storedPerN / perType) * availableBytes;
            long usedForItems = Math.max(storedItemCount > 0 ? 1 : 0, (long) usedForItemsDouble);

            return CellMathHelper.addWithOverflowProtection(usedForItems, usedForTypes);
        }

        long capacity = getTotalItemCapacity();
        long availableBytes = totalBytes - usedForTypes;

        // If capacity overflowed to Long.MAX_VALUE, scale bytes proportionally
        // This ensures the cell shows as full when we've stored the max trackable amount
        if (capacity == Long.MAX_VALUE) {
            // At max capacity, scale linearly relative to available bytes (after type overhead)
            double ratio = (double) storedItemCount / (double) Long.MAX_VALUE;
            long usedForItems = Math.max(storedItemCount > 0 ? 1 : 0, (long) (availableBytes * ratio));

            return CellMathHelper.addWithOverflowProtection(usedForItems, usedForTypes);
        }

        // Normal case: capacity fits in long, calculate directly
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long itemsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(itemsPerByte, multiplier);
        if (itemsPerDisplayByte == 0) itemsPerDisplayByte = 1;

        // Overflow-safe ceiling division for items
        long usedForItems = (storedItemCount == 0) ? 0 : (storedItemCount - 1) / itemsPerDisplayByte + 1;

        return CellMathHelper.addWithOverflowProtection(usedForItems, usedForTypes);
    }

    @Override
    public long getRemainingItemCount() {
        long capacity = getTotalItemCapacity();
        return Math.max(0, capacity - storedItemCount);
    }

    @Override
    public int getUnusedItemCount() {
        // Fractional items that don't fill a byte (in display scale)
        // This represents how many more items can fit before consuming another display byte
        int itemsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long itemsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(itemsPerByte, multiplier);

        if (itemsPerDisplayByte == 0) return 0;

        // Calculate how many items would round up to the current used bytes
        // usedBytes (for items only) = ceil(storedItemCount / itemsPerDisplayByte)
        long usedBytesForItems = getUsedBytes() - storedTypes * getDisplayBytesPerType();
        if (usedBytesForItems <= 0) return 0;

        // Unused = capacity - actual stored
        long fullItems = CellMathHelper.multiplyWithOverflowProtection(usedBytesForItems, itemsPerDisplayByte);
        long unused = fullItems - storedItemCount;
        if (unused < 0) unused = 0; // Safety: should not happen with correct math

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (storedItemCount == 0 && storedTypes == 0) return 4; // Empty
        if (canHoldNewItem()) return 1;                          // Has space for new types
        if (getRemainingItemCount() > 0) return 2;               // Has space for more of existing

        return 3; // Full
    }

    @Override
    public void persist() {
        saveToNBT();
    }
}
