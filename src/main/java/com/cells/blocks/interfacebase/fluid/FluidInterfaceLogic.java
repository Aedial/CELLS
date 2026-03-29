package com.cells.blocks.interfacebase.fluid;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;

import com.cells.items.ItemRecoveryContainer;
import com.cells.util.FluidStackKey;


/**
 * Fluid-specific implementation of the resource interface logic.
 * Handles fluid import/export interfaces for both tiles and parts.
 * <p>
 * Extends {@link AbstractResourceInterfaceLogic} with FluidStack as the resource type,
 * IAEFluidStack as the AE2 stack type, and FluidStackKey as the key type.
 */
public class FluidInterfaceLogic extends AbstractResourceInterfaceLogic<FluidStack, IAEFluidStack, FluidStackKey> {

    /**
     * Host interface for fluid interfaces.
     */
    public interface Host extends AbstractResourceInterfaceLogic.Host {
    }

    /** External handler exposed via capabilities. */
    private final IFluidHandler externalHandler;

    public FluidInterfaceLogic(Host host) {
        super(host, FluidStack.class);

        // Create appropriate external handler based on direction
        if (host.isExport()) {
            this.externalHandler = new ExportFluidHandler(this);
        } else {
            this.externalHandler = new FilteredFluidHandler(this);
        }
    }

    @Override
    public String getTypeName() {
        return "fluid";
    }

    /**
     * @return The external handler to expose via capabilities.
     */
    public IFluidHandler getExternalHandler() {
        return this.externalHandler;
    }

    @Nullable
    public FluidStack getFluidInTank(int slot) {
        return getResourceInSlot(slot);
    }

    public int insertFluidIntoTank(int slot, FluidStack fluid) {
        return insertIntoSlot(slot, fluid);
    }

    @Nullable
    public FluidStack drainFluidFromTank(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlot(slot, maxDrain, doDrain);
    }

    public int insertFluidsIntoNetwork(FluidStack fluid) {
        return insertIntoNetwork(fluid);
    }

    public int findSlotForFluid(FluidStack fluid) {
        return findSlotForResource(fluid);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected FluidStackKey createKey(FluidStack resource) {
        return FluidStackKey.of(resource);
    }

    @Override
    protected int getAmount(FluidStack resource) {
        return resource.amount;
    }

    @Override
    protected void setAmount(FluidStack resource, int amount) {
        resource.amount = amount;
    }

    @Override
    protected FluidStack copyWithAmount(FluidStack resource, int amount) {
        FluidStack copy = resource.copy();
        copy.amount = amount;
        return copy;
    }

    @Override
    protected FluidStack copy(FluidStack resource) {
        return resource.copy();
    }

    @Override
    protected String getLocalizedName(FluidStack resource) {
        return resource.getLocalizedName();
    }

    @Override
    protected IAEFluidStack toAEStack(FluidStack resource) {
        return AEFluidStack.fromFluidStack(resource);
    }

    @Override
    protected FluidStack fromAEStack(IAEFluidStack aeStack) {
        return aeStack.getFluidStack();
    }

    @Override
    protected long getAEStackSize(IAEFluidStack aeStack) {
        return aeStack.getStackSize();
    }

    @Override
    protected void writeResourceToNBT(FluidStack resource, NBTTagCompound tag) {
        resource.writeToNBT(tag);
    }

    @Override
    protected String getResourceName(FluidStack resource) {
        return resource.getFluid().getName();
    }

    @Override
    @Nullable
    protected FluidStack getResourceByName(String name, int amount) {
        Fluid fluid = FluidRegistry.getFluid(name);
        if (fluid == null) return null;
        return new FluidStack(fluid, amount);
    }

    // ============================== NBT migration ==============================

    /**
     * Override to handle legacy AEFluidStack format in filters.
     */
    @Override
    @Nullable
    protected FluidStack readResourceFromNBT(NBTTagCompound tag) {
        // Try standard FluidStack format first
        FluidStack result = FluidStack.loadFluidStackFromNBT(tag);
        if (result != null && result.amount > 0) return result;

        // Legacy AEFluidStack format migration: amount is irrelevant
        if (tag.hasKey("FluidName")) {
            Fluid fluid = FluidRegistry.getFluid(tag.getString("FluidName"));
            if (fluid != null) return new FluidStack(fluid, 1000);
        }

        return null;
    }

    /**
     * Override to handle legacy TAG_LIST format for storage.
     * Old format: TAG_LIST with one entry per slot, empties marked "Empty".
     * New format: TAG_COMPOUND with slot indices as string keys.
     */
    @Override
    protected void readStorageFromNBT(NBTTagCompound data) {
        String storageKey = getStorageNBTKey();

        // Try current compound format first
        if (data.hasKey(storageKey, Constants.NBT.TAG_COMPOUND)) {
            super.readStorageFromNBT(data);
            return;
        }

        // Legacy TAG_LIST format migration
        String oldStorageKey = "fluidTanks";
        if (data.hasKey(oldStorageKey, Constants.NBT.TAG_LIST)) {
            NBTTagList storageList = data.getTagList(oldStorageKey, Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < storageList.tagCount() && i < STORAGE_SLOTS; i++) {
                NBTTagCompound slotTag = storageList.getCompoundTagAt(i);
                if (slotTag.hasKey("Empty")) {
                    this.storage[i] = null;
                } else {
                    this.storage[i] = readResourceFromNBT(slotTag);
                }
            }
        }
    }

    // ============================== ME network ==============================

    @Override
    protected IMEInventory<IAEFluidStack> getMEInventory(IStorageGrid storage) {
        return storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
        );
    }

    @Override
    protected ItemStack createRecoveryItem(FluidStack identity, long amount) {
        return ItemRecoveryContainer.createForFluid(identity, amount);
    }

    /**
     * Handle fluid filter changes. Call from host's onFluidInventoryChanged.
     */
    public void onFluidFilterChanged(int slot) {
        onFilterChanged(slot);
    }

    // ============================== External handlers ==============================

    /**
     * Filtered fluid insertion handler for import interfaces.
     * Uses the base class helper methods for filter/overflow/trash logic.
     */
    private static class FilteredFluidHandler implements IFluidHandler {
        private final FluidInterfaceLogic logic;

        public FilteredFluidHandler(FluidInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                final int s = slot;
                // Clamp to int for IFluidHandler API
                int capacity = (int) Math.min(logic.maxSlotSize, Integer.MAX_VALUE);

                FluidStackKey filterKey = logic.slotToFilterMap.get(slot);

                props.add(new IFluidTankProperties() {
                    @Nullable
                    @Override
                    public FluidStack getContents() {
                        // Create stack with actual amount from parallel array
                        FluidStack identity = logic.storage[s];
                        if (identity == null) return null;

                        long amount = logic.amounts[s];
                        if (amount <= 0) return null;

                        // Clamp to int for external API
                        return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
                    }

                    @Override
                    public int getCapacity() {
                        return capacity;
                    }

                    @Override
                    public boolean canFill() {
                        return true;
                    }

                    @Override
                    public boolean canDrain() {
                        return false;
                    }

                    @Override
                    public boolean canFillFluidType(FluidStack fluidStack) {
                        return filterKey != null && filterKey.matches(fluidStack);
                    }

                    @Override
                    public boolean canDrainFluidType(FluidStack fluidStack) {
                        return false;
                    }
                });
            }

            return props.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return logic.receiveFiltered(resource, doFill);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            // Import interface does not allow external extraction
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            // Import interface does not allow external extraction
            return null;
        }
    }

    /**
     * Export fluid handler that exposes storage for extraction only.
     * Uses the base class helper methods for drain logic.
     */
    private static class ExportFluidHandler implements IFluidHandler {
        private final FluidInterfaceLogic logic;

        public ExportFluidHandler(FluidInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            List<IFluidTankProperties> props = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                final int s = slot;
                // Clamp to int for IFluidHandler API
                int capacity = (int) Math.min(logic.maxSlotSize, Integer.MAX_VALUE);

                FluidStackKey filterKey = logic.slotToFilterMap.get(slot);

                props.add(new IFluidTankProperties() {
                    @Nullable
                    @Override
                    public FluidStack getContents() {
                        // Create stack with actual amount from parallel array
                        FluidStack identity = logic.storage[s];
                        if (identity == null) return null;

                        long amount = logic.amounts[s];
                        if (amount <= 0) return null;

                        // Clamp to int for external API
                        return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
                    }

                    @Override
                    public int getCapacity() {
                        return capacity;
                    }

                    @Override
                    public boolean canFill() {
                        return false;
                    }

                    @Override
                    public boolean canDrain() {
                        return true;
                    }

                    @Override
                    public boolean canFillFluidType(FluidStack fluidStack) {
                        return false;
                    }

                    @Override
                    public boolean canDrainFluidType(FluidStack fluidStack) {
                        return filterKey != null && filterKey.matches(fluidStack);
                    }
                });
            }

            return props.toArray(new IFluidTankProperties[0]);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            // Export interface does not allow external insertion
            return 0;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return logic.drainSpecific(resource, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return logic.drainAny(maxDrain, doDrain);
        }
    }
}
