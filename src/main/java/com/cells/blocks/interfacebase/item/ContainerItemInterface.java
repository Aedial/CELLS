package com.cells.blocks.interfacebase.item;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.gui.slots.PagedItemHandler;
import com.cells.network.sync.ResourceType;
import com.cells.util.ItemStackKey;


/**
 * Container for Item Import/Export Interface GUIs.
 * <p>
 * Extends {@link AbstractContainerInterface} with item-specific implementations.
 * Filter slots are custom GUI slots ({@link com.cells.gui.slots.ItemFilterSlot}) with unified PacketResourceSlot sync.
 * Storage slots use {@link com.cells.gui.slots.ItemStorageSlot} for actual item manipulation.
 * <p>
 * Layout: 4 rows of slots (filter above storage), 4 upgrade slots on the right.
 */
public class ContainerItemInterface
    extends AbstractContainerInterface<IAEItemStack, ItemStackKey, IItemInterfaceHost> {

    /** Paged handler for storage inventory (actual items). */
    private final PagedItemHandler pagedStorageHandler;

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerItemInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IItemInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerItemInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IItemInterfaceHost) part, part);
    }

    /**
     * Common constructor.
     */
    private ContainerItemInterface(final InventoryPlayer ip, final IItemInterfaceHost host, final Object anchor) {
        super(ip, host, anchor, ItemInterfaceLogic.DEFAULT_MAX_SLOT_SIZE);

        // Create paged storage handler for GUI use (item-specific)
        this.pagedStorageHandler = new PagedItemHandler(
            host.getStorageInventory(),
            ItemInterfaceLogic.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );
    }

    // ================================= Accessors =================================

    /**
     * @return The paged storage handler for GUI use.
     */
    public PagedItemHandler getPagedStorageHandler() {
        return this.pagedStorageHandler;
    }

    // ================================= Abstract Implementations =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.ITEM;
    }

    @Override
    @Nullable
    protected ItemStackKey createKey(@Nullable IAEItemStack stack) {
        if (stack == null) return null;
        return ItemStackKey.of(stack.getDefinition());
    }

    @Override
    @Nullable
    protected IAEItemStack getFilter(int slot) {
        return this.host.getFilter(slot);
    }

    @Override
    protected void setFilter(int slot, @Nullable IAEItemStack stack) {
        this.host.setFilter(slot, stack);
    }

    @Override
    protected boolean isStorageEmpty(int slot) {
        return this.host.isStorageEmpty(slot);
    }

    @Override
    protected boolean keysEqual(@Nonnull ItemStackKey a, @Nonnull ItemStackKey b) {
        return a.equals(b);
    }

    @Override
    @Nullable
    protected IAEItemStack extractFilterFromContainer(ItemStack container) {
        // For items, the container IS the filter (return single-count AE stack)
        if (container.isEmpty()) return null;
        ItemStack filter = container.copy();
        filter.setCount(1);
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(filter);
    }

    @Override
    @Nonnull
    protected IAEItemStack createFilterStack(@Nonnull IAEItemStack raw) {
        IAEItemStack filter = raw.copy();
        filter.setStackSize(1);
        return filter;
    }

    @Override
    @Nullable
    protected IAEItemStack copyFilter(@Nullable IAEItemStack filter) {
        if (filter == null) return null;
        return filter.copy();
    }

    @Override
    protected boolean filtersEqual(@Nullable IAEItemStack a, @Nullable IAEItemStack b) {
        if (a == null) return b == null;
        if (b == null) return false;
        return a.equals(b);
    }

    // ================================= Storage Slot Actions =================================

    /**
     * Handle direct storage interaction with direction restrictions.
     * <p>
     * <ul>
     *   <li>Export: extract only (empty cursor → fill from storage)</li>
     *   <li>Import: insert only (item in cursor → insert to storage)</li>
     *   <li>No swap allowed in either mode</li>
     * </ul>
     * <p>
     * Oversized stacks (>64) are capped when moving to cursor to prevent
     * packet serialization issues on hybrid servers.
     *
     * @param player The player performing the action
     * @param storageSlot The absolute storage slot index
     * @param halfStack If true, extract half stack / insert single (right-click behavior)
     */
    @Override
    protected boolean handleStorageInteraction(EntityPlayerMP player, int storageSlot, boolean halfStack) {
        IItemHandlerModifiable storage = this.host.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack held = player.inventory.getItemStack();
        ItemStack stored = storage.getStackInSlot(storageSlot);
        boolean isExport = this.host.isExport();

        if (held.isEmpty()) {
            // Empty cursor: extract from storage
            if (isExport && !stored.isEmpty()) {
                // Use long-based amount for accurate extraction calculation
                long storedAmount = this.host.getStoredAmount(storageSlot);

                // Calculate extraction amount:
                // - Left-click: full stack (capped at 64)
                // - Right-click: half of what left-click would take
                int extractAmount;
                if (halfStack) {
                    // Half-stack: take half (min 1, capped at 32 to ensure cursor limit)
                    extractAmount = (int) Math.max(1, Math.min(storedAmount / 2, 32));
                } else {
                    // Full stack: take all (capped at 64)
                    extractAmount = (int) Math.min(storedAmount, 64);
                }

                ItemStack toExtract = stored.copy();
                toExtract.setCount(extractAmount);

                // Use adjustStoredAmount for proper long handling (negative to extract)
                this.host.adjustStoredAmount(storageSlot, -extractAmount);

                player.inventory.setItemStack(toExtract);
                this.updateHeld(player);
                this.host.refreshFilterMap();
                this.host.markForNetworkUpdate();
            }
            // Import mode: can't extract, do nothing
            return true;
        }

        // Item in cursor: insert to storage
        if (isExport) return true; // Export mode: can't insert

        // Validate against filter
        if (!this.host.isItemValidForSlot(storageSlot, held)) return true;

        // Calculate insertion amount:
        // - Left-click: insert all
        // - Right-click: insert single item
        int insertAmount = halfStack ? 1 : held.getCount();

        if (stored.isEmpty()) {
            // Empty slot: insert held item
            int maxInsert = (int) Math.min(insertAmount, this.host.getMaxSlotSize());
            ItemStack toInsert = held.copy();
            toInsert.setCount(maxInsert);
            storage.setStackInSlot(storageSlot, toInsert);
            held.shrink(maxInsert);
            if (held.isEmpty()) player.inventory.setItemStack(ItemStack.EMPTY);
            this.updateHeld(player);
            this.host.refreshFilterMap();
            this.host.markForNetworkUpdate();
            return true;
        }

        // Non-empty slot: only merge if same item (no swap allowed)
        if (!ItemStack.areItemsEqual(held, stored) || !ItemStack.areItemStackTagsEqual(held, stored)) {
            return true; // Different item = no action (swap disabled)
        }

        // Same item: merge (respecting halfStack = single item insertion)
        // Use host.getStoredAmount() for accurate long-based space calculation
        long currentAmount = this.host.getStoredAmount(storageSlot);
        long space = this.host.getMaxSlotSize() - currentAmount;
        int toTransfer = (int) Math.min(insertAmount, Math.min(space, Integer.MAX_VALUE));
        if (toTransfer > 0) {
            // Use adjustStoredAmount for proper long handling
            this.host.adjustStoredAmount(storageSlot, toTransfer);
            held.shrink(toTransfer);
            if (held.isEmpty()) player.inventory.setItemStack(ItemStack.EMPTY);
            this.updateHeld(player);
            this.host.refreshFilterMap();
            this.host.markForNetworkUpdate();
        }

        return true;
    }

    /**
     * Handle shift-click for storage slots (export mode only).
     * <p>
     * Moves items from storage to player inventory.
     * Import mode does nothing (extraction not allowed).
     * <p>
     * Handles oversized stacks by splitting into multiple vanilla-sized stacks.
     */
    @Override
    protected boolean handleStorageShiftClick(EntityPlayerMP player, int storageSlot) {
        // Import mode: can't extract
        if (!this.host.isExport()) return true;

        IItemHandlerModifiable storage = this.host.getStorageInventory();
        if (storageSlot < 0 || storageSlot >= storage.getSlots()) return false;

        ItemStack stored = storage.getStackInSlot(storageSlot);
        if (stored.isEmpty()) return true;

        // Get the actual long amount for proper tracking
        long remainingAmount = this.host.getStoredAmount(storageSlot);
        ItemStack template = stored.copy();
        int vanillaMax = template.getMaxStackSize();

        long totalTransferred = 0;

        for (int i = 0; i < player.inventory.mainInventory.size() && remainingAmount > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);

            if (invStack.isEmpty()) {
                // Empty slot: insert up to vanilla max
                int toInsert = (int) Math.min(remainingAmount, vanillaMax);
                ItemStack newStack = template.copy();
                newStack.setCount(toInsert);
                player.inventory.mainInventory.set(i, newStack);
                remainingAmount -= toInsert;
                totalTransferred += toInsert;
            } else if (ItemStack.areItemsEqual(invStack, template) &&
                       ItemStack.areItemStackTagsEqual(invStack, template)) {
                // Matching stack: merge up to vanilla max
                int space = vanillaMax - invStack.getCount();
                int toTransfer = (int) Math.min(remainingAmount, space);
                if (toTransfer > 0) {
                    invStack.grow(toTransfer);
                    remainingAmount -= toTransfer;
                    totalTransferred += toTransfer;
                }
            }
        }

        // Update storage using adjustStoredAmount for proper long handling
        if (totalTransferred > 0) {
            this.host.adjustStoredAmount(storageSlot, -totalTransferred);
        }

        this.host.refreshFilterMap();
        this.host.markForNetworkUpdate();
        this.detectAndSendChanges();
        return true;
    }

    // ================================= Upgrade Slot Change Handler =================================

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) this.host.refreshUpgrades();
    }

    // ================================= Stack Size Cap =================================

    /**
     * Cap the return value of slotClick to prevent oversized stacks (count > 64) from being
     * serialized in vanilla CPacketClickWindow. This is a safety measure against hybrid servers
     * that don't support AE2-UEL's extended encoding.
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @Nonnull EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);

        if (!result.isEmpty() && result.getCount() > result.getMaxStackSize()) {
            result = result.copy();
            result.setCount(result.getMaxStackSize());
        }

        return result;
    }
}
