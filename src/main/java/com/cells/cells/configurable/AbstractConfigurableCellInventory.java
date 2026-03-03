package com.cells.cells.configurable;

import java.util.Arrays;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.items.contents.CellConfig;
import appeng.util.Platform;

import com.cells.cells.common.INBTSizeProvider;
import com.cells.config.CellsConfig;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;
import com.cells.util.DeferredCellOperations;
import com.cells.util.NBTSizeHelper;


/**
 * Abstract base class for Configurable Storage Cell inventories.
 * <p>
 * Provides common implementations for ICellInventory methods shared across
 * all channel types (item, fluid, essentia, gas). Subclasses implement
 * channel-specific logic for inject/extract/serialize operations.
 * <p>
 * This cell has equal distribution built-in: the capacity is divided equally
 * among maxTypes types. The user can additionally set a lower per-type limit
 * via the GUI text field.
 *
 * @param <T> The AE stack type for this channel
 */
public abstract class AbstractConfigurableCellInventory<T extends IAEStack<T>> implements ICellInventory<T>, INBTSizeProvider {

    protected final ItemStack cellStack;
    protected final ISaveProvider container;
    protected final ComponentInfo componentInfo;
    protected final NBTTagCompound tagCompound;

    // Cached values
    protected final int maxTypes;
    protected final long userMaxPerType;
    protected final long physicalPerType;
    protected final long effectivePerType;
    protected final boolean hasOverflowCard;

    // Storage tracking
    protected long storedCount = 0;
    protected int storedTypes = 0;

    // NBT size tracking - updated by subclasses
    protected int totalNbtSize = 0;

    protected AbstractConfigurableCellInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        this.cellStack = cellStack;
        this.container = container;
        this.componentInfo = componentInfo;
        this.tagCompound = Platform.openNbtData(cellStack);

        // Use effective max types (min of user limit and config limit)
        this.maxTypes = ComponentHelper.getEffectiveMaxTypes(cellStack, componentInfo.getChannelType());
        this.userMaxPerType = ComponentHelper.getMaxPerType(cellStack);
        this.physicalPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(componentInfo, maxTypes);
        this.effectivePerType = Math.min(userMaxPerType, physicalPerType);

        IItemHandler upgrades = getUpgradesInventory();
        this.hasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);
    }

    /**
     * Returns the total capacity of this cell (effectivePerType * maxTypes).
     */
    protected long getTotalCapacity() {
        return effectivePerType * maxTypes;
    }

    /**
     * Schedule a deferred save operation.
     */
    protected void saveChangesDeferred() {
        DeferredCellOperations.markDirty(this, container);
    }

    // =====================
    // ICellInventory implementation - common methods
    // =====================

    @Override
    public abstract IStorageChannel<T> getChannel();

    @Override
    public ItemStack getItemStack() {
        return cellStack;
    }

    @Override
    public double getIdleDrain() {
        return CellsConfig.configurableCellIdleDrain;
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        String fz = Platform.openNbtData(cellStack).getString("FuzzyMode");

        try {
            return FuzzyMode.valueOf(fz);
        } catch (Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public IItemHandler getConfigInventory() {
        return new CellConfig(cellStack);
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return new CustomCellUpgrades(cellStack, 2, Arrays.asList(
            CustomCellUpgrades.CustomUpgrades.OVERFLOW
        ));
    }

    @Override
    public int getBytesPerType() {
        return (int) Math.min(componentInfo.getBytesPerType(), Integer.MAX_VALUE);
    }

    @Override
    public boolean canHoldNewItem() {
        return storedTypes < maxTypes && effectivePerType > 0;
    }

    @Override
    public long getTotalBytes() {
        return componentInfo.getBytes();
    }

    @Override
    public long getFreeBytes() {
        return Math.max(0, getTotalBytes() - getUsedBytes());
    }

    @Override
    public long getTotalItemTypes() {
        return maxTypes;
    }

    @Override
    public long getStoredItemCount() {
        return storedCount;
    }

    @Override
    public long getStoredItemTypes() {
        return storedTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        return maxTypes - storedTypes;
    }

    @Override
    public long getRemainingItemCount() {
        return Math.max(0, getTotalCapacity() - storedCount);
    }

    @Override
    public int getStatusForCell() {
        if (storedCount == 0 && storedTypes == 0) return 4; // Empty but valid
        if (canHoldNewItem()) return 1; // Has space for new types
        if (getRemainingItemCount() > 0) return 2; // Has space for more of existing

        return 3; // Full
    }

    @Override
    public void persist() {
        // Individual counts are saved in setStoredCount() of subclasses
    }

    /**
     * Get the count per bit for this channel type.
     * Discrete channels (item, essentia) have countPerBit = 1.
     * Volumetric channels have higher values (fluid = 1000, gas = 4000).
     */
    protected int getCountPerBit() {
        return componentInfo.getChannelType().getCountPerBit();
    }

    @Override
    public long getUsedBytes() {
        if (storedCount == 0 && storedTypes == 0) return 0;

        // Type overhead is consistent across all channel types
        long usedForTypes = (long) storedTypes * componentInfo.getBytesPerType();

        int countPerBit = getCountPerBit();

        if (countPerBit > 1) {
            // Volumetric (fluid, gas): type overhead + proportional content usage
            long totalCapacity = getTotalCapacity();
            if (totalCapacity <= 0) return usedForTypes;

            // Bytes available for content after reserving space for all type slots
            long maxTypeOverhead = (long) maxTypes * componentInfo.getBytesPerType();
            long availableBytesForContent = getTotalBytes() - maxTypeOverhead;
            if (availableBytesForContent <= 0) return usedForTypes;

            // Proportional usage of content bytes based on fill ratio
            long usedForContent = (storedCount * availableBytesForContent) / totalCapacity;

            return usedForTypes + usedForContent;
        }

        // Discrete (item, essentia): type overhead + item bytes
        long usedForItems = (storedCount == 0) ? 0 : (storedCount - 1) / 8 + 1;

        return usedForItems + usedForTypes;
    }

    @Override
    public int getUnusedItemCount() {
        if (getCountPerBit() > 1) return 0;  // No byte rounding

        // Discrete: fractional items not filling a byte
        long usedBytesForItems = getUsedBytes() - (long) storedTypes * componentInfo.getBytesPerType();
        if (usedBytesForItems <= 0) return 0;

        long fullItems = usedBytesForItems * 8;
        long unused = fullItems - storedCount;
        if (unused < 0) return 0;

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    /**
     * Get the total NBT size of all stored items/fluids in bytes.
     * Used for tooltip display and warning when approaching limits.
     *
     * @return Total NBT size in bytes
     */
    public int getTotalNbtSize() {
        return totalNbtSize;
    }

    // =====================
    // Abstract methods to be implemented by subclasses
    // =====================

    /**
     * Load stored data from NBT into memory cache.
     */
    protected abstract void loadFromNBT();
}
