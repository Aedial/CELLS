package com.cells.cells.hyperdensity.fluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.api.networking.security.IActionSource;
import appeng.util.Platform;

import com.cells.cells.common.INBTSizeProvider;
import com.cells.config.CellsConfig;
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.DeferredCellOperations;
import com.cells.util.FluidStackKey;
import com.cells.util.NBTSizeHelper;


/**
 * Inventory implementation for hyper-density fluid storage cells.
 * <p>
 * This inventory handles the internal byte multiplier, ensuring all calculations
 * are overflow-safe. The display shows standard byte values (1k, 4k, etc.)
 * but internally stores vastly more.
 * <p>
 * Key overflow protection points:
 * - All capacity calculations use CellMathHelper.multiplyWithOverflowProtection
 * - Storage is tracked in a way that avoids overflow during fluid operations
 * - Division is preferred over multiplication where possible
 * <p>
 * When an Equal Distribution Card is installed, this cell operates in a special mode:
 * - The type limit is reduced to the card's value
 * - The total capacity is divided equally among those types
 * - Each type can only store up to its allocated share
 */
public class FluidHyperDensityCellInventory implements ICellInventory<IAEFluidStack>, INBTSizeProvider {

    private static final String NBT_FLUID_TYPE = "fluidType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEFluidStack> channel;
    private final IItemFluidHyperDensityCell cellType;

    private final NBTTagCompound tagCompound;

    // In-memory cache: FluidStackKey -> NBT index for O(1) lookups
    private final Map<FluidStackKey, Integer> keyToNbtIndex = new HashMap<>();
    // Cached AEFluidStacks by NBT index, avoids expensive reconstruction in getAvailableItems
    private final Map<Integer, IAEFluidStack> nbtIndexToFluidStack = new HashMap<>();
    // Cached counts by NBT index, avoids NBT reads in getAvailableItems and getStoredCount
    private final Map<Integer, Long> nbtIndexToCount = new HashMap<>();

    // Next available NBT index for new fluids (computed on load, updated on insert)
    private int cachedNextIndex = 0;

    private long storedFluidCount = 0;
    private int storedTypes = 0;

    // Cached upgrade card states
    private int equalDistributionLimit = 0;
    private boolean cachedHasOverflowCard = false;

    // NBT size tracking - per-fluid sizes for incremental updates
    private final Map<FluidStackKey, Integer> fluidNbtSizes = new HashMap<>();
    private int totalNbtSize = 0;

    // Cached immutable capacity components — computed once at construction to avoid
    // repeated virtual dispatch through cellType/channel on every inject/extract call.
    private final long cachedDisplayBytes;
    private final long cachedMultiplier;
    private final int cachedUnitsPerByte;
    private final long cachedDisplayBytesPerType;
    private final long cachedPerTypeCapacity;
    private final int cachedEffectiveMaxTypes;

    public FluidHyperDensityCellInventory(IItemFluidHyperDensityCell cellType, ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.cellType = cellType;
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);

        loadFromNBT();

        IItemHandler upgrades = cellType.getUpgradesInventory(cellStack);
        equalDistributionLimit = CellUpgradeHelper.getEqualDistributionLimit(upgrades);
        cachedHasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);

        // Cache immutable capacity components, these depend only on cell type, cell stack,
        // and upgrade cards, all of which are fixed for the lifetime of this inventory.
        this.cachedDisplayBytes = cellType.getDisplayBytes(cellStack);
        this.cachedMultiplier = cellType.getByteMultiplier();
        this.cachedUnitsPerByte = channel.getUnitsPerByte();

        long bytesPerType = cellType.getBytesPerType(cellStack);
        this.cachedDisplayBytesPerType = (cachedMultiplier <= 0) ? bytesPerType : bytesPerType / cachedMultiplier;

        int maxTypes = cellType.getMaxTypes();
        this.cachedEffectiveMaxTypes = (equalDistributionLimit > 0) ? Math.min(equalDistributionLimit, maxTypes) : maxTypes;

        if (equalDistributionLimit <= 0) {
            this.cachedPerTypeCapacity = Long.MAX_VALUE;
        } else {
            int n = equalDistributionLimit;
            long typeBytesDisplay = (long) n * cachedDisplayBytesPerType;
            long availableDisplayBytes = cachedDisplayBytes - typeBytesDisplay;
            this.cachedPerTypeCapacity = (availableDisplayBytes <= 0) ? 0
                : CellMathHelper.multiplyThenDivide(availableDisplayBytes, cachedUnitsPerByte, cachedMultiplier, n);
        }
    }

    private int getEffectiveMaxTypes() {
        return cachedEffectiveMaxTypes;
    }

    /**
     * Get the per-type capacity limit when Equal Distribution is active.
     * Returns Long.MAX_VALUE if Equal Distribution is not active.
     * <p>
     * When Equal Distribution is active, the total capacity must be divided
     * among N types, and each type consumes bytesPerType overhead. So:
     * - Total available = totalBytes - (N * bytesPerType)
     * - Per-type capacity = (Total available * unitsPerByte * multiplier) / N
     * <p>
     * To avoid overflow while maintaining precision, we use overflow-safe
     * division that handles the case where the numerator would overflow.
     */
    private long getPerTypeCapacity() {
        return cachedPerTypeCapacity;
    }

    private void loadFromNBT() {
        // Derive counts from actual stored fluids and build in-memory cache
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        storedFluidCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        nbtIndexToFluidStack.clear();
        nbtIndexToCount.clear();
        fluidNbtSizes.clear();
        totalNbtSize = 0;
        cachedNextIndex = 0;

        // Collect all keys upfront to avoid ConcurrentModificationException
        List<String> allKeys = new ArrayList<>(fluidsTag.getKeySet());

        // First pass: find highest numeric index to avoid collisions during migration
        for (String nbtKey : allKeys) {
            if (isNumericKey(nbtKey)) {
                int index = Integer.parseInt(nbtKey);
                if (index >= cachedNextIndex) cachedNextIndex = index + 1;
            }
        }

        // Track legacy keys that need migration
        List<String> legacyKeys = new ArrayList<>();

        // Second pass: load fluids and migrate legacy keys
        for (String nbtKey : allKeys) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(nbtKey);
            long count = fluidTag.getLong(NBT_STORED_COUNT);

            if (count > 0) {
                // Reconstruct the FluidStack and create a key for the cache
                FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidTag);
                FluidStackKey key = FluidStackKey.of(stack);

                if (key != null) {
                    // Check if this is a legacy string key that needs migration
                    boolean isLegacy = !isNumericKey(nbtKey);
                    int index;

                    if (isLegacy) {
                        // Assign new numeric index and mark for migration
                        index = cachedNextIndex++;
                        legacyKeys.add(nbtKey);

                        // Write fluid under new numeric key
                        fluidsTag.setTag(String.valueOf(index), fluidTag.copy());
                    } else {
                        index = Integer.parseInt(nbtKey);
                    }

                    keyToNbtIndex.put(key, index);
                    nbtIndexToFluidStack.put(index, channel.createStack(stack));
                    nbtIndexToCount.put(index, count);
                    storedFluidCount = CellMathHelper.addWithOverflowProtection(storedFluidCount, count);
                    storedTypes++;

                    // Track NBT size for this fluid (if enabled)
                    if (CellsConfig.enableNbtSizeTooltip) {
                        int fluidSize = NBTSizeHelper.calculateSize(fluidTag);
                        fluidNbtSizes.put(key, fluidSize);
                        totalNbtSize += fluidSize;
                    }
                }
            }
        }

        // Remove legacy keys after migration
        for (String legacyKey : legacyKeys) fluidsTag.removeTag(legacyKey);

        // Persist migration if any keys were converted
        if (!legacyKeys.isEmpty()) tagCompound.setTag(NBT_FLUID_TYPE, fluidsTag);
    }

    /**
     * Check if a key is a valid numeric index (new format).
     */
    private boolean isNumericKey(String key) {
        if (key == null || key.isEmpty()) return false;

        for (int i = 0; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) return false;
        }

        return true;
    }

    private void saveLongToTag(NBTTagCompound tag, long value) {
        tag.setLong(NBT_STORED_COUNT, value);
    }

    private void saveToNBT() {
        // Individual fluid counts are saved in setStoredCount()
        // Total count is derived on load - no separate tracking needed
    }

    /**
     * Save changes and notify container - deferred to end of tick for efficiency.
     * This is the hot path for inject/extract operations.
     */
    private void saveChangesDeferred() {
        saveToNBT();
        DeferredCellOperations.markDirty(this, container);
    }

    /**
     * Save all changes immediately.
     * Used for initialization or when immediate persistence is required.
     */
    private void saveChanges() {
        saveToNBT();
        if (container != null) container.saveChanges(this);
    }

    private long getTotalFluidCapacity() {
        return getTotalFluidCapacityForTypes(storedTypes);
    }

    private long getTotalFluidCapacityWithExtraType() {
        return getTotalFluidCapacityForTypes(storedTypes + 1);
    }

    /**
     * Get the total capacity in fluid units for a given number of types.
     * <p>
     * When Equal Distribution is active, we always reserve overhead for ALL N types,
     * regardless of how many are currently stored. This ensures each type gets a fair
     * and consistent share of the capacity. The total is derived from perTypeCapacity * N
     * to ensure consistency between the two calculations.
     * 
     * @param typeCount The number of types to account for in overhead calculation
     *                  (ignored when Equal Distribution is active)
     */
    private long getTotalFluidCapacityForTypes(int typeCount) {
        // When Equal Distribution is active, derive total from per-type to ensure consistency
        if (equalDistributionLimit > 0) {
            return CellMathHelper.multiplyWithOverflowProtection(cachedPerTypeCapacity, equalDistributionLimit);
        }

        // Calculate type overhead in display bytes, then multiply
        long typeBytesDisplay = (long) typeCount * cachedDisplayBytesPerType;
        long availableDisplayBytes = cachedDisplayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        long unitsAtDisplayScale = CellMathHelper.multiplyWithOverflowProtection(availableDisplayBytes, cachedUnitsPerByte);
        if (unitsAtDisplayScale == Long.MAX_VALUE) return Long.MAX_VALUE;

        return CellMathHelper.multiplyWithOverflowProtection(unitsAtDisplayScale, cachedMultiplier);
    }

    /**
     * Get bytes per type in display units (before multiplier).
     */
    private long getDisplayBytesPerType() {
        return cachedDisplayBytesPerType;
    }

    /**
     * Get the stored fluid count using a pre-computed key.
     * Avoids creating a new FluidStackKey (which deep-copies NBT) on every call.
     */
    private long getStoredCount(FluidStackKey key) {
        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        return nbtIndexToCount.getOrDefault(index, 0L);
    }

    /**
     * Set the stored count for a specific fluid in NBT using a pre-computed key.
     * Only serializes the full fluid on first insert; subsequent updates only change the count.
     * Also tracks NBT size for tooltip display.
     *
     * @param fluid The AE fluid stack (needed for serialization of new fluids)
     * @param key   Pre-computed FluidStackKey to avoid duplicate NBT deep-copy
     * @param count The new count to set
     */
    private void setStoredCount(IAEFluidStack fluid, FluidStackKey key, long count) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            // Remove the fluid entirely
            if (index != null) {
                // Subtract this fluid's NBT size from total
                Integer oldSize = fluidNbtSizes.remove(key);
                if (oldSize != null) totalNbtSize -= oldSize;

                fluidsTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
                nbtIndexToFluidStack.remove(index);
                nbtIndexToCount.remove(index);
            }
        } else if (index != null) {
            // Fluid already exists - just update the count (no re-serialization needed)
            // NBT size change is minimal (just the count value), don't recalculate
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(String.valueOf(index));
            saveLongToTag(fluidTag, count);
            nbtIndexToCount.put(index, count);
        } else {
            // New fluid - serialize fully and assign a new sequential index
            index = cachedNextIndex++;
            String nbtKey = String.valueOf(index);

            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.getFluidStack().writeToNBT(fluidTag);
            saveLongToTag(fluidTag, count);
            fluidsTag.setTag(nbtKey, fluidTag);
            nbtIndexToFluidStack.put(index, fluid.copy());
            nbtIndexToCount.put(index, count);

            keyToNbtIndex.put(key, index);

            // Track NBT size for this new fluid (if enabled)
            if (CellsConfig.enableNbtSizeTooltip) {
                int fluidSize = NBTSizeHelper.calculateSize(fluidTag);
                fluidNbtSizes.put(key, fluidSize);
                totalNbtSize += fluidSize;
            }

            // Only set the parent tag reference when adding a new fluid — the compound may
            // not be in the parent yet (first fluid ever). For updates/removals, the compound
            // is already referenced from the parent and modifications are reflected automatically.
            tagCompound.setTag(NBT_FLUID_TYPE, fluidsTag);
        }
    }

    private boolean canAcceptFluid(IAEFluidStack fluid) {
        return !cellType.isBlackListed(cellStack, fluid);
    }

    private boolean hasOverflowCard() {
        return cachedHasOverflowCard;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        // Blacklisted fluids are always rejected
        if (!canAcceptFluid(input)) return input;

        // Compute key once to avoid duplicate NBT deep-copy in getStoredCount + setStoredCount
        FluidStackKey key = FluidStackKey.of(input.getFluidStack());
        if (key == null) return input;

        long existingCount = getStoredCount(key);
        boolean isNewType = existingCount == 0;

        // New types beyond limit are rejected
        int maxTypes = getEffectiveMaxTypes();
        if (isNewType && storedTypes >= maxTypes) return input;

        // Overflow card voids fluids of types already stored in the cell
        boolean canVoidOverflow = hasOverflowCard() && !isNewType;

        long capacity = isNewType ? getTotalFluidCapacityWithExtraType() : getTotalFluidCapacity();
        long available = capacity - storedFluidCount;

        if (available <= 0) {
            if (canVoidOverflow) return null;

            return input;
        }

        long toInsert = Math.min(input.getStackSize(), available);

        long perTypeLimit = getPerTypeCapacity();
        if (perTypeLimit < Long.MAX_VALUE) {
            long typeAvailable = perTypeLimit - existingCount;

            if (typeAvailable <= 0) {
                if (canVoidOverflow) return null;

                return input;
            }

            toInsert = Math.min(toInsert, typeAvailable);
        }

        if (mode == Actionable.MODULATE) {
            if (isNewType) storedTypes++;

            setStoredCount(input, key, CellMathHelper.addWithOverflowProtection(existingCount, toInsert));
            storedFluidCount = CellMathHelper.addWithOverflowProtection(storedFluidCount, toInsert);
            saveChangesDeferred();
        }

        if (toInsert >= input.getStackSize()) return null;

        // Void remainder if it's an existing type
        if (canVoidOverflow) return null;

        IAEFluidStack remainder = input.copy();
        remainder.setStackSize(CellMathHelper.subtractWithUnderflowProtection(input.getStackSize(), toInsert));

        return remainder;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        // Compute key once to avoid duplicate NBT deep-copy in getStoredCount + setStoredCount
        FluidStackKey key = FluidStackKey.of(request.getFluidStack());
        if (key == null) return null;

        long existingCount = getStoredCount(key);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, key, newCount);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedFluidCount = Math.max(0, storedFluidCount - toExtract);
            saveChangesDeferred();
        }

        IAEFluidStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        for (Map.Entry<Integer, IAEFluidStack> entry : nbtIndexToFluidStack.entrySet()) {
            long count = nbtIndexToCount.getOrDefault(entry.getKey(), 0L);
            if (count <= 0) continue;

            // Copy the cached prototype to avoid mutating it, callers may hold references
            IAEFluidStack aeStack = entry.getValue().copy();
            aeStack.setStackSize(count);
            out.add(aeStack);
        }

        return out;
    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
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
        return (int) Math.min(getDisplayBytesPerType(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return storedTypes < getEffectiveMaxTypes() && getRemainingItemCount() > 0;
    }

    @Override
    public long getTotalBytes() {
        return cellType.getDisplayBytes(cellStack);
    }

    @Override
    public long getFreeBytes() {
        long total = getTotalBytes();
        long used = getUsedBytes();

        return Math.max(0, total - used);
    }

    @Override
    public long getTotalItemTypes() {
        return getEffectiveMaxTypes();
    }

    @Override
    public long getStoredItemCount() {
        return storedFluidCount;
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
        if (storedFluidCount == 0 && storedTypes == 0) return 0;

        long totalBytes = getTotalBytes();
        long usedForTypes = storedTypes * getDisplayBytesPerType();

        // When Equal Distribution is active, calculate bytes based on the per-type ratio
        // to ensure each type shows as using exactly 1/n of the available space when full.
        // The formula is: usedBytes = (storedFluidCount / perTypeCapacity) * (availableBytes / n)
        // Rewritten to avoid overflow: (storedFluidCount / n) / perTypeCapacity * availableBytes
        if (equalDistributionLimit > 0) {
            int n = equalDistributionLimit;
            long perType = getPerTypeCapacity();
            long typeBytesDisplay = (long) n * getDisplayBytesPerType();
            long availableBytes = totalBytes - typeBytesDisplay;

            if (availableBytes <= 0 || perType <= 0) {
                return usedForTypes;
            }

            // Calculate usedForFluids = storedFluidCount * availableBytes / (perType * n)
            // Rewrite as: (storedFluidCount / n) * availableBytes / perType
            // to avoid overflow in (perType * n)
            double storedPerN = (double) storedFluidCount / n;
            double usedForFluidsDouble = (storedPerN / perType) * availableBytes;
            long usedForFluids = Math.max(storedFluidCount > 0 ? 1 : 0, (long) usedForFluidsDouble);

            return CellMathHelper.addWithOverflowProtection(usedForFluids, usedForTypes);
        }

        long capacity = getTotalFluidCapacity();
        long availableBytes = totalBytes - usedForTypes;

        if (capacity == Long.MAX_VALUE) {
            double ratio = (double) storedFluidCount / (double) Long.MAX_VALUE;
            long usedForFluids = Math.max(storedFluidCount > 0 ? 1 : 0, (long) (availableBytes * ratio));

            return CellMathHelper.addWithOverflowProtection(usedForFluids, usedForTypes);
        }

        long unitsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(cachedUnitsPerByte, cachedMultiplier);
        if (unitsPerDisplayByte == 0) unitsPerDisplayByte = 1;

        long usedForFluids = (storedFluidCount == 0) ? 0 : (storedFluidCount - 1) / unitsPerDisplayByte + 1;

        return CellMathHelper.addWithOverflowProtection(usedForFluids, usedForTypes);
    }

    @Override
    public long getRemainingItemCount() {
        long capacity = getTotalFluidCapacity();

        return Math.max(0, capacity - storedFluidCount);
    }

    @Override
    public int getUnusedItemCount() {
        long unitsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(cachedUnitsPerByte, cachedMultiplier);

        if (unitsPerDisplayByte == 0) return 0;

        long usedBytesForFluids = getUsedBytes() - storedTypes * getDisplayBytesPerType();
        if (usedBytesForFluids <= 0) return 0;

        long fullUnits = CellMathHelper.multiplyWithOverflowProtection(usedBytesForFluids, unitsPerDisplayByte);
        long unused = fullUnits - storedFluidCount;
        if (unused < 0) unused = 0;

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (storedFluidCount == 0 && storedTypes == 0) return 4;
        if (canHoldNewItem()) return 1;
        if (getRemainingItemCount() > 0) return 2;

        return 3;
    }

    /**
     * Get the total NBT size of all stored fluids in bytes.
     * Used for tooltip display and warning when approaching limits.
     *
     * @return Total NBT size in bytes
     */
    public int getTotalNbtSize() {
        return totalNbtSize;
    }

    @Override
    public void persist() {
        saveToNBT();
    }
}
