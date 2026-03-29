package com.cells.integration.mekanismenergistics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.parts.IPart;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.api.gas.IGasItem;
import mekanism.common.capabilities.Capabilities;

import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.gui.QuickAddHelper;
import com.cells.network.sync.ResourceType;


/**
 * Container for Gas Import/Export Interface GUIs.
 * <p>
 * Extends {@link AbstractContainerInterface} with gas-specific implementations.
 * Most logic is inherited from the abstract base class.
 */
public class ContainerGasInterface
    extends AbstractContainerInterface<IAEGasStack, GasStackKey, IGasInterfaceHost> {

    /**
     * Constructor for tile entity hosts.
     */
    public ContainerGasInterface(final InventoryPlayer ip, final TileEntity tile) {
        this(ip, (IGasInterfaceHost) tile, tile);
    }

    /**
     * Constructor for part hosts.
     */
    public ContainerGasInterface(final InventoryPlayer ip, final IPart part) {
        this(ip, (IGasInterfaceHost) part, part);
    }

    /**
     * Common constructor.
     */
    private ContainerGasInterface(final InventoryPlayer ip, final IGasInterfaceHost host, final Object anchor) {
        super(ip, host, anchor, GasInterfaceLogic.DEFAULT_MAX_SLOT_SIZE);
    }

    // ================================= Abstract Implementations =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.GAS;
    }

    @Override
    @Nullable
    protected GasStackKey createKey(@Nullable IAEGasStack stack) {
        if (stack == null) return null;
        return GasStackKey.of(stack.getGasStack());
    }

    @Override
    @Nullable
    protected IAEGasStack getFilter(int slot) {
        return this.host.getFilter(slot);
    }

    @Override
    protected void setFilter(int slot, @Nullable IAEGasStack stack) {
        this.host.setFilter(slot, stack);
    }

    @Override
    protected boolean isStorageEmpty(int slot) {
        return this.host.isStorageEmpty(slot);
    }

    @Override
    protected boolean keysEqual(@Nonnull GasStackKey a, @Nonnull GasStackKey b) {
        return a.equals(b);
    }

    @Override
    @Nullable
    protected IAEGasStack extractFilterFromContainer(ItemStack container) {
        GasStack gas = QuickAddHelper.getGasFromItemStack(container);
        if (gas == null || gas.amount <= 0) return null;
        return AEGasStack.of(gas);
    }

    @Override
    @Nonnull
    protected IAEGasStack createFilterStack(@Nonnull IAEGasStack raw) {
        // Already an AE stack, just ensure it has count 1
        IAEGasStack copy = raw.copy();
        copy.setStackSize(1);
        return copy;
    }

    @Override
    @Nullable
    protected IAEGasStack copyFilter(@Nullable IAEGasStack filter) {
        return filter == null ? null : filter.copy();
    }

    @Override
    protected boolean filtersEqual(@Nullable IAEGasStack a, @Nullable IAEGasStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ================================= Upgrade Slot Change Handler =================================

    @Override
    public void onSlotChange(final Slot s) {
        super.onSlotChange(s);
        if (s instanceof SlotUpgrade) this.host.refreshUpgrades();
    }

    // ================================= Gas Pouring (Import Mode) =================================

    /**
     * Handle pouring gas from a held item into a tank slot.
     * <p>
     * Supports IGasItem (Mekanism gas tanks) and GAS_HANDLER_CAPABILITY items.
     */
    @Override
    protected boolean handleEmptyItemAction(EntityPlayerMP player, int tankSlot) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Check for IGasItem (Mekanism gas tanks)
        if (held.getItem() instanceof IGasItem) {
            return handleIGasItemPouring(player, tankSlot, held);
        }

        // Check for gas handler capability
        IGasHandler handler = held.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
        if (handler != null) {
            return handleGasCapabilityPouring(player, tankSlot, held, handler);
        }

        return false;
    }

    /**
     * Handle pouring from an IGasItem (Mekanism gas tank, basic tank, etc.).
     * <p>
     * Gas tanks are reusable - they are NOT consumed when emptied.
     * Instead, their internal gas storage (NBT) is modified directly.
     */
    private boolean handleIGasItemPouring(EntityPlayerMP player, int tankSlot, ItemStack held) {
        IGasItem gasItem = (IGasItem) held.getItem();

        // Get gas from the held item
        GasStack drainable = gasItem.getGas(held);
        if (drainable == null || drainable.amount <= 0) return false;

        // Check if the slot has a filter set
        IAEGasStack filterGas = this.host.getFilter(tankSlot);
        if (filterGas != null && !filterGas.getGasStack().isGasEqual(drainable)) return false;

        // Calculate how much we can insert into the tank
        int capacity = (int) Math.min(this.host.getMaxSlotSize(), Integer.MAX_VALUE);
        GasStack currentTankGas = this.host.getGasInTank(tankSlot);

        // If tank has gas, it must match
        if (currentTankGas != null && !currentTankGas.isGasEqual(drainable)) return false;

        int currentAmount = currentTankGas != null ? currentTankGas.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        // Calculate how much we can actually transfer
        int toTransfer = Math.min(drainable.amount, spaceAvailable);

        // Actually remove gas from the held item (modifies the item's NBT directly)
        GasStack removed = gasItem.removeGas(held, toTransfer);
        if (removed == null || removed.amount <= 0) return false;

        // Insert into tank
        GasStack newGas;
        if (currentTankGas == null) {
            newGas = new GasStack(removed.getGas(), removed.amount);
        } else {
            newGas = currentTankGas.copy();
            newGas.amount += removed.amount;
        }
        this.host.setGasInTank(tankSlot, newGas);

        // Sync the held item change to the client
        this.updateHeld(player);
        this.detectAndSendChanges();
        return true;
    }

    /**
     * Handle pouring from a gas handler capability item.
     * <p>
     * This handles generic gas containers that use the capability system.
     */
    private boolean handleGasCapabilityPouring(EntityPlayerMP player, int tankSlot, ItemStack held, IGasHandler handler) {
        // Query what gas the handler can provide
        GasStack drainable = handler.drawGas(null, Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return false;

        // Check if the slot has a filter set
        IAEGasStack filterGas = this.host.getFilter(tankSlot);
        if (filterGas != null && !filterGas.getGasStack().isGasEqual(drainable)) return false;

        // Calculate how much we can insert into the tank
        int capacity = (int) Math.min(this.host.getMaxSlotSize(), Integer.MAX_VALUE);
        GasStack currentTankGas = this.host.getGasInTank(tankSlot);

        // If tank has gas, it must match
        if (currentTankGas != null && !currentTankGas.isGasEqual(drainable)) return false;

        int currentAmount = currentTankGas != null ? currentTankGas.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

        // Calculate how much we can actually transfer
        int toTransfer = Math.min(drainable.amount, spaceAvailable);

        // Actually draw gas from the handler
        GasStack drawn = handler.drawGas(null, toTransfer, true);
        if (drawn == null || drawn.amount <= 0) return false;

        // Insert into tank
        GasStack newGas;
        if (currentTankGas == null) {
            newGas = new GasStack(drawn.getGas(), drawn.amount);
        } else {
            newGas = currentTankGas.copy();
            newGas.amount += drawn.amount;
        }
        this.host.setGasInTank(tankSlot, newGas);

        // Sync changes
        this.updateHeld(player);
        this.detectAndSendChanges();
        return true;
    }

    /**
     * Extract gas from a container item (doesn't modify the item itself).
     *
     * @param container The container item
     * @param maxAmount Maximum amount to extract
     * @return The extracted gas, or null if extraction failed
     */
    @Nullable
    private GasStack extractGasFromContainer(ItemStack container, int maxAmount) {
        GasStack contained = QuickAddHelper.getGasFromItemStack(container);
        if (contained == null) return null;

        return new GasStack(contained.getGas(), Math.min(contained.amount, maxAmount));
    }

    // ================================= Gas Extraction (Export Only) =================================

    /**
     * Handle filling held item from tank.
     * Called by the base class doAction for FILL_ITEM actions on export interfaces.
     */
    @Override
    protected boolean handleFillItemAction(EntityPlayerMP player, int tankSlot) {
        // Check if tank has gas
        GasStack tankGas = this.host.getGasInTank(tankSlot);
        if (tankGas == null || tankGas.amount <= 0) return false;

        // Get the player's held item
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Process each item in the stack
        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            // Check if tank still has gas
            tankGas = this.host.getGasInTank(tankSlot);
            if (tankGas == null || tankGas.amount <= 0) break;

            // Try to fill a single container
            ItemStack singleContainer = held.copy();
            singleContainer.setCount(1);

            // Fill the container using Mekanism's gas filling
            ItemStack filledContainer = fillContainerWithGas(singleContainer, tankSlot);
            if (filledContainer.isEmpty()) break;

            // Update the player's held item
            if (held.getCount() == 1) {
                player.inventory.setItemStack(filledContainer);
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(filledContainer)) {
                    player.dropItem(filledContainer, false);
                }
            }
        }

        this.detectAndSendChanges();
        return true;
    }

    /**
     * Fill a gas container from a tank slot.
     *
     * @param container The empty/partial container
     * @param tankSlot The tank slot to drain from
     * @return The filled container, or EMPTY if filling failed
     */
    @Nonnull
    private ItemStack fillContainerWithGas(ItemStack container, int tankSlot) {
        GasStack tankGas = this.host.getGasInTank(tankSlot);
        if (tankGas == null || tankGas.amount <= 0) return ItemStack.EMPTY;

        // Try IGasItem interface first
        if (container.getItem() instanceof IGasItem) {
            IGasItem gasItem = (IGasItem) container.getItem();

            // Check if gas can be received (must be same type or empty)
            GasStack existing = gasItem.getGas(container);
            if (existing != null && existing.amount > 0 && !existing.isGasEqual(tankGas)) {
                return ItemStack.EMPTY;
            }

            // Calculate how much we can fill
            int capacity = gasItem.getMaxGas(container);
            int currentAmount = existing != null ? existing.amount : 0;
            int space = capacity - currentAmount;
            if (space <= 0) return ItemStack.EMPTY;

            int toFill = Math.min(space, tankGas.amount);

            // Drain from tank and fill container
            GasStack drained = this.host.drainGasFromTank(tankSlot, toFill, true);
            if (drained == null || drained.amount <= 0) return ItemStack.EMPTY;

            ItemStack result = container.copy();
            gasItem.setGas(result, new GasStack(drained.getGas(), currentAmount + drained.amount));
            return result;
        }

        // Try GAS_HANDLER_CAPABILITY
        IGasHandler handler = container.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
        if (handler != null) {
            // Simulate to check how much can be received
            int canReceive = handler.receiveGas(null, tankGas, false);
            if (canReceive <= 0) return ItemStack.EMPTY;

            int toFill = Math.min(canReceive, tankGas.amount);

            // Drain from tank
            GasStack drained = this.host.drainGasFromTank(tankSlot, toFill, true);
            if (drained == null || drained.amount <= 0) return ItemStack.EMPTY;

            // Fill into container
            ItemStack result = container.copy();
            IGasHandler resultHandler = result.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, null);
            if (resultHandler != null) {
                resultHandler.receiveGas(null, drained, true);
            }
            return result;
        }

        return ItemStack.EMPTY;
    }

    // ================================= Gas-specific Methods =================================

    /**
     * Get a gas filter from the client cache (for GUI rendering).
     * This maintains backward compatibility with GuiGasFilterSlot.
     */
    public IAEGasStack getClientFilterGas(int slot) {
        return getClientFilter(slot);
    }
}
