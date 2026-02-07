package com.cells.cells.hyperdensity.fluid;

import javax.annotation.Nonnull;

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

import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;


/**
 * Inventory implementation for hyper-density fluid storage cells.
 * 
 * This inventory handles the internal byte multiplier, ensuring all calculations
 * are overflow-safe. The display shows standard byte values (1k, 4k, etc.)
 * but internally stores vastly more.
 * 
 * Key overflow protection points:
 * - All capacity calculations use CellMathHelper.multiplyWithOverflowProtection
 * - Storage is tracked in a way that avoids overflow during fluid operations
 * - Division is preferred over multiplication where possible
 * 
 * When an Equal Distribution Card is installed, this cell operates in a special mode:
 * - The type limit is reduced to the card's value
 * - The total capacity is divided equally among those types
 * - Each type can only store up to its allocated share
 */
public class FluidHyperDensityCellInventory implements ICellInventory<IAEFluidStack> {

    private static final String NBT_STORED_FLUID_COUNT = "StoredFluidCount";
    private static final String NBT_FLUID_TYPE = "fluidType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEFluidStack> channel;
    private final IItemFluidHyperDensityCell cellType;

    private final NBTTagCompound tagCompound;

    private long storedFluidCount = 0;
    private int storedTypes = 0;

    // Cached upgrade card states
    private int equalDistributionLimit = 0;
    private boolean cachedHasOverflowCard = false;

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
    }

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
     * - Per-type capacity = (Total available * unitsPerByte * multiplier) / N
     * 
     * To avoid overflow while maintaining precision, we use overflow-safe
     * division that handles the case where the numerator would overflow.
     */
    private long getPerTypeCapacity() {
        if (equalDistributionLimit <= 0) return Long.MAX_VALUE;

        int n = equalDistributionLimit;
        long displayBytes = cellType.getDisplayBytes(cellStack);
        long multiplier = cellType.getByteMultiplier();
        int unitsPerByte = channel.getUnitsPerByte();

        long typeBytesDisplay = (long) n * getDisplayBytesPerType();
        long availableDisplayBytes = displayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        // Use overflow-safe division: (a * b * c) / n
        return CellMathHelper.multiplyThenDivide(availableDisplayBytes, unitsPerByte, multiplier, n);
    }

    private void loadFromNBT() {
        storedFluidCount = tagCompound.getLong(NBT_STORED_FLUID_COUNT);

        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        storedTypes = 0;
        for (String key : fluidsTag.getKeySet()) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(key);
            if (fluidTag.getLong(NBT_STORED_COUNT) > 0) storedTypes++;
        }
    }

    private long loadLongFromTag(NBTTagCompound tag) {
        return tag.getLong(NBT_STORED_COUNT);
    }

    private void saveLongToTag(NBTTagCompound tag, long value) {
        tag.setLong(NBT_STORED_COUNT, value);
    }

    private void saveToNBT() {
        tagCompound.setLong(NBT_STORED_FLUID_COUNT, storedFluidCount);
    }

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
     * 
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
            long perType = getPerTypeCapacity();
            return CellMathHelper.multiplyWithOverflowProtection(perType, equalDistributionLimit);
        }

        long displayBytes = cellType.getDisplayBytes(cellStack);
        long multiplier = cellType.getByteMultiplier();
        int unitsPerByte = channel.getUnitsPerByte();

        long typeBytesDisplay = (long) typeCount * getDisplayBytesPerType();
        long availableDisplayBytes = displayBytes - typeBytesDisplay;

        if (availableDisplayBytes <= 0) return 0;

        long unitsAtDisplayScale = CellMathHelper.multiplyWithOverflowProtection(availableDisplayBytes, unitsPerByte);
        if (unitsAtDisplayScale == Long.MAX_VALUE) return Long.MAX_VALUE;

        return CellMathHelper.multiplyWithOverflowProtection(unitsAtDisplayScale, multiplier);
    }

    /**
     * Get bytes per type in display units (before multiplier).
     */
    private long getDisplayBytesPerType() {
        long multipliedBytesPerType = cellType.getBytesPerType(cellStack);
        long multiplier = cellType.getByteMultiplier();

        if (multiplier <= 0) return multipliedBytesPerType;

        return multipliedBytesPerType / multiplier;
    }

    private long getStoredCount(IAEFluidStack fluid) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        String key = getFluidKey(fluid);

        if (!fluidsTag.hasKey(key)) return 0;

        return loadLongFromTag(fluidsTag.getCompoundTag(key));
    }

    private void setStoredCount(IAEFluidStack fluid, long count) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        String key = getFluidKey(fluid);

        if (count <= 0) {
            fluidsTag.removeTag(key);
        } else {
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.getFluidStack().writeToNBT(fluidTag);
            saveLongToTag(fluidTag, count);
            fluidsTag.setTag(key, fluidTag);
        }

        tagCompound.setTag(NBT_FLUID_TYPE, fluidsTag);
    }

    private String getFluidKey(IAEFluidStack fluid) {
        FluidStack fs = fluid.getFluidStack();

        return fs.getFluid().getName();
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

        long existingCount = getStoredCount(input);
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

            setStoredCount(input, CellMathHelper.addWithOverflowProtection(existingCount, toInsert));
            storedFluidCount = CellMathHelper.addWithOverflowProtection(storedFluidCount, toInsert);
            saveChanges();
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

        long existingCount = getStoredCount(request);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, newCount);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedFluidCount = Math.max(0, storedFluidCount - toExtract);
            saveChanges();
        }

        IAEFluidStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);

        for (String key : fluidsTag.getKeySet()) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(key);
            FluidStack fs = FluidStack.loadFluidStackFromNBT(fluidTag);
            if (fs == null) continue;

            long count = loadLongFromTag(fluidTag);
            if (count <= 0) continue;

            IAEFluidStack aeStack = channel.createStack(fs);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
            }
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

        int unitsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long unitsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(unitsPerByte, multiplier);
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
        int unitsPerByte = channel.getUnitsPerByte();
        long multiplier = cellType.getByteMultiplier();
        long unitsPerDisplayByte = CellMathHelper.multiplyWithOverflowProtection(unitsPerByte, multiplier);

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

    @Override
    public void persist() {
        saveToNBT();
    }
}
