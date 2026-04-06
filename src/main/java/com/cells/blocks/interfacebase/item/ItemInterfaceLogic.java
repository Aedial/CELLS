package com.cells.blocks.interfacebase.item;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.capabilities.Capabilities;
import appeng.util.item.AEItemStack;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.managers.InterfaceAdjacentHandler;
import com.cells.items.ItemRecoveryContainer;
import com.cells.util.ItemStackKey;


/**
 * Item-specific implementation of the resource interface logic.
 * Handles item import/export interfaces for both tiles and parts.
 * <p>
 * Extends {@link AbstractResourceInterfaceLogic} with ItemStack as the resource type,
 * IAEItemStack as the AE2 stack type, and ItemStackKey as the key type.
 * <p>
 * Provides IItemHandler wrappers for Forge capability system compatibility.
 */
public class ItemInterfaceLogic extends AbstractResourceInterfaceLogic<ItemStack, IAEItemStack, ItemStackKey> {

    /**
     * Host interface for item interfaces.
     * Extends the base Host interface without adding additional requirements.
     */
    public interface Host extends AbstractResourceInterfaceLogic.Host {
    }

    /** Default max slot size for items (standard stack size). */
    public static final int DEFAULT_MAX_SLOT_SIZE = 64;

    /** IItemHandler wrapper for storage array access. */
    private final IItemHandlerModifiable storageHandler;

    /** External handler exposed via capabilities. */
    private final IItemHandler externalHandler;

    @Override
    public long getDefaultMaxSlotSize() {
        return DEFAULT_MAX_SLOT_SIZE;
    }

    public ItemInterfaceLogic(Host host) {
        super(host, ItemStack.class);

        // Create IItemHandler wrapper for storage access via GUI
        this.storageHandler = new ArrayItemHandler(STORAGE_SLOTS, false);

        // Create appropriate external handler based on direction
        if (host.isExport()) {
            this.externalHandler = new ExportStorageHandler(this);
        } else {
            this.externalHandler = new FilteredStorageHandler(this);
        }
    }

    @Override
    public String getTypeName() {
        return "item";
    }

    /**
     * @return IItemHandler wrapper for storage access.
     */
    public IItemHandlerModifiable getStorageInventory() {
        return this.storageHandler;
    }

    /**
     * @return The external handler to expose via capabilities.
     */
    public IItemHandler getExternalHandler() {
        return this.externalHandler;
    }

    /**
     * @return The external handler as an IItemRepository for bulk slotless access.
     *         Both ExportStorageHandler and FilteredStorageHandler implement IItemRepository.
     */
    public IItemRepository getItemRepository() {
        return (IItemRepository) this.externalHandler;
    }

    /**
     * Get the ItemStack in a specific slot with the correct amount.
     * Uses getResourceInSlot to return a stack with the actual amount from amounts[] array.
     *
     * @param slot The slot index
     * @return The stored ItemStack with correct count, or null if empty
     */
    @Nullable
    public ItemStack getItemInSlot(int slot) {
        return getResourceInSlot(slot);
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     * Import uses filter-to-slot mapping; export checks direct filter match.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return false;
        if (stack == null || stack.isEmpty()) return false;

        ItemStackKey filterKey = this.getFilterKey(slot);
        if (filterKey == null) return false;

        return filterKey.matches(stack);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected ItemStackKey createKey(ItemStack resource) {
        return ItemStackKey.of(resource);
    }

    @Override
    protected int getAmount(ItemStack resource) {
        return resource.getCount();
    }

    @Override
    protected void setAmount(ItemStack resource, int amount) {
        resource.setCount(amount);
    }

    @Override
    protected ItemStack copyWithAmount(ItemStack resource, int amount) {
        ItemStack copy = resource.copy();
        copy.setCount(amount);
        return copy;
    }

    @Override
    protected ItemStack copy(ItemStack resource) {
        return resource.copy();
    }

    @Override
    protected String getLocalizedName(ItemStack resource) {
        return resource.getDisplayName();
    }

    @Override
    protected IAEItemStack toAEStack(ItemStack resource) {
        return AEItemStack.fromItemStack(resource);
    }

    @Override
    protected ItemStack fromAEStack(IAEItemStack aeStack) {
        return aeStack.createItemStack();
    }

    @Override
    protected long getAEStackSize(IAEItemStack aeStack) {
        return aeStack.getStackSize();
    }

    @Override
    protected void writeResourceToNBT(ItemStack resource, NBTTagCompound tag) {
        resource.writeToNBT(tag);
    }

    @Override
    @Nullable
    protected ItemStack readResourceFromNBT(NBTTagCompound tag) {
        ItemStack stack = new ItemStack(tag);
        return stack.isEmpty() ? null : stack;
    }

    @Override
    protected String getResourceName(ItemStack resource) {
        // For items, we need to include the registry name
        ResourceLocation registryName = resource.getItem().getRegistryName();
        return registryName != null ? registryName.toString() : "";
    }

    @Override
    @Nullable
    protected ItemStack getResourceByName(String name, int amount) {
        Item item = Item.getByNameOrId(name);
        if (item == null) return null;
        return new ItemStack(item, amount);
    }

    @Override
    protected IMEInventory<IAEItemStack> getMEInventory(IStorageGrid storage) {
        return storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
        );
    }

    @Override
    protected ItemStack createRecoveryItem(ItemStack identity, long amount) {
        // Items with small amounts can be dropped directly as ItemStack
        // For large amounts (> max stack size), use RecoveryContainer
        if (amount <= Integer.MAX_VALUE) {
            ItemStack drop = identity.copy();
            drop.setCount((int) amount);
            return drop;
        }

        // Large amounts need RecoveryContainer to preserve full long amount
        return ItemRecoveryContainer.createForItem(identity, amount);
    }

    // ============================== Auto-Pull/Push capability methods ==============================

    @Override
    protected List<Capability<?>> getAdjacentCapabilities() {
        // Prefer IItemRepository (slotless bulk operations, e.g. Storage Drawers) over
        // IItemHandler (slot-by-slot iteration). ITEM_REPOSITORY_CAPABILITY is always
        // registered by CELLS in preInit, even without Storage Drawers present.
        return Arrays.asList(Capabilities.ITEM_REPOSITORY_CAPABILITY, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
    }

    // ============================== IItemHandler slot cache ==============================

    /**
     * Cached snapshot of an IItemHandler's slot contents, built once per handler per
     * auto-pull/push cycle. Avoids O(N×F) full-scan overhead (N=handler slots, F=filter count)
     * by indexing slots by ItemStackKey, allowing extract/insert operations to jump directly
     * to relevant slots instead of scanning all N slots per filter.
     * <p>
     * The cache is <b>mutable</b>: counts, empty-slot lists, and totalCounts are updated
     * after each extract/insert operation, so subsequent filter iterations within the same
     * cycle see accurate state without re-scanning. This also means that callers holding
     * a reference to {@link #totalCounts} (e.g. keepQuantity logic in
     * {@link InterfaceAdjacentHandler}) automatically see post-mutation values.
     * <p>
     * Lifecycle: built lazily on first access via {@link #getOrBuildCache}, valid for the
     * duration of one {@link InterfaceAdjacentHandler#performAutoPullPush} cycle, then
     * cleared by {@link #flushOperationCaches()}.
     */
    private static class ItemHandlerSlotCache {

        /** The handler this cache was built for (identity comparison). */
        final IItemHandler handler;

        /**
         * Occupied slots indexed by item key. Each entry tracks the slot index,
         * current count (mutable), and slot limit. Entries with count=0 are
         * stale (depleted by extraction) and skipped on access.
         */
        final Map<ItemStackKey, List<SlotEntry>> occupiedSlots;

        /**
         * Empty slots available for insertion, ordered by index.
         * Entries are removed as items are inserted into them.
         */
        final List<SlotEntry> emptySlots;

        /**
         * Pre-computed total counts per key. Updated in-place after mutations,
         * so callers holding a reference to this map (e.g. keepQuantity logic
         * in InterfaceAdjacentHandler) automatically see updated values.
         */
        final Map<ItemStackKey, Long> totalCounts;

        ItemHandlerSlotCache(IItemHandler handler) {
            this.handler = handler;
            this.occupiedSlots = new HashMap<>();
            this.emptySlots = new ArrayList<>();
            this.totalCounts = new HashMap<>();

            int slots = handler.getSlots();
            for (int i = 0; i < slots; i++) {
                int limit = handler.getSlotLimit(i);
                ItemStack stack = handler.getStackInSlot(i);

                if (stack.isEmpty()) {
                    this.emptySlots.add(new SlotEntry(i, 0, limit));
                    continue;
                }

                ItemStackKey key = ItemStackKey.of(stack);
                if (key == null) continue;

                int count = stack.getCount();
                this.occupiedSlots.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new SlotEntry(i, count, limit));
                this.totalCounts.merge(key, (long) count, Long::sum);
            }
        }

        /** Mutable entry tracking a single slot's state within the cache. */
        static class SlotEntry {
            final int slotIndex;
            final int limit;
            int count; // mutable, updated after extract/insert operations

            SlotEntry(int slotIndex, int count, int limit) {
                this.slotIndex = slotIndex;
                this.count = count;
                this.limit = limit;
            }
        }
    }

    /**
     * Cached slot snapshot for the current auto-pull/push cycle.
     * Null when no cycle is active. Cleared by {@link #flushOperationCaches()}.
     */
    @Nullable
    private ItemHandlerSlotCache itemHandlerCache = null;

    /**
     * Get or lazily build the slot cache for the given IItemHandler.
     * If the handler differs from the cached one (different facing / different TE),
     * the cache is rebuilt from scratch.
     */
    private ItemHandlerSlotCache getOrBuildCache(IItemHandler handler) {
        if (this.itemHandlerCache != null && this.itemHandlerCache.handler == handler) {
            return this.itemHandlerCache;
        }

        this.itemHandlerCache = new ItemHandlerSlotCache(handler);
        return this.itemHandlerCache;
    }

    @Override
    protected void flushOperationCaches() {
        this.itemHandlerCache = null;
    }

    // ============================== Auto-Pull/Push capability methods (cached) ==============================

    @Override
    protected long countResourceInHandler(Object handler, ItemStackKey key, EnumFacing facing) {
        if (handler instanceof IItemRepository) {
            return ((IItemRepository) handler).getStoredItemCount(key.toStack(1));
        }

        if (!(handler instanceof IItemHandler)) return 0;

        // Use cached total counts when available (avoids O(N) slot scan per key)
        ItemHandlerSlotCache cache = getOrBuildCache((IItemHandler) handler);
        return cache.totalCounts.getOrDefault(key, 0L);
    }

    /**
     * Extract items from an adjacent IItemHandler using the slot cache.
     * Instead of scanning all N slots, jumps directly to slots known to contain
     * the target item. Updates the cache after each extraction so subsequent
     * filter iterations see accurate state.
     */
    @Override
    protected long extractResourceFromHandler(Object handler, ItemStackKey key, int maxAmount, EnumFacing facing) {
        if (handler instanceof IItemRepository) {
            ItemStack extracted = ((IItemRepository) handler).extractItem(key.toStack(1), maxAmount, false);
            return extracted.isEmpty() ? 0 : extracted.getCount();
        }

        if (!(handler instanceof IItemHandler)) return 0;

        IItemHandler itemHandler = (IItemHandler) handler;
        ItemHandlerSlotCache cache = getOrBuildCache(itemHandler);

        List<ItemHandlerSlotCache.SlotEntry> entries = cache.occupiedSlots.get(key);
        if (entries == null || entries.isEmpty()) return 0;

        long totalExtracted = 0;
        int remaining = maxAmount;

        for (int i = 0; i < entries.size() && remaining > 0; i++) {
            ItemHandlerSlotCache.SlotEntry entry = entries.get(i);
            if (entry.count <= 0) continue; // Depleted by a prior operation in this cycle

            ItemStack extracted = itemHandler.extractItem(entry.slotIndex, remaining, false);
            if (extracted.isEmpty()) continue;

            int extractedCount = extracted.getCount();
            totalExtracted += extractedCount;
            remaining -= extractedCount;
            entry.count -= extractedCount;
            cache.totalCounts.merge(key, -(long) extractedCount, Long::sum);

            // Slot emptied, make it available for future insertions
            if (entry.count <= 0) {
                cache.emptySlots.add(new ItemHandlerSlotCache.SlotEntry(
                        entry.slotIndex, 0, entry.limit));
            }
        }

        return totalExtracted;
    }

    /**
     * Insert items into an adjacent IItemHandler using the slot cache.
     * Phase 1: merge into existing slots that already contain the same item type.
     * Phase 2: fill empty slots.
     * Updates the cache after each insertion so subsequent filter iterations
     * see accurate state.
     */
    @Override
    protected long insertResourceIntoHandler(Object handler, ItemStack identity, int maxAmount, EnumFacing facing) {
        if (handler instanceof IItemRepository) {
            ItemStack toInsert = copyWithAmount(identity, maxAmount);
            ItemStack remainder = ((IItemRepository) handler).insertItem(toInsert, false);
            return maxAmount - (remainder.isEmpty() ? 0 : remainder.getCount());
        }

        if (!(handler instanceof IItemHandler)) return 0;

        IItemHandler itemHandler = (IItemHandler) handler;
        ItemHandlerSlotCache cache = getOrBuildCache(itemHandler);

        ItemStackKey key = ItemStackKey.of(identity);
        if (key == null) return 0;

        int remaining = maxAmount;

        // Phase 1: merge into existing partial slots with the same item
        List<ItemHandlerSlotCache.SlotEntry> entries = cache.occupiedSlots.get(key);
        if (entries != null) {
            for (int i = 0; i < entries.size() && remaining > 0; i++) {
                ItemHandlerSlotCache.SlotEntry entry = entries.get(i);
                if (entry.count >= entry.limit) continue; // Slot already full

                ItemStack toInsert = copyWithAmount(identity, remaining);
                ItemStack leftover = itemHandler.insertItem(entry.slotIndex, toInsert, false);
                int accepted = remaining - (leftover.isEmpty() ? 0 : leftover.getCount());

                if (accepted > 0) {
                    entry.count += accepted;
                    remaining -= accepted;
                    cache.totalCounts.merge(key, (long) accepted, Long::sum);
                }
            }
        }

        // Phase 2: fill empty slots
        Iterator<ItemHandlerSlotCache.SlotEntry> emptyIter = cache.emptySlots.iterator();
        while (emptyIter.hasNext() && remaining > 0) {
            ItemHandlerSlotCache.SlotEntry emptyEntry = emptyIter.next();

            ItemStack toInsert = copyWithAmount(identity, remaining);
            ItemStack leftover = itemHandler.insertItem(emptyEntry.slotIndex, toInsert, false);
            int accepted = remaining - (leftover.isEmpty() ? 0 : leftover.getCount());

            if (accepted > 0) {
                // Move from empty list to occupied map
                emptyIter.remove();
                ItemHandlerSlotCache.SlotEntry occupied = new ItemHandlerSlotCache.SlotEntry(
                        emptyEntry.slotIndex, accepted, emptyEntry.limit);
                cache.occupiedSlots.computeIfAbsent(key, k -> new ArrayList<>()).add(occupied);
                cache.totalCounts.merge(key, (long) accepted, Long::sum);
                remaining -= accepted;
            }
        }

        return maxAmount - remaining;
    }

    @Override
    @Nullable
    protected Map<ItemStackKey, Long> buildResourceCountMap(Object handler, EnumFacing facing) {
        // There is no worry about overflow here since capabilities are limited to int amounts
        if (handler instanceof IItemRepository) {
            Map<ItemStackKey, Long> map = new HashMap<>();
            for (IItemRepository.ItemRecord record : ((IItemRepository) handler).getAllItems()) {
                ItemStackKey key = ItemStackKey.of(record.itemPrototype);
                if (key != null) map.merge(key, (long) record.count, Long::sum);
            }

            return map;
        }

        if (handler instanceof IItemHandler) {
            // Build the slot cache (side effect: populates totalCounts). Subsequent
            // extract/insert calls within this cycle will reuse the cached state.
            // Returns the live totalCounts map so keepQuantity calculations
            // automatically see post-mutation values from earlier filter iterations.
            ItemHandlerSlotCache cache = getOrBuildCache((IItemHandler) handler);
            return cache.totalCounts;
        }

        return null;
    }

    // ============================== Item-specific stream serialization ==============================

    /**
     * Items use compressed NBT for stream serialization since they can have complex NBT data.
     * Length-prefixed so the reader can always consume the correct number of bytes.
     */
    @Override
    protected void writeResourceToStream(ItemStack resource, ByteBuf data) {
        try {
            NBTTagCompound tag = resource.writeToNBT(new NBTTagCompound());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CompressedStreamTools.writeCompressed(tag, baos);
            byte[] nbtBytes = baos.toByteArray();

            data.writeInt(nbtBytes.length);
            data.writeBytes(nbtBytes);
        } catch (Exception e) {
            // Write zero-length marker on error so the reader stays in sync
            data.writeInt(0);
        }
    }

    /**
     * Read a compressed-NBT-encoded ItemStack from the stream.
     * @return The item, or null if data is corrupted or empty.
     */
    @Override
    @Nullable
    protected ItemStack readResourceFromStream(ByteBuf data) {
        int nbtLen = data.readInt();
        if (nbtLen <= 0) return null;

        byte[] nbtBytes = new byte[nbtLen];
        data.readBytes(nbtBytes);

        try {
            NBTTagCompound tag = CompressedStreamTools.readCompressed(new ByteArrayInputStream(nbtBytes));
            ItemStack stack = new ItemStack(tag);
            return stack.isEmpty() ? null : stack;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================== Item-specific NBT format ==============================

    /**
     * Override to handle legacy NBT formats for filters (Items TAG_LIST).
     */
    @Override
    protected void readFiltersFromNBT(NBTTagCompound data, String name) {
        if (!data.hasKey(name, Constants.NBT.TAG_COMPOUND)) return;

        NBTTagCompound filterData = data.getCompoundTag(name);

        // Legacy TAG_LIST format (AE2 style "Items")
        if (filterData.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList tagList = filterData.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound itemTag = tagList.getCompoundTagAt(i);
                int slot = itemTag.getInteger("Slot");
                if (slot >= 0 && slot < FILTER_SLOTS) {
                    ItemStack stack = new ItemStack(itemTag);
                    this.inventoryManager.setFilterDirect(slot, stack.isEmpty() ? null : stack);
                }
            }
            return;
        }

        // New format - delegate to super
        super.readFiltersFromNBT(data, name);
    }

    /**
     * Override to handle legacy NBT formats for storage (Items TAG_LIST).
     * Falls back to super for new numeric key format.
     */
    @Override
    protected void readStorageFromNBT(NBTTagCompound data) {
        // Try standard key first
        String storageKey = getStorageNBTKey();
        if (data.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) {
            super.readStorageFromNBT(data);
            return;
        }

        // Legacy: Try TAG_LIST "Items" directly in data
        if (data.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList tagList = data.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound itemTag = tagList.getCompoundTagAt(i);
                int slot = itemTag.getInteger("Slot");
                if (slot >= 0 && slot < STORAGE_SLOTS) {
                    ItemStack stack = new ItemStack(itemTag);
                    this.setResourceInSlotWithAmount(slot, stack.isEmpty() ? null : stack, stack.isEmpty() ? 0 : stack.getCount());
                }
            }
        }
    }

    // ============================== Item-specific insertion (slotless) ==============================

    /**
     * Slotless insertion logic that ignores item's maxStackSize.
     * Delegates to the base class {@link #receiveFiltered} and converts return type.
     * <p>
     * Handles overflow and trash-unselected upgrade cards via the base class.
     */
    private ItemStack slotlessInsertItem(@Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        int accepted = receiveFiltered(stack, !simulate);
        if (accepted >= stack.getCount()) return ItemStack.EMPTY;

        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    // ============================== Item-specific NBT (legacy migration) ==============================

    /**
     * Read logic state from NBT with legacy key migration for tiles/parts.
     * Tiles used "inv" for storage, parts used "storage".
     * 
     * @param data The NBT data
     * @param isTile true if called from a tile entity, false for parts
     */
    public void readFromNBT(NBTTagCompound data, boolean isTile) {
        // Try legacy storage key BEFORE calling super which reads new key
        String legacyStorageKey = isTile ? "inv" : "storage";
        if (data.hasKey(legacyStorageKey, Constants.NBT.TAG_COMPOUND)) {
            readLegacyStorage(data.getCompoundTag(legacyStorageKey));
        }

        // Try legacy filter key "filter"
        if (data.hasKey("filter", Constants.NBT.TAG_COMPOUND)) {
            readFiltersFromNBT(data, "filter");
        }

        // Delegate to super for standard format (reads new keys, upgrades, etc.)
        super.readFromNBT(data);
    }

    /**
     * Read legacy storage format (supports Items TAG_LIST, numeric map, and AE2 "itemX" formats).
     */
    private void readLegacyStorage(NBTTagCompound storageMap) {
        // TAG_LIST format (AppEngInternalInventory style)
        if (storageMap.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList tagList = storageMap.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                NBTTagCompound itemTag = tagList.getCompoundTagAt(i);
                int slot = itemTag.getInteger("Slot");
                if (slot >= 0 && slot < STORAGE_SLOTS) {
                    ItemStack stack = new ItemStack(itemTag);
                    this.setResourceInSlotWithAmount(slot, stack.isEmpty() ? null : stack, stack.isEmpty() ? 0 : stack.getCount());
                }
            }
            return;
        }

        // Map format - try both numeric keys ("0", "1") and AE2 format ("item0", "item1")
        for (String slotKey : storageMap.getKeySet()) {
            int slot;

            // Try numeric key first (our new format)
            try {
                slot = Integer.parseInt(slotKey);
            } catch (NumberFormatException e) {
                // Try AE2's "itemX" format
                if (slotKey.startsWith("item")) {
                    try {
                        slot = Integer.parseInt(slotKey.substring(4));
                    } catch (NumberFormatException e2) {
                        continue;
                    }
                } else {
                    continue;
                }
            }

            if (slot >= 0 && slot < STORAGE_SLOTS) {
                ItemStack stack = new ItemStack(storageMap.getCompoundTag(slotKey));
                this.setResourceInSlotWithAmount(slot, stack.isEmpty() ? null : stack, stack.isEmpty() ? 0 : stack.getCount());
            }
        }
    }

    // ============================== External handlers ==============================

    /**
     * Wrapper handler that provides slotless insertion of filtered items.
     * Items are automatically routed to the appropriate slot based on filters.
     * Does not allow extraction (import-only interface).
     * <p>
     * Note: Exposes 1 dummy slot that's always empty because Forge's hopper code
     * uses a broken "isFull" check that compares stack count to ItemStack.getMaxStackSize()
     * instead of IItemHandler.getSlotLimit(). The dummy slot ensures hoppers see the
     * inventory as "not full" and attempt insertion, which our slotless logic handles.
     */
    private static class FilteredStorageHandler implements IItemHandler, IItemRepository {
        private final ItemInterfaceLogic logic;

        public FilteredStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            // Expose 1 dummy slot so hoppers see an empty slot and don't think we're full.
            return 1 + logic.getFilterSlotList().size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            // Slot 0 is the dummy slot - always empty
            if (slot <= 0) return ItemStack.EMPTY;

            // Slots 1 through filterSlotList.size() are actual filter slots
            int filterIndex = slot - 1;
            if (filterIndex >= logic.getFilterSlotList().size()) return ItemStack.EMPTY;

            int storageSlot = logic.getFilterSlotList().get(filterIndex);
            ItemStack identity = logic.getStorageIdentity(storageSlot);
            if (identity == null) return ItemStack.EMPTY;

            long amount = logic.getSlotAmount(storageSlot);
            if (amount <= 0) return ItemStack.EMPTY;

            // Create stack with actual amount, clamped to int for API
            return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Delegate to the logic's slotless insertion
            return logic.slotlessInsertItem(stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Import interface does not allow external extraction
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return logic.inventoryManager.getMaxSlotSizeInt();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // For slotless operation, we check if ANY filter accepts this item
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            return logic.containsFilterKey(key);
        }

        // ============================== IItemRepository (bulk slotless access) ==============================

        @Nonnull
        @Override
        public NonNullList<ItemRecord> getAllItems() {
            NonNullList<ItemRecord> items = NonNullList.create();

            for (int filterIdx : logic.getFilterSlotList()) {
                ItemStack identity = logic.getStorageIdentity(filterIdx);
                if (identity == null) continue;

                long amount = logic.getSlotAmount(filterIdx);
                if (amount <= 0) continue;

                // Prototype with count=1, actual count from amounts[]
                ItemStack prototype = identity.copy();
                prototype.setCount(1);
                // Clamp to int for ItemRecord API
                items.add(new ItemRecord(prototype, (int) Math.min(amount, Integer.MAX_VALUE)));
            }

            return items;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(@Nonnull ItemStack stack, boolean simulate, Predicate<ItemStack> predicate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;
            if (predicate != null && !predicate.test(stack)) return stack;

            return logic.slotlessInsertItem(stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(@Nonnull ItemStack stack, int amount, boolean simulate, Predicate<ItemStack> predicate) {
            // Import interface does not allow external extraction
            return ItemStack.EMPTY;
        }
    }

    /**
     * Wrapper handler that exposes storage slots for extraction only.
     * Does not allow external insertion (export-only interface).
     * <p>
     * Updated for parallel amounts array: storage[] holds identity (count=1),
     * amounts[] holds actual count as long.
     */
    private static class ExportStorageHandler implements IItemHandler, IItemRepository {
        private final ItemInterfaceLogic logic;

        public ExportStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            return logic.getFilterSlotList().size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= logic.getFilterSlotList().size()) return ItemStack.EMPTY;

            int storageSlot = logic.getFilterSlotList().get(slot);
            ItemStack identity = logic.getStorageIdentity(storageSlot);
            if (identity == null) return ItemStack.EMPTY;

            long amount = logic.getSlotAmount(storageSlot);
            if (amount <= 0) return ItemStack.EMPTY;

            // Create stack with actual amount, clamped to int for API
            return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // Export interface does not allow external insertion
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= logic.getFilterSlotList().size()) return ItemStack.EMPTY;

            int storageSlot = logic.getFilterSlotList().get(slot);
            ItemStack result = logic.drainFromSlot(storageSlot, amount, !simulate);
            return result != null ? result : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return logic.inventoryManager.getMaxSlotSizeInt();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // External insertion not allowed
            return false;
        }

        // ============================== IItemRepository (bulk slotless access) ==============================

        @Nonnull
        @Override
        public NonNullList<ItemRecord> getAllItems() {
            NonNullList<ItemRecord> items = NonNullList.create();

            for (int filterIdx : logic.getFilterSlotList()) {
                ItemStack identity = logic.getStorageIdentity(filterIdx);
                if (identity == null) continue;

                long amount = logic.getSlotAmount(filterIdx);
                if (amount <= 0) continue;

                // Prototype with count=1, actual count from amounts[]
                ItemStack prototype = identity.copy();
                prototype.setCount(1);
                // Clamp to int for ItemRecord API
                items.add(new ItemRecord(prototype, (int) Math.min(amount, Integer.MAX_VALUE)));
            }

            return items;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(@Nonnull ItemStack stack, boolean simulate, Predicate<ItemStack> predicate) {
            // Export interface does not allow external insertion
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(@Nonnull ItemStack stack, int amount, boolean simulate, Predicate<ItemStack> predicate) {
            if (stack.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            if (predicate != null && !predicate.test(stack)) return ItemStack.EMPTY;

            // O(1) lookup via filter map instead of slot iteration
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return ItemStack.EMPTY;

            Integer targetSlot = logic.getFilterSlotForKey(key);
            if (targetSlot == null) return ItemStack.EMPTY;

            ItemStack result = logic.drainFromSlot(targetSlot, amount, !simulate);
            return result != null ? result : ItemStack.EMPTY;
        }
    }

    /**
     * IItemHandlerModifiable wrapper that provides GUI-facing access to interface slots.
     * Uses accessor methods rather than direct array references for proper encapsulation.
     * Converts null entries to ItemStack.EMPTY for IItemHandler API compatibility.
     */
    private class ArrayItemHandler implements IItemHandlerModifiable {
        private final int slotCount;
        private final boolean isGhostSlot;

        /**
         * @param slotCount The number of slots this handler exposes
         * @param isGhostSlot If true, stacks are always copied and set to count 1 (filter behavior)
         */
        public ArrayItemHandler(int slotCount, boolean isGhostSlot) {
            this.slotCount = slotCount;
            this.isGhostSlot = isGhostSlot;
        }

        @Override
        public int getSlots() {
            return slotCount;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= slotCount) return ItemStack.EMPTY;

            if (isGhostSlot) {
                // Ghost slots always return the filter identity with count 1
                ItemStack filter = getRawFilter(slot);
                return filter != null ? filter : ItemStack.EMPTY;
            } else {
                // Storage slots: combine identity with amount (capped at int max)
                ItemStack identity = getStorageIdentity(slot);
                if (identity == null) return ItemStack.EMPTY;

                long amount = getSlotAmount(slot);
                if (amount <= 0) return ItemStack.EMPTY;

                ItemStack result = identity.copy();
                result.setCount((int) Math.min(amount, Integer.MAX_VALUE));
                return result;
            }
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            if (slot < 0 || slot >= slotCount) return;

            // Convert EMPTY to null for consistency with fluid/gas pattern
            if (stack.isEmpty()) {
                if (isGhostSlot) {
                    inventoryManager.setFilterDirect(slot, null);
                    onFilterChanged(slot);
                } else {
                    setResourceInSlotWithAmount(slot, null, 0);
                    host.markDirtyAndSave();
                }
                return;
            }

            if (isGhostSlot) {
                // Ghost slots store only a single item as a filter template
                ItemStack ghost = stack.copy();
                ghost.setCount(1);
                inventoryManager.setFilterDirect(slot, ghost);
                onFilterChanged(slot);
            } else {
                // Storage slots: store identity (count=1) and amount separately
                // Note: ItemStack.getCount() returns int, but we preserve the full value
                // since setStackInSlot is only called from GUI which caps at int anyway.
                // For values > int max, use adjustSlotAmount() instead.
                ItemStack identity = stack.copy();
                long amount = identity.getCount();
                identity.setCount(1);
                setResourceInSlotWithAmount(slot, identity, amount);
                host.markDirtyAndSave();
            }
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // Direct insertion via IItemHandler is not supported - use setStackInSlot for ghost slots
            // or the logic's slotless insertion for storage
            return stack;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Direct extraction via IItemHandler is not supported
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return isGhostSlot ? 1 : inventoryManager.getMaxSlotSizeInt();
        }
    }
}
