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

import appeng.api.parts.IPart;

import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.gui.slots.PagedItemHandler;
import com.cells.network.sync.ResourceType;
import com.cells.util.ItemStackKey;


/**
 * Container for Item Import/Export Interface GUIs.
 * <p>
 * Extends {@link AbstractContainerInterface} with item-specific implementations.
 * Filter slots are custom GUI slots ({@link com.cells.gui.slots.ItemFilterSlot}) with unified PacketResourceSlot sync.
 * Storage slots use {@link ItemStorageSlot} for actual item manipulation.
 * <p>
 * Layout: 4 rows of slots (filter above storage), 4 upgrade slots on the right.
 */
public class ContainerItemInterface
    extends AbstractContainerInterface<ItemStack, ItemStackKey, IItemInterfaceHost> {

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

        // Create paged storage handler for GUI use
        this.pagedStorageHandler = new PagedItemHandler(
            host.getStorageInventory(),
            ItemInterfaceLogic.SLOTS_PER_PAGE,
            () -> this.currentPage,
            () -> this.totalPages
        );


        // Add upgrade slots
        for (int i = 0; i < ItemInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade<>(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
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
    protected int getUpgradeSlotCount() {
        return ItemInterfaceLogic.UPGRADE_SLOTS;
    }

    @Override
    protected int getFilterSlotCount() {
        return ItemInterfaceLogic.FILTER_SLOTS;
    }

    @Override
    protected int getSlotsPerPage() {
        return ItemInterfaceLogic.SLOTS_PER_PAGE;
    }

    @Override
    @Nullable
    protected ItemStackKey createKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ItemStackKey.of(stack);
    }

    @Override
    @Nullable
    protected ItemStack getFilter(int slot) {
        return this.host.getFilter(slot);
    }

    @Override
    protected void setFilter(int slot, @Nullable ItemStack stack) {
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
    protected ItemStack extractFilterFromContainer(ItemStack container) {
        // For items, the container IS the filter (return single-count copy)
        if (container.isEmpty()) return null;
        ItemStack filter = container.copy();
        filter.setCount(1);
        return filter;
    }

    @Override
    @Nonnull
    protected ItemStack createFilterStack(@Nonnull ItemStack raw) {
        ItemStack filter = raw.copy();
        filter.setCount(1);
        return filter;
    }

    @Override
    @Nullable
    protected ItemStack copyFilter(@Nullable ItemStack filter) {
        if (filter == null || filter.isEmpty()) return null;
        return filter.copy();
    }

    @Override
    protected boolean filtersEqual(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        if (b == null || b.isEmpty()) return false;
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
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
                int storedCount = stored.getCount();

                // Calculate extraction amount:
                // - Left-click: full stack (capped at 64)
                // - Right-click: half of what left-click would take
                int extractAmount;
                if (halfStack) {
                    // Half-stack: take half (min 1, capped at 32 to ensure cursor limit)
                    extractAmount = Math.max(1, Math.min(storedCount / 2, 32));
                } else {
                    // Full stack: take all (capped at 64)
                    extractAmount = Math.min(storedCount, 64);
                }

                ItemStack toExtract = stored.copy();
                toExtract.setCount(extractAmount);

                if (extractAmount >= storedCount) {
                    storage.setStackInSlot(storageSlot, ItemStack.EMPTY);
                } else {
                    ItemStack remainder = stored.copy();
                    remainder.shrink(extractAmount);
                    storage.setStackInSlot(storageSlot, remainder);
                }

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
            int maxInsert = Math.min(insertAmount, this.host.getMaxSlotSize());
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
        int space = this.host.getMaxSlotSize() - stored.getCount();
        int toTransfer = Math.min(insertAmount, space);
        if (toTransfer > 0) {
            stored.grow(toTransfer);
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

        // Transfer to player inventory, respecting vanilla stack limits
        ItemStack remainder = stored.copy();
        int vanillaMax = remainder.getMaxStackSize();

        for (int i = 0; i < player.inventory.mainInventory.size() && !remainder.isEmpty(); i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);

            if (invStack.isEmpty()) {
                // Empty slot: insert up to vanilla max
                int toInsert = Math.min(remainder.getCount(), vanillaMax);
                ItemStack newStack = remainder.copy();
                newStack.setCount(toInsert);
                player.inventory.mainInventory.set(i, newStack);
                remainder.shrink(toInsert);
            } else if (ItemStack.areItemsEqual(invStack, remainder) &&
                       ItemStack.areItemStackTagsEqual(invStack, remainder)) {
                // Matching stack: merge up to vanilla max
                int space = vanillaMax - invStack.getCount();
                int toTransfer = Math.min(remainder.getCount(), space);
                if (toTransfer > 0) {
                    invStack.grow(toTransfer);
                    remainder.shrink(toTransfer);
                }
            }
        }

        // Update storage with whatever couldn't be transferred
        storage.setStackInSlot(storageSlot, remainder);
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
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);

        if (!result.isEmpty() && result.getCount() > result.getMaxStackSize()) {
            result = result.copy();
            result.setCount(result.getMaxStackSize());
        }

        return result;
    }
}
