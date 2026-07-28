package com.cells.cells.emc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.latmod.mods.projectex.ProjectEXUtils;

import moze_intel.projecte.api.ProjectEAPI;

import com.cells.ItemRegistry;
import com.cells.cells.creative.AbstractCreativeCellFilterHandler;
import com.cells.config.CellsConfig;
import com.cells.integration.jei.cellview.CellViewHelper;
import com.cells.util.ItemStackKey;


/**
 * NBT-backed ghost partition inventory for the Cell Workbench.
 */
public class EmcCellFilterHandler extends AbstractCreativeCellFilterHandler<ItemStack, ItemStackKey>
        implements IItemHandlerModifiable {

    private static final String NBT_KEY_FILTERS = "EmcFilters";

    public EmcCellFilterHandler(@Nonnull ItemStack cellStack) {
        super(cellStack);
    }

    @Override
    protected String getNBTKey() {
        return NBT_KEY_FILTERS;
    }

    @Override
    protected ItemStack readStackFromNBT(@Nonnull NBTTagCompound nbt) {
        return new ItemStack(nbt);
    }

    @Override
    protected void writeStackToNBT(@Nonnull ItemStack stack, @Nonnull NBTTagCompound nbt) {
        stack.writeToNBT(nbt);
    }

    @Override
    protected ItemStackKey createKey(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : ItemStackKey.of(stack);
    }

    @Override
    protected ItemStack createGhostCopy(@Nonnull ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    protected boolean isStackEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    @Override
    protected int getSlotCount() {
        return CellsConfig.getEmcCellMaxPartitionSlots();
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        if (slot >= getUnlockedSlots()) return ItemStack.EMPTY;

        ItemStack result = super.getStackInSlot(slot);
        return result != null ? result : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        if (slot >= getUnlockedSlots()) return;
        if (!stack.isEmpty() && !isItemValid(slot, stack)) return;

        super.setStackInSlot(slot, stack.isEmpty() ? ItemStack.EMPTY : ProjectEXUtils.fixOutput(stack));
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (slot >= getUnlockedSlots()) return stack;
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;

        if (!simulate) setStackInSlot(slot, stack);

        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot >= getUnlockedSlots()) return ItemStack.EMPTY;

        ItemStack extracted = getStackInSlot(slot);
        if (extracted.isEmpty()) return ItemStack.EMPTY;

        if (!simulate) setStackInSlot(slot, ItemStack.EMPTY);

        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot >= getUnlockedSlots()) return false;
        if (stack.isEmpty()) return true;
        if (CellViewHelper.isCell(stack)) return false;

        // Only allow items with EMC value, to prevent confusion
        // The specific knowledge of the player cannot be checked here, as we have no world
        return ProjectEAPI.getEMCProxy().hasValue(ProjectEXUtils.fixOutput(stack));
    }

    public int getUnlockedSlots() {
        if (ItemRegistry.EMC_CAPACITY_CARD == null) return CellsConfig.getEmcCellUnlockedSlots(0);

        int unlocked = ItemRegistry.EMC_CAPACITY_CARD.getUnlockedSlots(this.cellStack);
        return Math.max(1, unlocked);
    }

    public int getVisibleFilterCount() {
        int count = 0;
        int unlockedSlots = getUnlockedSlots();

        for (int slot = 0; slot < unlockedSlots; slot++) {
            if (!getStackInSlot(slot).isEmpty()) count++;
        }

        return count;
    }

    @Nonnull
    public MergeResult mergeMissingStacks(@Nonnull Iterable<ItemStack> stacks) {
        int unlockedSlots = getUnlockedSlots();
        Set<ItemStackKey> knownFilters = new LinkedHashSet<>();
        List<Integer> emptySlots = new ArrayList<>();

        // Collect known filters and empty slots
        for (int slot = 0; slot < unlockedSlots; slot++) {
            ItemStack existingStack = getStackInSlot(slot);
            if (existingStack.isEmpty()) {
                emptySlots.add(slot);
                continue;
            }

            ItemStackKey existingKey = ItemStackKey.of(existingStack);
            if (existingKey != null) knownFilters.add(existingKey);
        }

        int addedCount = 0;
        int blockedByCapacityCount = 0;
        int nextEmptySlot = 0;

        // Append new unique filters into currently empty slots without disturbing existing partitions
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;

            ItemStack normalized = ProjectEXUtils.fixOutput(stack);
            if (normalized.isEmpty()) continue;
            if (!isItemValid(0, normalized)) continue;

            ItemStackKey key = ItemStackKey.of(normalized);
            if (key == null || !knownFilters.add(key)) continue;

            if (nextEmptySlot >= emptySlots.size()) {
                blockedByCapacityCount++;
                continue;
            }

            setStackInSlot(emptySlots.get(nextEmptySlot), normalized);
            nextEmptySlot++;
            addedCount++;
        }

        return new MergeResult(addedCount, blockedByCapacityCount);
    }

    public static class MergeResult {

        private final int addedCount;
        private final int blockedByCapacityCount;

        private MergeResult(int addedCount, int blockedByCapacityCount) {
            this.addedCount = addedCount;
            this.blockedByCapacityCount = blockedByCapacityCount;
        }

        public int getAddedCount() {
            return this.addedCount;
        }

        public int getBlockedByCapacityCount() {
            return this.blockedByCapacityCount;
        }
    }
}