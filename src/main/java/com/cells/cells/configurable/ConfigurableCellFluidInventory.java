package com.cells.cells.configurable;

import java.util.HashMap;
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
import appeng.items.contents.CellConfig;
import appeng.util.Platform;

import com.cells.config.CellsConfig;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;
import com.cells.util.DeferredCellOperations;
import com.cells.util.FluidStackKey;


/**
 * Fluid-channel inventory for the Configurable Storage Cell.
 * <p>
 * Identical logic to ConfigurableCellItemInventory but operates on IAEFluidStack
 * and stores data under the "fluidType" NBT key.
 */
public class ConfigurableCellFluidInventory implements ICellInventory<IAEFluidStack> {

    private static final String NBT_FLUID_TYPE = "fluidType";
    private static final String NBT_STORED_COUNT = "StoredCount";

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IStorageChannel<IAEFluidStack> channel;
    private final ComponentInfo componentInfo;

    private final NBTTagCompound tagCompound;

    private final Map<FluidStackKey, Integer> keyToNbtIndex = new HashMap<>();
    private int cachedNextIndex = 0;

    private long storedFluidCount = 0;
    private int storedTypes = 0;

    private final int maxTypes;
    private final long userMaxPerType;
    private final long physicalPerType;
    private final long effectivePerType;
    private final boolean hasOverflowCard;

    public ConfigurableCellFluidInventory(ItemStack cellStack, ISaveProvider container, ComponentInfo componentInfo) {
        this.cellStack = cellStack;
        this.container = container;
        this.componentInfo = componentInfo;
        this.channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);

        this.maxTypes = CellsConfig.configurableCellMaxTypes;
        this.userMaxPerType = ComponentHelper.getMaxPerType(cellStack);
        this.physicalPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(componentInfo, maxTypes);
        this.effectivePerType = Math.min(userMaxPerType, physicalPerType * 1000L);

        IItemHandler upgrades = new CustomCellUpgrades(cellStack, 2);
        this.hasOverflowCard = CellUpgradeHelper.hasOverflowCard(upgrades);

        loadFromNBT();
    }

    private void loadFromNBT() {
        NBTTagCompound fluidsTag = tagCompound.getCompoundTag(NBT_FLUID_TYPE);
        storedFluidCount = 0;
        storedTypes = 0;
        keyToNbtIndex.clear();
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
                    storedFluidCount += count;
                    storedTypes++;
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
                fluidsTag.removeTag(String.valueOf(index));
                keyToNbtIndex.remove(key);
            }
        } else if (index != null) {
            NBTTagCompound fluidTag = fluidsTag.getCompoundTag(String.valueOf(index));
            fluidTag.setLong(NBT_STORED_COUNT, count);
        } else {
            index = cachedNextIndex++;
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.getFluidStack().writeToNBT(fluidTag);
            fluidTag.setLong(NBT_STORED_COUNT, count);
            fluidsTag.setTag(String.valueOf(index), fluidTag);
            keyToNbtIndex.put(key, index);
        }

        tagCompound.setTag(NBT_FLUID_TYPE, fluidsTag);
    }

    private void saveChangesDeferred() {
        DeferredCellOperations.markDirty(this, container);
    }

    private long getTotalFluidCapacity() {
        return effectivePerType * maxTypes;
    }

    // =====================
    // ICellInventory implementation
    // =====================

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
            storedFluidCount += toInsert;
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

            storedFluidCount = Math.max(0, storedFluidCount - toExtract);
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
        return storedFluidCount;
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
        if (storedFluidCount == 0 && storedTypes == 0) return 0;

        long usedForTypes = (long) storedTypes * componentInfo.getBytesPerType();
        long usedForFluids = (storedFluidCount == 0) ? 0 : (storedFluidCount - 1) / 8 + 1;

        return usedForFluids + usedForTypes;
    }

    @Override
    public long getRemainingItemCount() {
        return Math.max(0, getTotalFluidCapacity() - storedFluidCount);
    }

    @Override
    public int getUnusedItemCount() {
        long usedBytesForFluids = getUsedBytes() - (long) storedTypes * componentInfo.getBytesPerType();
        if (usedBytesForFluids <= 0) return 0;

        long fullItems = usedBytesForFluids * 8;
        long unused = fullItems - storedFluidCount;
        if (unused < 0) return 0;

        return (int) Math.min(unused, Integer.MAX_VALUE);
    }

    @Override
    public int getStatusForCell() {
        if (storedFluidCount == 0 && storedTypes == 0) return 0;
        if (canHoldNewItem()) return 1;
        if (getRemainingItemCount() > 0) return 2;

        return 3;
    }

    @Override
    public void persist() {
        // Individual fluid counts are saved in setStoredCount()
    }
}
