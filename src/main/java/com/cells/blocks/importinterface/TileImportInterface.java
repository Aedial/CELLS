package com.cells.blocks.importinterface;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;


/**
 * Tile entity for the Import Interface block.
 * Provides 36 filter slots (ghost items) and 36 storage slots.
 * Only accepts items that match the filter in the corresponding slot.
 * Automatically imports stored items into the ME network.
 */
public class TileImportInterface extends AENetworkInvTile implements IGridTickable, IAEAppEngInventory {

    public static final int FILTER_SLOTS = 36; // 36 filter slots (ghost items)
    public static final int STORAGE_SLOTS = 36; // 36 storage slots (actual items)
    public static final int DEFAULT_MAX_SLOT_SIZE = 64;
    public static final int MIN_MAX_SLOT_SIZE = 1;
    public static final int MAX_MAX_SLOT_SIZE = Integer.MAX_VALUE;

    // Filter inventory - ghost items only (1 stack size each)
    private final AppEngInternalInventory filterInventory = new AppEngInternalInventory(this, FILTER_SLOTS, 1);

    // Storage inventory - actual items
    private final AppEngInternalInventory storageInventory;

    // Wrapper that exposes storage slots with filter checking
    private final FilteredStorageHandler filteredHandler;

    // Max slot size for all storage slots
    private int maxSlotSize = DEFAULT_MAX_SLOT_SIZE;

    // Action source for network operations
    private final IActionSource actionSource;

    public TileImportInterface() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
        this.actionSource = new MachineSource(this);

        // Create storage inventory with filter
        this.storageInventory = new AppEngInternalInventory(this, STORAGE_SLOTS, DEFAULT_MAX_SLOT_SIZE) {
            @Override
            public int getSlotLimit(int slot) {
                return TileImportInterface.this.maxSlotSize;
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return TileImportInterface.this.isItemValidForSlot(slot, stack);
            }
        };

        this.filteredHandler = new FilteredStorageHandler(this);
    }

    /**
     * Check if an item is valid for a specific storage slot based on the filter.
     */
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.storageInventory.getSlots()) return false;

        ItemStack filterStack = ItemStack.EMPTY;

        if (slot < this.filterInventory.getSlots()) {
            filterStack = this.filterInventory.getStackInSlot(slot);
        }

        if (filterStack.isEmpty()) return false; // No filter set = no items accepted

        return ItemStack.areItemsEqual(filterStack, stack) &&
               ItemStack.areItemStackTagsEqual(filterStack, stack);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.filterInventory.readFromNBT(data, "filter");
        this.storageInventory.readFromNBT(data, "storage");
        this.maxSlotSize = data.getInteger("maxSlotSize");

        if (this.maxSlotSize < MIN_MAX_SLOT_SIZE) this.maxSlotSize = MIN_MAX_SLOT_SIZE;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.filterInventory.writeToNBT(data, "filter");
        this.storageInventory.writeToNBT(data, "storage");
        data.setInteger("maxSlotSize", this.maxSlotSize);

        return data;
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        return super.readFromStream(data);
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return this.storageInventory;
    }

    /**
     * Get the filter inventory (ghost items).
     */
    public AppEngInternalInventory getFilterInventory() {
        return this.filterInventory;
    }

    /**
     * Get the storage inventory.
     */
    public AppEngInternalInventory getStorageInventory() {
        return this.storageInventory;
    }

    public int getMaxSlotSize() {
        return this.maxSlotSize;
    }

    public void setMaxSlotSize(int size) {
        this.maxSlotSize = Math.max(MIN_MAX_SLOT_SIZE, size);
        this.markDirty();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.storageInventory && !added.isEmpty()) {
            // Wake up the tile to import items
            try {
                this.getProxy().getTick().alertDevice(this.getProxy().getNode());
            } catch (GridAccessException e) {
                // Not connected to grid
            }
        }

        this.markDirty();
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        // Drop all stored items
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            ItemStack stack = this.storageInventory.getStackInSlot(i);
            if (!stack.isEmpty()) drops.add(stack);
        }
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // IGridTickable implementation

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(
            TickRates.Interface.getMin(),
            TickRates.Interface.getMax(),
            !hasWorkToDo(),
            true
        );
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) return TickRateModulation.SLEEP;

        boolean didWork = importItems();
        return didWork ? TickRateModulation.FASTER : (hasWorkToDo() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP);
    }

    private boolean hasWorkToDo() {
        for (int i = 0; i < this.storageInventory.getSlots(); i++) {
            if (!this.storageInventory.getStackInSlot(i).isEmpty()) return true;
        }

        return false;
    }

    /**
     * Import items from storage slots into the ME network.
     * @return true if any items were imported
     */
    private boolean importItems() {
        boolean didWork = false;

        try {
            IStorageGrid storage = this.getProxy().getStorage();
            IMEInventory<IAEItemStack> itemStorage = storage.getInventory(
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
            );

            for (int i = 0; i < this.storageInventory.getSlots(); i++) {
                ItemStack stack = this.storageInventory.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
                if (aeStack == null) continue;

                // Try to insert into network
                IAEItemStack remaining = itemStorage.injectItems(aeStack, Actionable.MODULATE, this.actionSource);

                if (remaining == null) {
                    // All items inserted
                    this.storageInventory.setStackInSlot(i, ItemStack.EMPTY);
                    didWork = true;
                } else if (remaining.getStackSize() < stack.getCount()) {
                    // Some items inserted
                    ItemStack newStack = stack.copy();
                    newStack.setCount((int) remaining.getStackSize());
                    this.storageInventory.setStackInSlot(i, newStack);
                    didWork = true;
                }
                // else: nothing inserted, network full
            }
        } catch (GridAccessException e) {
            // Not connected to grid
        }

        return didWork;
    }

    // Capability handling

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return true;

        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.filteredHandler);
        }

        return super.getCapability(capability, facing);
    }

    /**
     * Wrapper handler that provides slotless insertion of filtered items.
     * Items are automatically routed to the appropriate slot based on filters.
     * Does not allow extraction (import-only interface).
     */
    private static class FilteredStorageHandler implements IItemHandler {
        private final TileImportInterface tile;

        public FilteredStorageHandler(TileImportInterface tile) {
            this.tile = tile;
        }

        @Override
        public int getSlots() {
            // Return actual slot count - external systems can query individual slots if needed,
            // but primary insertion path is via slotless routing through insertItem(0, stack, simulate)
            // TODO: should we return exactly the number of slots with filters set
            return tile.storageInventory.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return tile.storageInventory.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Slotless insertion: find any slot that can accept this item based on filters
            // This is more flexible than slot-specific insertion and works better with pipes/hoppers
            return insertItemSlotless(stack, simulate);
        }

        /**
         * Insert an item into any available slot that has a matching filter.
         * Prioritizes slots that already contain the same item type, then empty filtered slots.
         */
        private ItemStack insertItemSlotless(@Nonnull ItemStack stack, boolean simulate) {
            ItemStack remaining = stack.copy();

            // TODO: can probably assume no 2 slots are the same item and just have an item -> slot mapping

            // First pass: try to fill existing stacks with matching items
            for (int i = 0; i < tile.storageInventory.getSlots() && !remaining.isEmpty(); i++) {
                if (!tile.isItemValidForSlot(i, remaining)) continue;

                ItemStack existing = tile.storageInventory.getStackInSlot(i);
                if (existing.isEmpty()) continue;

                if (!ItemStack.areItemsEqual(existing, remaining) ||
                    !ItemStack.areItemStackTagsEqual(existing, remaining)) continue;

                int space = tile.maxSlotSize - existing.getCount();
                if (space <= 0) continue;

                int toInsert = Math.min(remaining.getCount(), space);
                if (!simulate) existing.grow(toInsert);

                remaining.shrink(toInsert);
            }

            // Second pass: try to fill empty slots with matching filters
            for (int i = 0; i < tile.storageInventory.getSlots() && !remaining.isEmpty(); i++) {
                if (!tile.isItemValidForSlot(i, remaining)) continue;

                ItemStack existing = tile.storageInventory.getStackInSlot(i);
                if (!existing.isEmpty()) continue;

                int toInsert = Math.min(remaining.getCount(), tile.maxSlotSize);

                if (!simulate) {
                    ItemStack newStack = remaining.copy();
                    newStack.setCount(toInsert);
                    tile.storageInventory.setStackInSlot(i, newStack);
                }

                remaining.shrink(toInsert);
            }

            return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Import interface does not allow external extraction
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return tile.maxSlotSize;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            // For slotless operation, we check if ANY filter accepts this item
            for (int i = 0; i < tile.filterInventory.getSlots(); i++) {
                if (tile.isItemValidForSlot(i, stack)) return true;
            }

            return false;
        }
    }
}
