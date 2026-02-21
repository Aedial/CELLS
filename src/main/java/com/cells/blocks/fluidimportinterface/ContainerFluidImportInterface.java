package com.cells.blocks.fluidimportinterface;

import java.util.Collections;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import appeng.api.storage.data.IAEFluidStack;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotNormal;
import appeng.fluids.container.IFluidSyncContainer;
import appeng.fluids.helper.FluidSyncHelper;
import appeng.helpers.InventoryAction;
import appeng.util.Platform;

import com.cells.Cells;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.util.FluidStackKey;


/**
 * Container for the Fluid Import Interface GUI.
 * Layout: 4 rows of 9 filter slots (fluid filters, not item slots).
 * Storage is fluid-based (internal tanks), rendered as GuiFluidImportTankSlot.
 * Plus 4 upgrade slots on the right side.
 */
public class ContainerFluidImportInterface extends AEBaseContainer implements IFluidSyncContainer {

    private final TileFluidImportInterface tile;
    private FluidSyncHelper filterSync = null;

    @GuiSync(0)
    public long maxSlotSize = TileFluidImportInterface.DEFAULT_MAX_SLOT_SIZE;

    @GuiSync(1)
    public long pollingRate = TileImportInterface.DEFAULT_POLLING_RATE;

    public ContainerFluidImportInterface(final InventoryPlayer ip, final TileFluidImportInterface tile) {
        super(ip, tile, null);
        this.tile = tile;

        // Add 4 upgrade slots at the right side of the GUI
        for (int i = 0; i < TileFluidImportInterface.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade(
                tile.getUpgradeInventory(),
                i,
                186,
                25 + i * 18,
                tile
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    private FluidSyncHelper getFilterSyncHelper() {
        if (this.filterSync == null) {
            this.filterSync = new FluidSyncHelper(this.tile.getFilterInventory(), 0);
        }
        return this.filterSync;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Sync fluid filter inventory
        if (Platform.isServer()) this.getFilterSyncHelper().sendDiff(this.listeners);

        if (this.maxSlotSize != this.tile.getMaxSlotSize()) this.maxSlotSize = this.tile.getMaxSlotSize();
        if (this.pollingRate != this.tile.getPollingRate()) this.pollingRate = this.tile.getPollingRate();
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        this.getFilterSyncHelper().sendFull(Collections.singleton(listener));
    }

    @Override
    public void receiveFluidSlots(Map<Integer, IAEFluidStack> fluids) {
        // On client, just update the display cache
        if (this.tile.getWorld() != null && this.tile.getWorld().isRemote) {
            this.getFilterSyncHelper().readPacket(fluids);
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

        // On server, validate each change before applying
        for (Map.Entry<Integer, IAEFluidStack> entry : fluids.entrySet()) {
            int slot = entry.getKey();
            IAEFluidStack fluid = entry.getValue();

            // Validate slot index
            if (slot < 0 || slot >= TileFluidImportInterface.FILTER_SLOTS) continue;

            // Null fluid means clearing the filter - only allowed if tank is empty (prevent orphans)
            if (fluid == null) {
                if (this.tile.isTankEmpty(slot)) {
                    this.tile.setFilterFluid(slot, null);
                } else if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_interface.storage_not_empty"));
                }
                continue;
            }

            // Prevent filter changes if the corresponding tank has fluid
            if (!this.tile.isTankEmpty(slot)) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_interface.storage_not_empty"));
                }
                continue;
            }

            // Prevent duplicate fluid filters
            FluidStackKey newKey = FluidStackKey.of(fluid.getFluidStack());
            if (newKey == null) continue;

            boolean isDuplicate = false;
            for (int i = 0; i < TileFluidImportInterface.FILTER_SLOTS; i++) {
                if (i == slot) continue;

                IAEFluidStack otherFluid = this.tile.getFilterFluid(i);
                if (otherFluid == null) continue;

                FluidStackKey otherKey = FluidStackKey.of(otherFluid.getFluidStack());
                if (otherKey != null && otherKey.equals(newKey)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                if (player != null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_interface.filter_duplicate"));
                }
            } else {
                this.tile.setFilterFluid(slot, fluid);
            }
        }
    }

    public TileFluidImportInterface getTile() {
        return this.tile;
    }

    public void setMaxSlotSize(int size) {
        this.tile.setMaxSlotSize(size);
    }

    public void setPollingRate(int ticks) {
        this.tile.setPollingRate(ticks);
    }

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);

        if (s instanceof SlotUpgrade) tile.refreshUpgrades();
    }

    /**
     * Handle inventory actions from the GUI, specifically for fluid container operations.
     * Supports EMPTY_ITEM action to pour fluid from the player's held item into a tank slot.
     */
    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (action != InventoryAction.EMPTY_ITEM) {
            super.doAction(player, action, slot, id);
            return;
        }

        // Validate tank slot index
        if (slot < 0 || slot >= TileFluidImportInterface.TANK_SLOTS) return;

        // Get the player's held item
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return;

        // Make a copy with count 1 to get the fluid handler
        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) return;

        // Check if the slot has a filter set
        IAEFluidStack filterFluid = this.tile.getFilterFluid(slot);

        // Check what fluid is in the container
        FluidStack drainable = fh.drain(Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return;

        // If filter is set, check if fluid matches
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(drainable)) return;

        // Calculate how much we can insert into the tank
        int capacity = this.tile.getMaxSlotSize();
        FluidStack currentTankFluid = this.tile.getFluidInTank(slot);

        // If tank has fluid, it must match
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(drainable)) return;

        int currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return;

        // Process each item in the stack
        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            // Recalculate space available
            currentTankFluid = this.tile.getFluidInTank(slot);
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
            int actuallyInserted = this.tile.insertFluidIntoTank(slot, drained);

            // Safety check: if we couldn't insert everything we drained, we have a problem
            // This shouldn't happen, but log it if it does
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

    /**
     * Custom slot for upgrades that only accepts specific upgrade cards.
     */
    private static class SlotUpgrade extends SlotNormal {
        private final TileFluidImportInterface tile;

        public SlotUpgrade(appeng.tile.inventory.AppEngInternalInventory inv, int idx, int x, int y, TileFluidImportInterface tile) {
            super(inv, idx, x, y);
            this.tile = tile;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return tile.isValidUpgrade(stack);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }
    }
}
