package com.cells.integration.thaumicenergistics;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IItemList;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import com.cells.cells.configurable.AbstractConfigurableCellInventory;
import com.cells.cells.configurable.ComponentInfo;
import com.cells.config.CellsConfig;
import com.cells.util.NBTSizeHelper;


/**
 * Essentia-channel inventory for the Configurable Storage Cell.
 * <p>
 * Extends the abstract base to provide essentia-specific NBT serialization
 * and inject/extract operations.
 */
public class ConfigurableCellEssentiaInventory extends AbstractConfigurableCellInventory<IAEEssentiaStack> {

    private static final String NBT_ESSENTIA_TYPE = "essentiaType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final IStorageChannel<IAEEssentiaStack> channel;

    // In-memory cache: EssentiaStackKey -> NBT index
    private final Map<EssentiaStackKey, Integer> keyToNbtIndex = new HashMap<>();
    // NBT size tracking per essentia type
    private final Map<EssentiaStackKey, Integer> essentiaEntryNbtSizes = new HashMap<>();
    private int cachedNextIndex = 0;

    public ConfigurableCellEssentiaInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        super(cellStack, container, componentInfo);
        this.channel = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
        loadFromNBT();
    }

    @Override
    protected void loadFromNBT() {
        NBTTagCompound essentiaTag = tagCompound.getCompoundTag(NBT_ESSENTIA_TYPE);
        storedCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        essentiaEntryNbtSizes.clear();
        totalNbtSize = 0;
        cachedNextIndex = 0;

        for (String nbtKey : essentiaTag.getKeySet()) {
            NBTTagCompound entryTag = essentiaTag.getCompoundTag(nbtKey);
            long count = entryTag.getLong(NBT_STORED_COUNT);

            if (count > 0) {
                String aspectTag = entryTag.getString("Aspect");
                EssentiaStackKey key = EssentiaStackKey.of(aspectTag);

                if (key != null && key.getAspect() != null) {
                    int index = Integer.parseInt(nbtKey);
                    if (index >= cachedNextIndex) cachedNextIndex = index + 1;

                    keyToNbtIndex.put(key, index);
                    storedCount += count;
                    storedTypes++;

                    // Track NBT size for this essentia (if enabled)
                    if (CellsConfig.enableNbtSizeTooltip) {
                        int entrySize = NBTSizeHelper.calculateSize(entryTag);
                        essentiaEntryNbtSizes.put(key, entrySize);
                        totalNbtSize += entrySize;
                    }
                }
            }
        }
    }

    private long getStoredCount(IAEEssentiaStack essentia) {
        EssentiaStackKey key = EssentiaStackKey.of(essentia.getAspect());
        if (key == null) return 0;

        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        NBTTagCompound essentiaTag = tagCompound.getCompoundTag(NBT_ESSENTIA_TYPE);
        return essentiaTag.getCompoundTag(String.valueOf(index)).getLong(NBT_STORED_COUNT);
    }

    private void setStoredCount(IAEEssentiaStack essentia, long count) {
        EssentiaStackKey key = EssentiaStackKey.of(essentia.getAspect());
        if (key == null) return;

        NBTTagCompound essentiaTag = tagCompound.getCompoundTag(NBT_ESSENTIA_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            if (index != null) {
                // Subtract this essentia's NBT size from total
                Integer oldSize = essentiaEntryNbtSizes.remove(key);
                if (oldSize != null) totalNbtSize -= oldSize;

                essentiaTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
            }
        } else if (index != null) {
            // Update count only - NBT size change is minimal
            NBTTagCompound entryTag = essentiaTag.getCompoundTag(String.valueOf(index));
            entryTag.setLong(NBT_STORED_COUNT, count);
        } else {
            // New essentia - serialize fully
            index = cachedNextIndex++;
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString("Aspect", essentia.getAspect().getTag());
            entryTag.setLong(NBT_STORED_COUNT, count);
            essentiaTag.setTag(String.valueOf(index), entryTag);
            keyToNbtIndex.put(key, index);

            // Track NBT size for this new essentia (if enabled)
            if (CellsConfig.enableNbtSizeTooltip) {
                int entrySize = NBTSizeHelper.calculateSize(entryTag);
                essentiaEntryNbtSizes.put(key, entrySize);
                totalNbtSize += entrySize;
            }
        }

        tagCompound.setTag(NBT_ESSENTIA_TYPE, essentiaTag);
    }

    // =====================
    // ICellInventory implementation - channel-specific
    // =====================

    @Override
    public IStorageChannel<IAEEssentiaStack> getChannel() {
        return channel;
    }

    @Override
    public IAEEssentiaStack injectItems(IAEEssentiaStack input, Actionable mode, IActionSource src) {
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

        IAEEssentiaStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - toInsert);

        return remainder;
    }

    @Override
    public IAEEssentiaStack extractItems(IAEEssentiaStack request, Actionable mode, IActionSource src) {
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

        IAEEssentiaStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEEssentiaStack> getAvailableItems(IItemList<IAEEssentiaStack> out) {
        NBTTagCompound essentiaTag = tagCompound.getCompoundTag(NBT_ESSENTIA_TYPE);

        for (String key : essentiaTag.getKeySet()) {
            NBTTagCompound entryTag = essentiaTag.getCompoundTag(key);
            String aspectTag = entryTag.getString("Aspect");
            Aspect aspect = Aspect.getAspect(aspectTag);
            if (aspect == null) continue;

            long count = entryTag.getLong(NBT_STORED_COUNT);
            if (count <= 0) continue;

            EssentiaStack stack = new EssentiaStack(aspect, (int) Math.min(count, Integer.MAX_VALUE));
            IAEEssentiaStack aeStack = AEEssentiaStack.fromEssentiaStack(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
            }
        }

        return out;
    }
}
