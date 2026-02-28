package com.cells.integration.mekanismenergistics;

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

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;

import com.cells.cells.configurable.AbstractConfigurableCellInventory;
import com.cells.cells.configurable.ComponentInfo;


/**
 * Gas-channel inventory for the Configurable Storage Cell.
 * <p>
 * Extends the abstract base to provide gas-specific NBT serialization
 * and inject/extract operations.
 */
public class ConfigurableCellGasInventory extends AbstractConfigurableCellInventory<IAEGasStack> {

    private static final String NBT_GAS_TYPE = "gasType";
    private static final String NBT_STORED_COUNT = "StoredCount";
    private static final String NBT_GAS_NAME = "GasName";

    private final IStorageChannel<IAEGasStack> channel;

    // In-memory cache: GasStackKey -> NBT index
    private final Map<GasStackKey, Integer> keyToNbtIndex = new HashMap<>();
    private int cachedNextIndex = 0;

    public ConfigurableCellGasInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        super(cellStack, container, componentInfo);
        this.channel = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
        loadFromNBT();
    }

    @Override
    protected void loadFromNBT() {
        NBTTagCompound gasTag = tagCompound.getCompoundTag(NBT_GAS_TYPE);
        storedCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        cachedNextIndex = 0;

        for (String nbtKey : gasTag.getKeySet()) {
            NBTTagCompound entryTag = gasTag.getCompoundTag(nbtKey);
            long count = entryTag.getLong(NBT_STORED_COUNT);

            if (count > 0) {
                String gasName = entryTag.getString(NBT_GAS_NAME);
                Gas gas = GasRegistry.getGas(gasName);
                GasStackKey key = GasStackKey.of(gas);

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

    private long getStoredCount(IAEGasStack gas) {
        GasStackKey key = GasStackKey.of(gas.getGas());
        if (key == null) return 0;

        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        NBTTagCompound gasTag = tagCompound.getCompoundTag(NBT_GAS_TYPE);
        return gasTag.getCompoundTag(String.valueOf(index)).getLong(NBT_STORED_COUNT);
    }

    private void setStoredCount(IAEGasStack gas, long count) {
        GasStackKey key = GasStackKey.of(gas.getGas());
        if (key == null) return;

        NBTTagCompound gasTag = tagCompound.getCompoundTag(NBT_GAS_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            if (index != null) {
                gasTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
            }
        } else if (index != null) {
            // Update count only
            NBTTagCompound entryTag = gasTag.getCompoundTag(String.valueOf(index));
            entryTag.setLong(NBT_STORED_COUNT, count);
        } else {
            // New gas - serialize fully
            index = cachedNextIndex++;
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString(NBT_GAS_NAME, gas.getGas().getName());
            entryTag.setLong(NBT_STORED_COUNT, count);
            gasTag.setTag(String.valueOf(index), entryTag);
            keyToNbtIndex.put(key, index);
        }

        tagCompound.setTag(NBT_GAS_TYPE, gasTag);
    }

    // =====================
    // ICellInventory implementation - channel-specific
    // =====================

    @Override
    public IStorageChannel<IAEGasStack> getChannel() {
        return channel;
    }

    @Override
    public IAEGasStack injectItems(IAEGasStack input, Actionable mode, IActionSource src) {
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

        IAEGasStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - toInsert);

        return remainder;
    }

    @Override
    public IAEGasStack extractItems(IAEGasStack request, Actionable mode, IActionSource src) {
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

        IAEGasStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEGasStack> getAvailableItems(IItemList<IAEGasStack> out) {
        NBTTagCompound gasTag = tagCompound.getCompoundTag(NBT_GAS_TYPE);

        for (String key : gasTag.getKeySet()) {
            NBTTagCompound entryTag = gasTag.getCompoundTag(key);
            String gasName = entryTag.getString(NBT_GAS_NAME);
            Gas gas = GasRegistry.getGas(gasName);
            if (gas == null) continue;

            long count = entryTag.getLong(NBT_STORED_COUNT);
            if (count <= 0) continue;

            GasStack stack = new GasStack(gas, (int) Math.min(count, Integer.MAX_VALUE));
            IAEGasStack aeStack = AEGasStack.of(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
            }
        }

        return out;
    }
}
