package com.cells.cells.configurable;

import java.util.HashMap;
import java.util.Map;

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
import appeng.items.contents.CellConfig;
import appeng.util.Platform;

import com.cells.config.CellsConfig;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;
import com.cells.util.DeferredCellOperations;
import com.cells.util.ItemStackKey;


/**
 * Item-channel inventory for the Configurable Storage Cell.
 * <p>
 * This cell has equal distribution built-in: the capacity is divided equally
 * among maxTypes types. The user can additionally set a lower per-type limit
 * via the GUI text field.
 * <p>
 * No overflow protection is needed since the maximum component is 2G bytes,
 * and all math stays well within long range.
 */
public class ConfigurableCellItemInventory implements ICellInventory<IAEItemStack> {

    private static final String NBT_ITEM_TYPE = "itemType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEItemStack> channel;
    private final ComponentInfo componentInfo;

    private final NBTTagCompound tagCompound;

    // In-memory cache: ItemStackKey -> NBT index
    private final Map<ItemStackKey, Integer> keyToNbtIndex = new HashMap<>();
    private int cachedNextIndex = 0;

    // Storage tracking
    private long storedItemCount = 0;
    private int storedTypes = 0;

    // Cached values
    private final int maxTypes;
    private final long userMaxPerType;
    private final long physicalPerType;
    private final long effectivePerType;
    private final boolean hasOverflowCard;

    public ConfigurableCellItemInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        this.cellStack = cellStack;
        this.container = container;
        this.componentInfo = componentInfo;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);

        this.maxTypes = CellsConfig.configurableCellMaxTypes;
        this.userMaxPerType = ComponentHelper.getMaxPerType(cellStack);
        this.physicalPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(componentInfo, maxTypes);
        this.effectivePerType = Math.min(userMaxPerType, physicalPerType);

        IItemHandler upgrades = new CustomCellUpgrades(cellStack, 2);
        this.hasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);

        loadFromNBT();
    }

    private void loadFromNBT() {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        storedItemCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        cachedNextIndex = 0;

        for (String nbtKey : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(nbtKey);
            long count = itemTag.getLong(NBT_STORED_COUNT);

            if (count > 0) {
                ItemStack stack = new ItemStack(itemTag);
                ItemStackKey key = ItemStackKey.of(stack);

                if (key != null) {
                    int index = Integer.parseInt(nbtKey);
                    if (index >= cachedNextIndex) cachedNextIndex = index + 1;

                    keyToNbtIndex.put(key, index);
                    storedItemCount += count;
                    storedTypes++;
                }
            }
        }
    }

    private long getStoredCount(IAEItemStack item) {
        ItemStackKey key = ItemStackKey.of(item.getDefinition());
        if (key == null) return 0;

        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        return itemsTag.getCompoundTag(String.valueOf(index)).getLong(NBT_STORED_COUNT);
    }

    private void setStoredCount(IAEItemStack item, long count) {
        ItemStackKey key = ItemStackKey.of(item.getDefinition());
        if (key == null) return;

        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            if (index != null) {
                itemsTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
            }
        } else if (index != null) {
            // Update count only
            NBTTagCompound itemTag = itemsTag.getCompoundTag(String.valueOf(index));
            itemTag.setLong(NBT_STORED_COUNT, count);
        } else {
            // New item - serialize fully
            index = cachedNextIndex++;
            NBTTagCompound itemTag = new NBTTagCompound();
            item.getDefinition().writeToNBT(itemTag);
            itemTag.setLong(NBT_STORED_COUNT, count);
            itemsTag.setTag(String.valueOf(index), itemTag);
            keyToNbtIndex.put(key, index);
        }

        tagCompound.setTag(NBT_ITEM_TYPE, itemsTag);
    }

    private void saveChangesDeferred() {
        DeferredCellOperations.markDirty(this, container);
    }

    /**
     * Get the total item capacity of the cell.
     * Equal distribution: effectivePerType * maxTypes
     */
    private long getTotalItemCapacity() {
        return effectivePerType * maxTypes;
    }

    // =====================
    // ICellInventory implementation
    // =====================

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        long existingCount = getStoredCount(input);
        boolean isNewType = existingCount == 0;

        // Reject new types beyond the limit
        if (isNewType && storedTypes >= maxTypes) return input;

        // Per-type capacity limit
        long typeAvailable = effectivePerType - existingCount;

        // Overflow card voids excess of already-stored types
        boolean canVoidOverflow = hasOverflowCard && !isNewType;

        if (typeAvailable <= 0) {
            if (canVoidOverflow) return null;
            return input;
        }

        long toInsert = Math.min(input.getStackSize(), typeAvailable);

        if (mode == Actionable.MODULATE) {
            if (isNewType) storedTypes++;

            setStoredCount(input, existingCount + toInsert);
            storedItemCount += toInsert;
            saveChangesDeferred();
        }

        if (toInsert >= input.getStackSize()) return null;
        if (canVoidOverflow) return null;

        IAEItemStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - toInsert);

        return remainder;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null || request.getStackSize() <= 0) return null;

        long existingCount = getStoredCount(request);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, newCount);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedItemCount = Math.max(0, storedItemCount - toExtract);
            saveChangesDeferred();
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);

        for (String key : itemsTag.getKeySet()) {
            NBTTagCompound itemTag = itemsTag.getCompoundTag(key);
            ItemStack stack = new ItemStack(itemTag);
            if (stack.isEmpty()) continue;

            long count = itemTag.getLong(NBT_STORED_COUNT);
            if (count <= 0) continue;

            IAEItemStack aeStack = channel.createStack(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
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
        return new CustomCellUpgrades(cellStack, 2);
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
        return storedItemCount;
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
    public long getUsedBytes() {
        if (storedItemCount == 0 && storedTypes == 0) return 0;

        long usedForTypes = (long) storedTypes * componentInfo.getBytesPerType();

        // 8 units per byte
        long usedForItems = (storedItemCount == 0) ? 0 : (storedItemCount - 1) / 8 + 1;

        return usedForItems + usedForTypes;
    }

    @Override
    public long getRemainingItemCount() {
        // Remaining = total effective capacity - stored
        long totalCapacity = getTotalItemCapacity();

        return Math.max(0, totalCapacity - storedItemCount);
    }

    @Override
    public int getUnusedItemCount() {
        // Fractional items not filling a byte
        long usedBytesForItems = getUsedBytes() - (long) storedTypes * componentInfo.getBytesPerType();
        if (usedBytesForItems <= 0) return 0;

        long fullItems = usedBytesForItems * 8;
        long unused = fullItems - storedItemCount;
        if (unused < 0) return 0;

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (storedItemCount == 0 && storedTypes == 0) return 4; // Empty but valid
        if (canHoldNewItem()) return 1; // Has space for new types
        if (getRemainingItemCount() > 0) return 2; // Has space for more of existing

        return 3; // Full
    }

    @Override
    public void persist() {
        // Individual item counts are saved in setStoredCount()
    }
}
