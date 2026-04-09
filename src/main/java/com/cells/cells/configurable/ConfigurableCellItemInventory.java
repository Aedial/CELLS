package com.cells.cells.configurable;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.api.networking.security.IActionSource;

import com.cells.config.CellsConfig;
import com.cells.util.ItemStackKey;
import com.cells.util.NBTSizeHelper;


/**
 * Item-channel inventory for the Configurable Storage Cell.
 * <p>
 * Extends the abstract base to provide item-specific NBT serialization
 * and inject/extract operations.
 */
public class ConfigurableCellItemInventory extends AbstractConfigurableCellInventory<IAEItemStack> {

    private static final String NBT_ITEM_TYPE = "itemType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final IStorageChannel<IAEItemStack> channel;

    // In-memory cache: ItemStackKey -> NBT index
    private final Map<ItemStackKey, Integer> keyToNbtIndex = new HashMap<>();
    // Cached AEItemStacks by NBT index, avoids expensive reconstruction in getAvailableItems
    private final Map<Integer, IAEItemStack> nbtIndexToItemStack = new HashMap<>();
    // Cached counts by NBT index, avoids NBT reads in getAvailableItems and getStoredCount
    private final Map<Integer, Long> nbtIndexToCount = new HashMap<>();
    private final Map<ItemStackKey, Integer> itemNbtSizes = new HashMap<>();
    private int cachedNextIndex = 0;

    public ConfigurableCellItemInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        super(cellStack, container, componentInfo);
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        loadFromNBT();
    }

    @Override
    protected void loadFromNBT() {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        storedCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        nbtIndexToItemStack.clear();
        nbtIndexToCount.clear();
        itemNbtSizes.clear();
        totalNbtSize = 0;
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
                    nbtIndexToItemStack.put(index, channel.createStack(stack));
                    nbtIndexToCount.put(index, count);
                    storedCount += count;
                    storedTypes++;

                    // Track NBT size for this item (if enabled)
                    if (CellsConfig.enableNbtSizeTooltip) {
                        int itemSize = NBTSizeHelper.calculateSize(itemTag);
                        itemNbtSizes.put(key, itemSize);
                        totalNbtSize += itemSize;
                    }
                }
            }
        }
    }

    /**
     * Get the stored item count using a pre-computed key.
     * Avoids creating a new ItemStackKey (which deep-copies NBT) on every call.
     */
    private long getStoredCount(ItemStackKey key) {
        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        return nbtIndexToCount.getOrDefault(index, 0L);
    }

    /**
     * Set the stored count for a specific item using a pre-computed key.
     * Only serializes the full item on first insert; subsequent updates only change the count.
     *
     * @param item  The AE item stack (needed for serialization of new items)
     * @param key   Pre-computed ItemStackKey to avoid duplicate NBT deep-copy
     * @param count The new count to set
     */
    private void setStoredCount(IAEItemStack item, ItemStackKey key, long count) {
        NBTTagCompound itemsTag = tagCompound.getCompoundTag(NBT_ITEM_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            if (index != null) {
                // Subtract this item's NBT size from total
                Integer oldSize = itemNbtSizes.remove(key);
                if (oldSize != null) totalNbtSize -= oldSize;

                itemsTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
                nbtIndexToItemStack.remove(index);
                nbtIndexToCount.remove(index);
            }
        } else if (index != null) {
            // Update count only - NBT size change is minimal
            NBTTagCompound itemTag = itemsTag.getCompoundTag(String.valueOf(index));
            itemTag.setLong(NBT_STORED_COUNT, count);
            nbtIndexToCount.put(index, count);
        } else {
            // New item - serialize fully
            index = cachedNextIndex++;
            NBTTagCompound itemTag = new NBTTagCompound();
            item.getDefinition().writeToNBT(itemTag);
            itemTag.setLong(NBT_STORED_COUNT, count);
            itemsTag.setTag(String.valueOf(index), itemTag);
            keyToNbtIndex.put(key, index);
            nbtIndexToItemStack.put(index, item.copy());
            nbtIndexToCount.put(index, count);

            // Track NBT size for this new item (if enabled)
            if (CellsConfig.enableNbtSizeTooltip) {
                int itemSize = NBTSizeHelper.calculateSize(itemTag);
                itemNbtSizes.put(key, itemSize);
                totalNbtSize += itemSize;
            }

            // Only set the parent tag reference when adding a new item, the compound may
            // not be in the parent yet (first item ever). For updates/removals, the compound
            // is already referenced from the parent and modifications are reflected automatically.
            tagCompound.setTag(NBT_ITEM_TYPE, itemsTag);
        }
    }

    // =====================
    // ICellInventory implementation - channel-specific
    // =====================

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return channel;
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        // Compute key once to avoid duplicate NBT deep-copy in getStoredCount + setStoredCount
        ItemStackKey key = ItemStackKey.of(input.getDefinition());
        if (key == null) return input;

        long existingCount = getStoredCount(key);
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

            setStoredCount(input, key, existingCount + toInsert);
            storedCount += toInsert;
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

        // Compute key once to avoid duplicate NBT deep-copy in getStoredCount + setStoredCount
        ItemStackKey key = ItemStackKey.of(request.getDefinition());
        if (key == null) return null;

        long existingCount = getStoredCount(key);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, key, newCount);

            if (newCount <= 0) storedTypes = Math.max(0, storedTypes - 1);

            storedCount = Math.max(0, storedCount - toExtract);
            saveChangesDeferred();
        }

        IAEItemStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        for (Map.Entry<Integer, IAEItemStack> entry : nbtIndexToItemStack.entrySet()) {
            long count = nbtIndexToCount.getOrDefault(entry.getKey(), 0L);
            if (count <= 0) continue;

            // Copy the cached prototype to avoid mutating it, callers may hold references
            IAEItemStack aeStack = entry.getValue().copy();
            aeStack.setStackSize(count);
            out.add(aeStack);
        }

        return out;
    }
}
