package com.cells.blocks.interfacebase.fluid;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.FluidTankProperties;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.Cells;
import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.network.sync.ResourceType;
import com.cells.util.FluidStackKey;


/**
 * Container for Fluid Import/Export Interface GUIs.
 * <p>
 * Extends {@link AbstractContainerInterface} with fluid-specific implementations.
 * Most logic is inherited from the abstract base class.
 */
public class ContainerFluidInterface
    extends AbstractContainerInterface<IAEFluidStack, FluidStackKey, IFluidInterfaceHost> {

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
     * Common constructor.
     */
    private ContainerFluidInterface(final InventoryPlayer ip, final IFluidInterfaceHost host, final Object anchor) {
        super(ip, host, anchor, FluidInterfaceLogic.DEFAULT_MAX_SLOT_SIZE);

        // Add upgrade slots
        for (int i = 0; i < FluidInterfaceLogic.UPGRADE_SLOTS; i++) {
            this.addSlotToContainer(new SlotUpgrade<>(
                host.getUpgradeInventory(), i, 186, 25 + i * 18, host
            ));
        }

        // Bind player inventory
        this.bindPlayerInventory(ip, 0, 174);
    }

    // ================================= Abstract Implementations =================================

    @Override
    protected ResourceType getResourceType() {
        return ResourceType.FLUID;
    }

    @Override
    protected int getUpgradeSlotCount() {
        return FluidInterfaceLogic.UPGRADE_SLOTS;
    }

    @Override
    protected int getFilterSlotCount() {
        return FluidInterfaceLogic.FILTER_SLOTS;
    }

    @Override
    protected int getSlotsPerPage() {
        return FluidInterfaceLogic.SLOTS_PER_PAGE;
    }

    @Override
    @Nullable
    protected FluidStackKey createKey(@Nullable IAEFluidStack stack) {
        if (stack == null) return null;
        return FluidStackKey.of(stack.getFluidStack());
    }

    @Override
    @Nullable
    protected IAEFluidStack getFilter(int slot) {
        return this.host.getFilter(slot);
    }

    @Override
    protected void setFilter(int slot, @Nullable IAEFluidStack stack) {
        this.host.setFilter(slot, stack);
    }

    @Override
    protected boolean isStorageEmpty(int slot) {
        return this.host.isStorageEmpty(slot);
    }

    @Override
    protected boolean keysEqual(@Nonnull FluidStackKey a, @Nonnull FluidStackKey b) {
        return a.equals(b);
    }

    @Override
    @Nullable
    protected IAEFluidStack extractFilterFromContainer(ItemStack container) {
        FluidStack fluid = FluidUtil.getFluidContained(container);
        if (fluid == null || fluid.amount <= 0) return null;
        return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class).createStack(fluid);
    }

    @Override
    @Nonnull
    protected IAEFluidStack createFilterStack(@Nonnull IAEFluidStack raw) {
        // Already an AE stack, just ensure it has count 1
        IAEFluidStack copy = raw.copy();
        copy.setStackSize(1);
        return copy;
    }

    @Override
    @Nullable
    protected IAEFluidStack copyFilter(@Nullable IAEFluidStack filter) {
        return filter == null ? null : filter.copy();
    }

    @Override
    protected boolean filtersEqual(@Nullable IAEFluidStack a, @Nullable IAEFluidStack b) {
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

    // ================================= Fluid Pouring (Import Only) =================================

    /**
     * Handle pouring fluid from held item into tank.
     * Called by the base class doAction for EMPTY_ITEM actions on import interfaces.
     */
    @Override
    protected boolean handleEmptyItemAction(EntityPlayerMP player, int slot) {
        // Validate tank slot index
        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        // Get the player's held item
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Make a copy with count 1 to get the fluid handler
        ItemStack heldCopy = held.copy();
        heldCopy.setCount(1);
        IFluidHandlerItem fh = FluidUtil.getFluidHandler(heldCopy);
        if (fh == null) return false;

        // Check if the slot has a filter set
        IAEFluidStack filterFluid = this.host.getFilter(slot);

        // Check what fluid is in the container
        FluidStack drainable = fh.drain(Integer.MAX_VALUE, false);
        if (drainable == null || drainable.amount <= 0) return false;

        // If filter is set, check if fluid matches
        if (filterFluid != null && !filterFluid.getFluidStack().isFluidEqual(drainable)) return false;

        // Calculate how much we can insert into the tank
        int capacity = this.host.getMaxSlotSize();
        FluidStack currentTankFluid = this.host.getFluidInTank(slot);

        // If tank has fluid, it must match
        if (currentTankFluid != null && !currentTankFluid.isFluidEqual(drainable)) return false;

        int currentAmount = currentTankFluid != null ? currentTankFluid.amount : 0;
        int spaceAvailable = capacity - currentAmount;
        if (spaceAvailable <= 0) return false;

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

            // Calculate how much we'll actually insert
            int toInsert = Math.min(drainable.amount, spaceAvailable);

            // Actually drain the exact amount we can insert
            FluidStack drained = fh.drain(toInsert, true);
            if (drained == null || drained.amount <= 0) break;

            // Now insert into tank
            int actuallyInserted = insertFluidIntoTank(slot, drained);

            if (actuallyInserted < drained.amount) {
                Cells.LOGGER.warn("Could not insert all drained fluid. Inserted: {}, Drained: {}",
                    actuallyInserted, drained.amount);
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
        return true;
    }

    /**
     * Insert fluid into a tank slot.
     */
    private int insertFluidIntoTank(int slot, FluidStack fluid) {
        return this.host.insertFluidIntoTank(slot, fluid);
    }

    // ================================= Fluid Extraction (Export Only) =================================

    /**
     * Handle filling held item from tank.
     * Called by the base class doAction for FILL_ITEM actions on export interfaces.
     */
    @Override
    protected boolean handleFillItemAction(EntityPlayerMP player, int slot) {
        // Validate tank slot index
        if (slot < 0 || slot >= FluidInterfaceLogic.STORAGE_SLOTS) return false;

        // Get the player's held item
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return false;

        // Check if tank has fluid
        FluidStack tankFluid = this.host.getFluidInTank(slot);
        if (tankFluid == null || tankFluid.amount <= 0) return false;

        // Process each item in the stack
        int heldAmount = held.getCount();
        for (int i = 0; i < heldAmount; i++) {
            // Check if tank still has fluid
            tankFluid = this.host.getFluidInTank(slot);
            if (tankFluid == null || tankFluid.amount <= 0) break;

            // Try to fill a single container
            ItemStack singleContainer = held.copy();
            singleContainer.setCount(1);

            // Use FluidUtil to fill the container
            FluidActionResult result = FluidUtil.tryFillContainer(
                singleContainer,
                new TankWrapper(slot, tankFluid),
                Integer.MAX_VALUE,
                player,
                true
            );

            if (!result.isSuccess()) break;

            // Update the player's held item
            if (held.getCount() == 1) {
                player.inventory.setItemStack(result.getResult());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(result.getResult())) {
                    player.dropItem(result.getResult(), false);
                }
            }
        }

        this.updateHeld(player);
        return true;
    }

    /**
     * Wrapper for tank slot to work with FluidUtil.
     */
    private class TankWrapper implements IFluidHandler {
        private final int slot;
        private FluidStack fluid;

        TankWrapper(int slot, FluidStack fluid) {
            this.slot = slot;
            this.fluid = fluid;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[] {
                new FluidTankProperties(fluid, host.getMaxSlotSize())
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            // This wrapper is for draining only
            return 0;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || !resource.isFluidEqual(fluid)) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = host.drainFluidFromTank(slot, maxDrain, doDrain);
            if (doDrain && drained != null) {
                // Update our cached fluid
                fluid = host.getFluidInTank(slot);
            }
            return drained;
        }
    }
}
