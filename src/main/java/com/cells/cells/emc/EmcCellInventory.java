package com.cells.cells.emc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

import com.latmod.mods.projectex.ProjectEXUtils;
import com.latmod.mods.projectex.integration.PersonalEMC;

import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.utils.Constants;

import com.cells.config.CellsConfig;
import com.cells.util.CellMathHelper;
import com.cells.util.DeferredCellOperations;
import com.cells.util.ItemStackKey;


/**
 * EMC-backed item cell inventory.
 * <p>
 * The cell snapshots its partition, unlocked slots, learned items, and owner once,
 * then keeps matching on cached filter state only. Provider EMC is only queried live
 * when the local EMC buffer cannot satisfy an extraction on its own.
 */
public class EmcCellInventory implements ICellInventory<IAEItemStack> {

    public static final String NBT_STORED_EMC = "StoredEmc";

    private static final long MAX_EMC = Constants.TILE_MAX_EMC;

    private final ItemStack cellStack;
    private final ISaveProvider container;
    private final IItemStorageChannel channel;
    private final NBTTagCompound tagCompound;
    private final EmcCellFilterHandler filterHandler;
    private final EmcCellUpgradeInventory upgradeInventory;
    private final int unlockedSlots;
    private final Map<ItemStackKey, FilterEntry> cachedFilters = new LinkedHashMap<>();
    private final List<FilterEntry> cachedFilterEntries = new ArrayList<>();
    private final Map<ItemStackKey, FilterEntry> cachedLearnedFilters = new LinkedHashMap<>();
    private final List<FilterEntry> cachedLearnedFilterEntries = new ArrayList<>();

    private long storedEmc;
    private boolean runtimeStateInitialized;

    @Nullable
    private UUID cachedOwnerId;

    @Nullable
    private IKnowledgeProvider cachedProvider;

    public EmcCellInventory(ItemStack cellStack, ISaveProvider container) {
        this.cellStack = cellStack;
        this.container = container;
        this.channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.tagCompound = Platform.openNbtData(cellStack);
        this.filterHandler = new EmcCellFilterHandler(cellStack);
        this.upgradeInventory = new EmcCellUpgradeInventory(cellStack);
        this.storedEmc = CellMathHelper.loadLong(this.tagCompound, NBT_STORED_EMC);

        this.filterHandler.loadCacheFromNBT();
        this.upgradeInventory.refresh();
        this.unlockedSlots = this.filterHandler.getUnlockedSlots();

        initializeStaticState();
        EmcCellSyncManager.track(this);
    }

    public boolean hasPartitionedContent() {
        return !this.cachedFilterEntries.isEmpty();
    }

    public boolean flushBufferedEmc() {
        if (this.storedEmc <= 0) return false;
        if (!ensureRuntimeState(null)) return false;
        if (this.cachedProvider == null) return false;

        long before = this.cachedProvider.getEmc();
        if (before >= MAX_EMC) return false;

        PersonalEMC.add(this.cachedProvider, this.storedEmc);

        long after = this.cachedProvider.getEmc();
        long moved = CellMathHelper.subtractWithUnderflowProtection(after, before);
        if (moved <= 0) return false;

        this.storedEmc = CellMathHelper.subtractWithUnderflowProtection(this.storedEmc, moved);
        saveChanges();
        return true;
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellStack;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public IItemHandler getConfigInventory() {
        return this.filterHandler;
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return this.upgradeInventory;
    }

    @Override
    public int getBytesPerType() {
        return 0;
    }

    @Override
    public boolean canHoldNewItem() {
        return false;
    }

    @Override
    public long getTotalBytes() {
        return 0;
    }

    @Override
    public long getFreeBytes() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalItemTypes() {
        return this.unlockedSlots;
    }

    @Override
    public long getStoredItemCount() {
        return getAccessibleFilterCount() > 0 ? getReportedAmount() : 0;
    }

    @Override
    public long getStoredItemTypes() {
        return getAccessibleFilterCount();
    }

    @Override
    public long getRemainingItemTypes() {
        return 0;
    }

    @Override
    public long getRemainingItemCount() {
        return 0;
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public int getStatusForCell() {
        return hasPartitionedContent() ? 1 : 4;
    }

    @Override
    public void persist() {
        saveStoredEmc();
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, IActionSource src) {
        if (input == null) return null;

        FilterEntry filterEntry = getFilterEntry(normalizeDefinition(input));

        if (filterEntry == null) return input;
        if (!ensureRuntimeState(src)) return input;
        if (!isLearnedFilter(filterEntry)) return input;

        if (mode == Actionable.MODULATE) {
            long gainedEmc = getInsertEmcValue(filterEntry.emcValue, input.getStackSize());
            if (gainedEmc > 0) {
                this.storedEmc = CellMathHelper.addWithOverflowProtection(this.storedEmc, gainedEmc);
                saveChangesDeferred();
            }

            IAEItemStack correction = input.copy();
            correction.setStackSize(-input.getStackSize());
            DeferredCellOperations.queueCrossTierNotification(
                this, this.container, this.channel, Collections.singletonList(correction), src);
        }

        return null;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;

        // We have no idea if the normalization will return the same stack as the request,
        // so we cannot assume we can reuse the IAEItemStack from the request.
        // Instead, we copy the cached IAEItemStack prototype from the filter entry,
        // which was built from the normalized ItemStack.
        FilterEntry filterEntry = getFilterEntry(normalizeDefinition(request));

        if (filterEntry == null) return null;
        if (!ensureRuntimeState(src)) return null;

        // Late learning check, after the runtime state is initialized (check needs world)
        if (!isLearnedFilter(filterEntry)) return null;

        long requestedAmount = Math.min(request.getStackSize(), getReportedAmount());
        ExtractionPlan extraction = createExtractionPlan(filterEntry, requestedAmount);
        if (extraction == null) return null;

        IAEItemStack result = copyFilterPrototype(filterEntry);
        if (result == null) return null;
        result.setStackSize(extraction.amount);

        if (mode == Actionable.MODULATE) {
            long fromBuffer = Math.min(this.storedEmc, extraction.cost);
            this.storedEmc = CellMathHelper.subtractWithUnderflowProtection(this.storedEmc, fromBuffer);

            // TODO: Optimize this to avoid DDoSing the provider with many requests.
            //       This is kinda tricky because we do not get a heads-up on how many extracts are coming,
            //       and EMC amount could become stale between the first and second extract.
            //       1st extract, something else drains all EMC, 2nd extract should fail, etc.
            //       We absolutely do not want to allow getting into the negative.
            //       - A solution may be to seize a certain amount of EMC, and then flush it back,
            //         but how much we can safely seize without knowing the future is unclear.
            //         If we have 100 cells seizing 1%, that's the entire provider's EMC gone.
            //       - A slightly different approach is a global broker for all EMC Cells,
            //         but then it becomes ProjectE in place of ProjectE, which is really not great.
            long fromProvider = extraction.cost - fromBuffer;
            if (fromProvider > 0) PersonalEMC.remove(this.cachedProvider, fromProvider);

            saveChangesDeferred();

            IAEItemStack correction = result.copy();
            DeferredCellOperations.queueCrossTierNotification(
                this, this.container, this.channel, Collections.singletonList(correction), src);
        }

        return result;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        if (!ensureRuntimeState(null)) return out;

        for (FilterEntry filterEntry : this.cachedLearnedFilterEntries) {
            // Copy the cached prototype, as callers may mutate or retain the returned stack
            IAEItemStack aeStack = copyFilterPrototype(filterEntry);
            if (aeStack == null) continue;

            aeStack.setStackSize(getReportedAmount());
            out.add(aeStack);
        }

        return out;
    }

    @Override
    public IItemStorageChannel getChannel() {
        return this.channel;
    }

    private void initializeStaticState() {
        this.cachedFilters.clear();
        this.cachedFilterEntries.clear();
        this.cachedLearnedFilters.clear();
        this.cachedLearnedFilterEntries.clear();

        for (int slot = 0; slot < unlockedSlots; slot++) {
            ItemStack filterStack = this.filterHandler.getStackInSlot(slot);
            if (filterStack.isEmpty()) continue;

            ItemStack normalized = normalizeStack(filterStack);
            if (normalized.isEmpty()) continue;

            ItemStackKey filterKey = ItemStackKey.of(normalized);
            if (filterKey == null || this.cachedFilters.containsKey(filterKey)) continue;

            long emcValue = ProjectEAPI.getEMCProxy().getValue(normalized);
            if (emcValue <= 0) continue;

            IAEItemStack aePrototype = this.channel.createStack(normalized);
            FilterEntry filterEntry = new FilterEntry(filterKey, normalized, aePrototype, emcValue);
            this.cachedFilters.put(filterKey, filterEntry);
            this.cachedFilterEntries.add(filterEntry);
        }
    }

    private void saveStoredEmc() {
        CellMathHelper.saveLong(this.tagCompound, NBT_STORED_EMC, this.storedEmc);
    }

    private void saveChangesDeferred() {
        saveStoredEmc();
        DeferredCellOperations.markDirty(this, this.container);
    }

    private void saveChanges() {
        saveStoredEmc();
        if (this.container != null) this.container.saveChanges(this);
    }

    private long getReportedAmount() {
        return CellsConfig.emcCellReportedAmount;
    }

    private long getAccessibleFilterCount() {
        if (!ensureRuntimeState(null)) return 0;
        return this.cachedLearnedFilterEntries.size();
    }

    @Nullable
    private IAEItemStack copyFilterPrototype(FilterEntry filterEntry) {
        if (filterEntry.aePrototype != null) return filterEntry.aePrototype.copy();

        return this.channel.createStack(filterEntry.prototype);
    }

    private ItemStack normalizeDefinition(IAEItemStack stack) {
        return normalizeStack(stack.getDefinition());
    }

    /**
     * Normalize the given stack to match the knowledge provider's view of the stack.
     * This means the item is considered valid for ProjectE interactions.
     * @param stack The stack to normalize
     * @return The normalized stack
     */
    private ItemStack normalizeStack(ItemStack stack) {
        // fixOutput() handles normalizing the stack to be like what is in knowledge,
        // e.g. by removing NBT, which could be used to trade between items that rely
        // on NBT tag to dispatch their types (e.g. Enchanted Books or Vis Crystals).
        ItemStack normalized = ProjectEXUtils.fixOutput(stack);
        if (normalized.isEmpty()) return ItemStack.EMPTY;

        return normalized;
    }

    /**
     * Returns the filter entry for the given stack, or null if the stack is not currently filtered.
     * The key is meant to be transient and should not be persisted, as it may be mutated later.
     * @param stack The stack to look up
     * @return The filter entry, or null if not found
     */
    @Nullable
    private FilterEntry getFilterEntry(ItemStack stack) {
        if (stack.isEmpty()) return null;

        ItemStackKey key = ItemStackKey.ofTransient(stack);
        if (key == null) return null;

        return this.cachedFilters.get(key);
    }

    private long getInsertEmcValue(long itemValue, long amount) {
        if (itemValue <= 0 || amount <= 0) return 0;

        double raw = itemValue * (double) amount * ProjectEConfig.difficulty.covalenceLoss;
        if (raw >= Long.MAX_VALUE) return Long.MAX_VALUE;
        if (raw <= 0) return 0;

        return (long) raw;
    }

    @Nullable
    private ExtractionPlan createExtractionPlan(FilterEntry filterEntry, long requestedAmount) {
        if (requestedAmount <= 0) return null;

        long value = filterEntry.emcValue;
        long accessibleEmc = this.storedEmc;
        long bufferedAmount = accessibleEmc / value;

        // We only check the provider EMC if the buffer cannot satisfy the request, to avoid unnecessary calls.
        // With ProjectE Teams, getEmc() needs to resolve the team from the player UUID,
        // not necessarily expensive but not exactly free either.
        if (requestedAmount > bufferedAmount && this.cachedProvider != null) {
            accessibleEmc = CellMathHelper.addWithOverflowProtection(accessibleEmc, this.cachedProvider.getEmc());
        }

        if (accessibleEmc < value) return null;

        long amount = Math.min(requestedAmount, accessibleEmc / value);
        if (amount <= 0) return null;

        long cost = CellMathHelper.multiplyWithOverflowProtection(value, amount);
        if (cost <= 0) return null;

        return new ExtractionPlan(amount, cost);
    }

    // As we need the world to resolve the provider, we lazy-initialize the runtime state
    private boolean ensureRuntimeState(@Nullable IActionSource src) {
        if (this.runtimeStateInitialized) return true;

        UUID ownerId = resolveOwnerId(src);
        if (ownerId == null) return false;

        World world = getWorld(src);
        if (world == null) return false;

        this.cachedProvider = PersonalEMC.get(world, ownerId);
        this.cachedLearnedFilters.clear();
        this.cachedLearnedFilterEntries.clear();

        for (FilterEntry filterEntry : this.cachedFilterEntries) {
            if (this.cachedProvider == null || !this.cachedProvider.hasKnowledge(filterEntry.prototype)) continue;

            this.cachedLearnedFilters.put(filterEntry.key, filterEntry);
            this.cachedLearnedFilterEntries.add(filterEntry);
        }

        this.runtimeStateInitialized = true;
        return true;
    }

    private boolean isLearnedFilter(FilterEntry filterEntry) {
        return this.cachedLearnedFilters.containsKey(filterEntry.key);
    }

    @Nullable
    private UUID resolveOwnerId(@Nullable IActionSource src) {
        if (this.cachedOwnerId != null) return this.cachedOwnerId;

        // The cell stays bound to the first player UUID that claims it. ProjectE Teams handles
        // team EMC and knowledge through the provider returned for that player UUID, so the cell
        // does not need to rewrite ownership when the player joins or leaves a team.
        UUID ownerId = ItemEmcCell.getOwnerId(this.cellStack);
        if (ownerId == null) {
            ItemEmcCell.ensureOwner(this.cellStack, src);
            ownerId = ItemEmcCell.getOwnerId(this.cellStack);
        }

        if (ownerId == null) return null;

        this.cachedOwnerId = ownerId;
        return ownerId;
    }

    @Nullable
    private World getWorld(@Nullable IActionSource src) {
        World world = CellMathHelper.getWorldFromSource(src);
        return world != null ? world : CellMathHelper.getWorldFromContainer(this.container);
    }

    private static final class FilterEntry {

        private final ItemStackKey key;
        private final ItemStack prototype;
        @Nullable
        private final IAEItemStack aePrototype;
        private final long emcValue;

        private FilterEntry(ItemStackKey key, ItemStack prototype, @Nullable IAEItemStack aePrototype, long emcValue) {
            this.key = key;
            this.prototype = prototype;
            this.aePrototype = aePrototype;
            this.emcValue = emcValue;
        }
    }

    private static final class ExtractionPlan {

        private final long amount;
        private final long cost;

        private ExtractionPlan(long amount, long cost) {
            this.amount = amount;
            this.cost = cost;
        }
    }
}