package com.cells.cells.configurable;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.api.networking.security.IActionSource;

import com.cells.config.CellsConfig;
import com.cells.util.FluidStackKey;
import com.cells.util.NBTSizeHelper;


/**
 * Fluid-channel inventory for the Configurable Storage Cell.
 * <p>
 * Extends the abstract base to provide fluid-specific NBT serialization
 * and inject/extract operations.
 */
public class ConfigurableCellFluidInventory extends AbstractConfigurableCellInventory<IAEFluidStack> {

    private static final String NBT_FLUID_TYPE = "fluidType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final IStorageChannel<IAEFluidStack> channel;

    // In-memory cache: FluidStackKey -> NBT index
    private final Map<FluidStackKey, Integer> keyToNbtIndex = new HashMap<>();
    // Cached AEFluidStacks by NBT index, avoids expensive reconstruction in getAvailableItems
    private final Map<Integer, IAEFluidStack> nbtIndexToFluidStack = new HashMap<>();
    // Cached counts by NBT index, avoids NBT reads in getAvailableItems and getStoredCount
    private final Map<Integer, Long> nbtIndexToCount = new HashMap<>();
    private final Map<FluidStackKey, Integer> fluidNbtSizes = new HashMap<>();
    private int cachedNextIndex = 0;

    public ConfigurableCellFluidInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        super(cellStack, container, componentInfo);
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        loadFromNBT();
    }

    @Override
    protected void loadFromNBT() {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        storedCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
        nbtIndexToFluidStack.clear();
        nbtIndexToCount.clear();
        fluidNbtSizes.clear();
        totalNbtSize = 0;
        cachedNextIndex = 0;

        for (String nbtKey : fluidsTag.getKeySet()) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(nbtKey);
            long count = fluidTag.getLong(NBT_STORED_COUNT);

            if (count > 0) {
                FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidTag);
                FluidStackKey key = FluidStackKey.of(stack);

                if (key != null) {
                    int index = Integer.parseInt(nbtKey);
                    if (index >= cachedNextIndex) cachedNextIndex = index + 1;

                    keyToNbtIndex.put(key, index);
                    nbtIndexToFluidStack.put(index, channel.createStack(stack));
                    nbtIndexToCount.put(index, count);
                    storedCount += count;
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
     * Set the stored count for a specific fluid using a pre-computed key.
     * Only serializes the full fluid on first insert; subsequent updates only change the count.
     *
     * @param fluid The AE fluid stack (needed for serialization of new fluids)
     * @param key   Pre-computed FluidStackKey to avoid duplicate NBT deep-copy
     * @param count The new count to set
     */
    private void setStoredCount(IAEFluidStack fluid, FluidStackKey key, long count) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
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
            // Update count only - NBT size change is minimal
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(String.valueOf(index));
            fluidTag.setLong(NBT_STORED_COUNT, count);
            nbtIndexToCount.put(index, count);
        } else {
            index = cachedNextIndex++;
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.getFluidStack().writeToNBT(fluidTag);
            fluidTag.setLong(NBT_STORED_COUNT, count);
            fluidsTag.setTag(String.valueOf(index), fluidTag);
            keyToNbtIndex.put(key, index);
            nbtIndexToFluidStack.put(index, fluid.copy());
            nbtIndexToCount.put(index, count);

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

    // =====================
    // ICellInventory implementation - channel-specific
    // =====================

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return channel;
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable mode, IActionSource src) {
        if (input == null || input.getStackSize() <= 0) return null;

        // Compute key once to avoid duplicate NBT deep-copy in getStoredCount + setStoredCount
        FluidStackKey key = FluidStackKey.of(input.getFluidStack());
        if (key == null) return input;

        long existingCount = getStoredCount(key);
        boolean isNewType = existingCount == 0;

        if (isNewType && storedTypes >= maxTypes) return input;

        long typeAvailable = effectivePerType - existingCount;
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

        IAEFluidStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - toInsert);

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

            storedCount = Math.max(0, storedCount - toExtract);
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

            // Copy the cached prototype to avoid mutating it (callers may hold references)
            IAEFluidStack aeStack = entry.getValue().copy();
            aeStack.setStackSize(count);
            out.add(aeStack);
        }

        return out;
    }
}
