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

    private long getStoredCount(IAEFluidStack fluid) {
        FluidStackKey key = FluidStackKey.of(fluid.getFluidStack());
        if (key == null) return 0;

        Integer index = keyToNbtIndex.get(key);
        if (index == null) return 0;

        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        return fluidsTag.getCompoundTag(String.valueOf(index)).getLong(NBT_STORED_COUNT);
    }

    private void setStoredCount(IAEFluidStack fluid, long count) {
        FluidStackKey key = FluidStackKey.of(fluid.getFluidStack());
        if (key == null) return;

        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        Integer index = keyToNbtIndex.get(key);

        if (count <= 0) {
            if (index != null) {
                // Subtract this fluid's NBT size from total
                Integer oldSize = fluidNbtSizes.remove(key);
                if (oldSize != null) totalNbtSize -= oldSize;

                fluidsTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
            }
        } else if (index != null) {
            // Update count only - NBT size change is minimal
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(String.valueOf(index));
            fluidTag.setLong(NBT_STORED_COUNT, count);
        } else {
            index = cachedNextIndex++;
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.getFluidStack().writeToNBT(fluidTag);
            fluidTag.setLong(NBT_STORED_COUNT, count);
            fluidsTag.setTag(String.valueOf(index), fluidTag);
            keyToNbtIndex.put(key, index);

            // Track NBT size for this new fluid (if enabled)
            if (CellsConfig.enableNbtSizeTooltip) {
                int fluidSize = NBTSizeHelper.calculateSize(fluidTag);
                fluidNbtSizes.put(key, fluidSize);
                totalNbtSize += fluidSize;
            }
        }

        tagCompound.setTag(NBT_FLUID_TYPE, fluidsTag);
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

        long existingCount = getStoredCount(input);
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

            setStoredCount(input, existingCount + toInsert);
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

        IAEFluidStack result = request.copy();
        result.setStackSize(toExtract);

        return result;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out) {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);

        for (String key : fluidsTag.getKeySet()) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(key);
            FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidTag);
            if (stack == null) continue;

            long count = fluidTag.getLong(NBT_STORED_COUNT);
            if (count <= 0) continue;

            IAEFluidStack aeStack = channel.createStack(stack);
            if (aeStack != null) {
                aeStack.setStackSize(count);
                out.add(aeStack);
            }
        }

        return out;
    }
}
