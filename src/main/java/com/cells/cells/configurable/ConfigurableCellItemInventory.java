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

import com.cells.util.ItemStackKey;


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
                    storedCount += count;
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

        long existingCount = getStoredCount(request);
        if (existingCount <= 0) return null;

        long toExtract = Math.min(request.getStackSize(), existingCount);

        if (mode == Actionable.MODULATE) {
            long newCount = existingCount - toExtract;
            setStoredCount(request, newCount);

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
}
