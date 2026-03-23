package com.cells.blocks.interfacebase.fluid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.cells.blocks.interfacebase.item.FluidInterfaceLogic;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotNormal;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketFluidSlot;
import appeng.fluids.container.IFluidSyncContainer;
import appeng.helpers.InventoryAction;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;

import com.cells.Cells;
import com.cells.util.FluidStackKey;


/**
 * Unified container for both Fluid Import Interface and Fluid Export Interface GUIs.
 * Layout: 4 rows of 9 filter slots (fluid filters, not item slots).
 * Storage is fluid-based (internal tanks), rendered as GuiFluidTankSlot.
 * Plus 4 upgrade slots on the right side.
 * <p>
 * Uses {@link IFluidInterfaceHost#isExport()} to determine behavioral differences:
 * <ul>
 *   <li>Import: filter slots locked when tank has fluid, supports fluid pouring from containers</li>
 *   <li>Export: filter slots freely settable, no fluid pouring (tanks filled from network)</li>
 * </ul>
 * <p>
 * Filter synchronization is handled directly via {@link PacketFluidSlot}.
 * <p>
 * Works with both tile entities and parts via {@link IFluidInterfaceHost}.
 */
public class ContainerFluidInterface extends AEBaseContainer implements IFluidSyncContainer {

    private final IFluidInterfaceHost host;
    private final Map<Integer, IAEFluidStack> serverFilterCache = new HashMap<>();

    @GuiSync(0)
    public long maxSlotSize = FluidInterfaceLogic.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = 0;

    @GuiSync(2)
    public int currentPage = 0;

    @GuiSync(3)
    public int totalPages = 1;

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerFluidInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IFluidInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerFluidInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IFluidInterfaceHost) part, part);
    }

    /**
     * Common constructor that both tile and part use.
     */
    private ContainerFluidInterface(final InventoryPlayer ip, final IFluidInterfaceHost host, final Object anchor) {
        super(ip, anchor instanceof TileEntity ? (TileEntity) anchor : null, anchor instanceof IPart ? (IPart) anchor : null);
        this.host = host;

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < FluidInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            // Sync fluid filters by comparing with cache
            for (int i = 0; i < FluidInterfaceLogic.FILTER_SLOTS; i++) {
                IAEFluidStack current = this.host.getFilterFluid(i);
                IAEFluidStack cached = this.serverFilterCache.get(i);

                boolean different = (current == null && cached != null) ||
                                   (current != null && cached == null) ||
                                   (current != null && !current.equals(cached));

                if (different) {
                    this.serverFilterCache.put(i, current == null ? null : current.copy());

                    // Send diff to all listeners
                    for (IContainerListener listener : this.listeners) {
                        if (listener instanceof EntityPlayerMP) {
                            NetworkHandler.instance().sendTo(
                                new PacketFluidSlot(Collections.singletonMap(i, current)),
                                (EntityPlayerMP) listener
                            );
                        }
                    }
                }
            }
        }

        if (this.maxSlotSize != this.host.getMaxSlotSize()) this.maxSlotSize = this.host.getMaxSlotSize();
        if (this.pollingRate != this.host.getPollingRate()) this.pollingRate = this.host.getPollingRate();
        if (this.currentPage != this.host.getCurrentPage()) this.currentPage = this.host.getCurrentPage();
        if (this.totalPages != this.host.getTotalPages()) this.totalPages = this.host.getTotalPages();
    }

    /**
     * Sets the current page for viewing, clamped to valid range.
     */
    public void setCurrentPage(int page) {
        int newPage = Math.max(0, Math.min(page, this.totalPages - 1));
        this.currentPage = newPage;
        this.host.setCurrentPage(newPage);
    }

    public void nextPage() {
        if (this.currentPage < this.totalPages - 1) setCurrentPage(this.currentPage + 1);
    }

    public void prevPage() {
        if (this.currentPage > 0) setCurrentPage(this.currentPage - 1);
    }

    @Override
    public void addListener(@Nonnull IContainerListener listener) {
        super.addListener(listener);

        // Send full fluid filter inventory to new listener
        if (Platform.isServer() && listener instanceof EntityPlayerMP) {
            Map<Integer, IAEFluidStack> fullMap = new HashMap<>();
            for (int i = 0; i < FluidInterfaceLogic.FILTER_SLOTS; i++) {
                IAEFluidStack fluid = this.host.getFilterFluid(i);
                fullMap.put(i, fluid);
                this.serverFilterCache.put(i, fluid == null ? null : fluid.copy());
            }
            NetworkHandler.instance().sendTo(new PacketFluidSlot(fullMap), (EntityPlayerMP) listener);
        }
    }

    @Override
    public void receiveFluidSlots(Map<Integer, IAEFluidStack> fluids) {
        // On client, update the host filters directly
        if (this.host.getHostWorld() != null && this.host.getHostWorld().isRemote) {
            for (Map.Entry<Integer, IAEFluidStack> entry : fluids.entrySet()) {
                this.host.setFilterFluid(entry.getKey(), entry.getValue());
            }
            return;
        }

        // Get the player for feedback messages (first listener should be the player)
        EntityPlayer player = null;
        for (IContainerListener listener : this.listeners) {
            if (listener instanceof EntityPlayer) {
                player = (EntityPlayer) listener;
                break;
            }
        }

        final boolean isExport = this.host.isExport();

        // On server, validate each change before applying
        for (Map.Entry<Integer, IAEFluidStack> entry : fluids.entrySet()) {
            int slot = entry.getKey();
            IAEFluidStack fluid = entry.getValue();

            // Validate slot index
            if (slot < 0 || slot >= FluidInterfaceLogic.FILTER_SLOTS) continue;

            // Null fluid means clearing the filter
            if (fluid == null) {
                // Import: only clear if tank is empty (prevent orphans)
                if (!isExport && !this.host.isTankEmpty(slot)) {
                    if (player != null) {
                        player.sendMessage(new TextComponentTranslation("message.cells.storage_not_empty"));
                    }
                    continue;
                }

                this.host.setFilterFluid(slot, null);
                continue;
            }

            // Import: prevent filter changes if the corresponding tank has fluid
            if (!isExport && !this.host.isTankEmpty(slot)) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.storage_not_empty"));
                }
                continue;
            }

            // Prevent duplicate fluid filters
            FluidStackKey newKey = FluidStackKey.of(fluid.getFluidStack());
            if (newKey == null) continue;

            boolean isDuplicate = false;

            for (int i = 0; i < FluidInterfaceLogic.FILTER_SLOTS; i++) {
                if (i == slot) continue;

                IAEFluidStack otherFluid = this.host.getFilterFluid(i);
                if (otherFluid == null) continue;

                FluidStackKey otherKey = FluidStackKey.of(otherFluid.getFluidStack());
                if (otherKey != null && otherKey.equals(newKey)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                }
            } else {
                this.host.setFilterFluid(slot, fluid);
            }
        }
    }

    public IFluidInterfaceHost getHost() {
        return this.host;
    }

    public void setMaxSlotSize(int size) {
        this.host.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.host.setPollingRate(ticks);
    }

    /**
     * Clear all filters. Import only clears where tank is empty (prevents orphans).
     * Export clears all and returns fluids to network. Delegates to host.
     */
    public void clearFilters() {
        this.host.clearFilters();
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) host.refreshUpgrades();
    }

    /**
     * Handle inventory actions from the GUI, specifically for fluid container operations.
     * Import mode: supports EMPTY_ITEM action to pour fluid from the player's held item into a tank slot.
     * Export mode: no fluid pouring support (tanks are filled from the network).
     */
    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (action != InventoryAction.EMPTY_ITEM || this.host.isExport()) {
            super.doAction(player, action, slot, id);
            return;
        }

        // Validate tank slot index
        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return;

        // Get the player's held item
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return;

        // Make a copy with count 1 to get the fluid handler
        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) return;

        // Check if the slot has a filter set
        IAEFluidStack filterFluid = this.host.getFilterFluid(slot);

        // Check what fluid is in the container
        FluidStack drainable = fh.drain(Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return;

        // If filter is set, check if fluid matches
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(drainable)) return;

        // Calculate how much we can insert into the tank
        int capacity = this.host.getMaxSlotSize();
        FluidStack currentTankFluid = this.host.getFluidInTank(slot);

        // If tank has fluid, it must match
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(drainable)) return;

        int currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return;

        // Process each item in the stack
        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            // Recalculate space available
            currentTankFluid = this.host.getFluidInTank(slot);
            currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
            spaceAvailable = capacity - currentAmount;
            if (spaceAvailable <= 0) break;

            // Create fresh handler for this iteration
            ItemStack copiedContainer = held.copy();
            copiedContainer.setCount(1);
            fh = FluidUtil.getFluidHandler(copiedContainer);
            if (fh == null) break;

            // Simulate drain to see how much we can get
            drainable = fh.drain(spaceAvailable, false);
            if (drainable == null || drainable.amount <= 0) break;

            // Calculate how much we'll actually insert (minimum of drainable and space)
            int toInsert = Math.min(drainable.amount, spaceAvailable);

            // Actually drain the exact amount we can insert
            FluidStack drained = fh.drain(toInsert, true);
            if (drained == null || drained.amount <= 0) break;

            // Now insert into tank - this should always succeed since we checked space
            int actuallyInserted = this.host.insertFluidIntoTank(slot, drained);

            if (actuallyInserted < drained.amount) {
                Cells.LOGGER.warn("Could not insert all drained fluid into tank. Inserted: {}, Drained: {}", actuallyInserted, drained.amount);
            }

            // Update the player's held item
            if (held.getCount() == 1) {
                player.inventory.setItemStack(fh.getContainer());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(fh.getContainer())) {
                    player.dropItem(fh.getContainer(), false);
                }
            }
        }

        this.updateHeld(player);
    }

    // TODO: add isInFilter and addToFilter to containers

    /**
     * Handle shift-click: if the clicked item is a fluid container, extract its fluid
     * and set it as a filter in the first available slot.
     * The actual item stays in place (return empty), only the filter is set.
     * <p>
     * Note: transferStackInSlot is called on SERVER side for actual execution.
     * On client, it's just for prediction. The server handles the actual filter setting,
     * and detectAndSendChanges syncs the result back to the client.
     * We do NOT send a packet here because that would cause a duplicate message
     * (server already processed the shift-click before receiving the packet).
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        // Only process on server side - client prediction is not needed for filter slots
        if (player.world.isRemote) return ItemStack.EMPTY;

        if (slotIndex < 0 || slotIndex >= inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = inventorySlots.get(slotIndex);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        // Only process shift-clicks from the player inventory (not from upgrade slots)
        // Our upgrade slots are added first, so player inventory starts after them
        int upgradeSlotCount = FluidInterfaceLogic.UPGRADE_SLOTS;
        if (slotIndex < upgradeSlotCount) return ItemStack.EMPTY;

        ItemStack clickedStack = slot.getStack();
        if (clickedStack.isEmpty()) return ItemStack.EMPTY;

        // Try to extract fluid from the item
        FluidStack fluid = FluidUtil.getFluidContained(clickedStack);
        if (fluid == null || fluid.amount <= 0) return ItemStack.EMPTY;

        // Check for duplicates across all filter slots
        FluidStackKey newKey = FluidStackKey.of(fluid);
        if (newKey == null) return ItemStack.EMPTY;

        for (int i = 0; i < FluidInterfaceLogic.FILTER_SLOTS; i++) {
            IAEFluidStack existingFilter = this.host.getFilterFluid(i);
            if (existingFilter != null) {
                FluidStackKey existingKey = FluidStackKey.of(existingFilter.getFluidStack());
                if (existingKey != null && existingKey.equals(newKey)) {
                    // Already in filter, show duplicate message
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return ItemStack.EMPTY;
                }
            }
        }

        // Find the first empty filter slot
        final boolean isExport = this.host.isExport();
        for (int i = 0; i < FluidInterfaceLogic.FILTER_SLOTS; i++) {
            IAEFluidStack existingFilter = this.host.getFilterFluid(i);
            if (existingFilter != null) continue;

            // Import mode: only set filter if tank is empty
            if (!isExport && !this.host.isTankEmpty(i)) continue;

            // Found an available slot, set the filter
            // Server sets directly, detectAndSendChanges will sync to client
            IAEFluidStack aeFluid = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
                .createStack(fluid);
            this.host.setFilterFluid(i, aeFluid);

            return ItemStack.EMPTY;
        }

        // No empty slot found
        return ItemStack.EMPTY;
    }

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final IFluidInterfaceHost host;

        public SlotUpgrade(AppEngInternalInventory inv, int idx, int x, int y, IFluidInterfaceHost host) {
            super(inv, idx, x, y);
            this.host = host;
            this.setIIcon(13 * 16 + 15);
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return host.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public hasCalculatedValidness getIsValid() {
            return hasCalculatedValidness.Valid;
        }
    }
}
