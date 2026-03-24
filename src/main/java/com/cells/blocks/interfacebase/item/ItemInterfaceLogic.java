package com.cells.blocks.interfacebase.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
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

    /** IItemHandler wrapper for filter array access. */
    private final IItemHandlerModifiable filterHandler;

    /** IItemHandler wrapper for storage array access. */
    private final IItemHandlerModifiable storageHandler;

    /** External handler exposed via capabilities. */
    private final IItemHandler externalHandler;

    public ItemInterfaceLogic(Host host) {
        super(host, ItemStack.class);

        this.maxSlotSize = DEFAULT_MAX_SLOT_SIZE;

        // Create IItemHandler wrappers for the static arrays
        this.filterHandler = new ArrayItemHandler(this.filters, true);
        this.storageHandler = new ArrayItemHandler(this.storage, false);

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
     * @return IItemHandler wrapper for filter access.
     */
    public IItemHandlerModifiable getFilterInventory() {
        return this.filterHandler;
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
     * @return The filter array directly. Wrapping in IItemHandler is caller's responsibility if needed.
     */
    public ItemStack[] getFilterArray() {
        return this.filters;
    }

    /**
     * @return The storage array directly. Wrapping in IItemHandler is caller's responsibility if needed.
     */
    public ItemStack[] getStorageArray() {
        return this.storage;
    }

    /**
     * Get filter at a specific slot (API compatible with EMPTY).
     * @return The filter ItemStack, or null if slot is empty or invalid
     */
    @Nullable
    public ItemStack getFilter(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return null;
        return this.filters[slot];
    }

    /**
     * Set filter at a specific slot. Converts EMPTY to null internally.
     */
    public void setFilter(int slot, @Nullable ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        this.filters[slot] = (stack == null || stack.isEmpty()) ? null : stack;
        onFilterChanged(slot);
    }

    /**
     * Get storage at a specific slot.
     * @return The stored ItemStack, or null if slot is empty or invalid
     */
    @Nullable
    public ItemStack getStorage(int slot) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return null;
        return this.storage[slot];
    }

    /**
     * Set storage at a specific slot. Converts EMPTY to null internally.
     */
    public void setStorage(int slot, @Nullable ItemStack stack) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;
        this.storage[slot] = (stack == null || stack.isEmpty()) ? null : stack;
    }

    /**
     * Check if a stack is in the filter.
     * Delegates to isResourceInFilter from base class.
     */
    public boolean isStackInFilter(@Nullable ItemStack stack) {
        return isResourceInFilter(stack);
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     * Import uses filter-to-slot mapping; export checks direct filter match.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return false;
        if (stack == null || stack.isEmpty()) return false;

        ItemStackKey filterKey = this.slotToFilterMap.get(slot);
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
    protected ItemStack createRecoveryItem(ItemStack resource) {
        // Items ARE items - return the resource directly as the drop
        return resource.copy();
    }

    // ============================== Item-specific stream serialization ==============================

    /**
     * Items need NBT-aware stream serialization since they can have complex NBT data.
     * Override to use NBT serialization instead of name-based.
     */
    @Override
    public boolean readStorageFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all storage first
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null) {
                this.storage[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int nbtLen = data.readInt();

            if (slot < 0 || slot >= STORAGE_SLOTS) {
                // Skip this entry's NBT data
                data.skipBytes(nbtLen);
                continue;
            }

            byte[] nbtBytes = new byte[nbtLen];
            data.readBytes(nbtBytes);

            try {
                NBTTagCompound tag = net.minecraft.nbt.CompressedStreamTools.readCompressed(
                    new java.io.ByteArrayInputStream(nbtBytes)
                );
                ItemStack stack = new ItemStack(tag);
                if (!stack.isEmpty()) {
                    this.storage[slot] = stack;
                    changed = true;
                }
            } catch (Exception e) {
                // Log and skip corrupted data
            }
        }

        return changed;
    }

    /**
     * Items need NBT-aware stream serialization since they can have complex NBT data.
     */
    @Override
    public void writeStorageToStream(ByteBuf data) {
        // Count non-empty storage slots first
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null && !this.storage[i].isEmpty()) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack stack = this.storage[i];
            if (stack == null || stack.isEmpty()) continue;

            data.writeShort(i);

            try {
                NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(tag, baos);
                byte[] nbtBytes = baos.toByteArray();

                data.writeInt(nbtBytes.length);
                data.writeBytes(nbtBytes);
            } catch (Exception e) {
                // Write empty on error
                data.writeInt(0);
            }
        }
    }

    /**
     * Items need NBT-aware filter stream serialization.
     */
    @Override
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all filters first
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) {
                this.filters[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int nbtLen = data.readInt();

            if (slot < 0 || slot >= FILTER_SLOTS) {
                data.skipBytes(nbtLen);
                continue;
            }

            byte[] nbtBytes = new byte[nbtLen];
            data.readBytes(nbtBytes);

            try {
                NBTTagCompound tag = net.minecraft.nbt.CompressedStreamTools.readCompressed(
                    new java.io.ByteArrayInputStream(nbtBytes)
                );
                ItemStack stack = new ItemStack(tag);
                if (!stack.isEmpty()) {
                    stack.setCount(1); // Filters are always count 1
                    this.filters[slot] = stack;
                    changed = true;
                }
            } catch (Exception e) {
                // Log and skip corrupted data
            }
        }

        this.refreshFilterMap();
        return changed;
    }

    /**
     * Items need NBT-aware filter stream serialization.
     */
    @Override
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        int count = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack filter = this.filters[i];
            if (filter == null) continue;

            data.writeShort(i);

            try {
                NBTTagCompound tag = filter.writeToNBT(new NBTTagCompound());
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(tag, baos);
                byte[] nbtBytes = baos.toByteArray();

                data.writeInt(nbtBytes.length);
                data.writeBytes(nbtBytes);
            } catch (Exception e) {
                data.writeInt(0);
            }
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
                    this.filters[slot] = stack.isEmpty() ? null : stack;
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
                    this.storage[slot] = stack.isEmpty() ? null : stack;
                }
            }
        }
    }

    // ============================== Item-specific insertion (slotless) ==============================

    /**
     * Slotless insertion logic that ignores item's maxStackSize.
     * Finds the correct slot via {@link #filterToSlotMap} using {@link ItemStackKey}.
     * <p>
     * Handles overflow and trash-unselected upgrade cards:
     * - If no filter matches and trash-unselected is installed, the item is voided.
     * - If the slot is full and overflow is installed, excess items are voided.
     */
    private ItemStack slotlessInsertItem(@Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        // Find the correct slot from the filter map
        ItemStackKey key = ItemStackKey.of(stack);
        if (key == null) return stack;

        // No matching filter — void if trash-unselected upgrade is installed, otherwise reject
        Integer targetSlot = this.filterToSlotMap.get(key);
        if (targetSlot == null) return this.installedTrashUnselectedUpgrade ? ItemStack.EMPTY : stack;

        int limit = this.maxSlotSize;
        ItemStack existing = this.storage[targetSlot];

        if (existing != null) {
            // Verify the existing stack matches (guards against orphaned items in the slot)
            if (!key.equals(ItemStackKey.of(existing))) return stack;

            // Slot is full — void excess if overflow upgrade is installed
            int space = limit - existing.getCount();
            if (space <= 0) return this.installedOverflowUpgrade ? ItemStack.EMPTY : stack;

            int toInsert = Math.min(stack.getCount(), space);
            if (!simulate) {
                existing.grow(toInsert);
                this.host.markDirtyAndSave();
                this.host.markForNetworkUpdate();
            }

            if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

            ItemStack remainder = stack.copy();
            remainder.shrink(toInsert);
            // Void any remainder if overflow upgrade is installed
            return this.installedOverflowUpgrade ? ItemStack.EMPTY : remainder;
        } else {
            int toInsert = Math.min(stack.getCount(), limit);
            if (!simulate) {
                ItemStack newStack = stack.copy();
                newStack.setCount(toInsert);
                this.storage[targetSlot] = newStack;
                this.host.markDirtyAndSave();
                this.host.markForNetworkUpdate();
            }

            if (toInsert >= stack.getCount()) return ItemStack.EMPTY;

            ItemStack remainder = stack.copy();
            remainder.shrink(toInsert);
            // Void any remainder if overflow upgrade is installed
            return this.installedOverflowUpgrade ? ItemStack.EMPTY : remainder;
        }
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
                    this.storage[slot] = stack.isEmpty() ? null : stack;
                }
            }
            return;
        }

        // Map format - try both numeric keys ("0", "1") and AE2 format ("item0", "item1")
        for (String slotKey : storageMap.getKeySet()) {
            int slot = -1;

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
                this.storage[slot] = stack.isEmpty() ? null : stack;
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
    private static class FilteredStorageHandler implements IItemHandler {
        private final ItemInterfaceLogic logic;

        public FilteredStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            // Expose 1 dummy slot so hoppers see an empty slot and don't think we're full.
            return 1 + logic.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            // Slot 0 is the dummy slot - always empty
            if (slot <= 0) return ItemStack.EMPTY;

            // Slots 1 through filterSlotList.size() are actual filter slots
            int filterIndex = slot - 1;
            if (filterIndex >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(filterIndex);
            ItemStack stack = logic.storage[storageSlot];
            return stack != null ? stack : ItemStack.EMPTY;
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
            return logic.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // For slotless operation, we check if ANY filter accepts this item
            ItemStackKey key = ItemStackKey.of(stack);
            if (key == null) return false;

            return logic.filterToSlotMap.containsKey(key);
        }
    }

    /**
     * Wrapper handler that exposes storage slots for extraction only.
     * Does not allow external insertion (export-only interface).
     */
    private static class ExportStorageHandler implements IItemHandler {
        private final ItemInterfaceLogic logic;

        public ExportStorageHandler(ItemInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public int getSlots() {
            return logic.filterSlotList.size();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(slot);
            ItemStack stack = logic.storage[storageSlot];
            return stack != null ? stack : ItemStack.EMPTY;
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
            if (slot < 0 || slot >= logic.filterSlotList.size()) return ItemStack.EMPTY;

            int storageSlot = logic.filterSlotList.get(slot);
            ItemStack stack = logic.storage[storageSlot];
            if (stack == null) return ItemStack.EMPTY;

            int toExtract = Math.min(amount, stack.getCount());
            ItemStack result = stack.copy();
            result.setCount(toExtract);

            if (!simulate) {
                stack.shrink(toExtract);
                if (stack.getCount() <= 0) logic.storage[storageSlot] = null;
                logic.host.markDirtyAndSave();
                logic.host.markForNetworkUpdate();
            }

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return logic.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // External insertion not allowed
            return false;
        }
    }

    /**
     * Simple IItemHandlerModifiable wrapper around a static ItemStack[] array.
     * Used to expose the filter and storage arrays as IItemHandler for GUI slots.
     * Converts null entries to ItemStack.EMPTY for IItemHandler API compatibility.
     */
    private class ArrayItemHandler implements IItemHandlerModifiable {
        private final ItemStack[] array;
        private final boolean isGhostSlot;

        /**
         * @param array The underlying ItemStack array (may contain null entries)
         * @param isGhostSlot If true, stacks are always copied and set to count 1 (filter behavior)
         */
        public ArrayItemHandler(ItemStack[] array, boolean isGhostSlot) {
            this.array = array;
            this.isGhostSlot = isGhostSlot;
        }

        @Override
        public int getSlots() {
            return array.length;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= array.length) return ItemStack.EMPTY;
            ItemStack stack = array[slot];
            return stack != null ? stack : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            if (slot < 0 || slot >= array.length) return;

            // Convert EMPTY to null for consistency with fluid/gas pattern
            if (stack.isEmpty()) {
                array[slot] = null;

                if (isGhostSlot) onFilterChanged(slot);
                return;
            }

            if (isGhostSlot) {
                // Ghost slots store only a single item as a filter template
                ItemStack ghost = stack.copy();
                ghost.setCount(1);
                array[slot] = ghost;
                onFilterChanged(slot);
            } else {
                array[slot] = stack;
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
            return isGhostSlot ? 1 : maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return true;
        }
    }
}
