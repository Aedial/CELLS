package com.cells.integration.mekanismenergistics;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import com.mekeng.github.common.me.inventory.IExtendedGasHandler;
import com.mekeng.github.common.me.storage.IGasStorageChannel;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.items.ItemRecoveryContainer;


/**
 * Gas-specific implementation of the resource interface logic.
 * Handles gas import/export interfaces for both tiles and parts.
 * <p>
 * Extends {@link AbstractResourceInterfaceLogic} with GasStack as the resource type,
 * IAEGasStack as the AE2 stack type, and GasStackKey as the key type.
 */
public class GasInterfaceLogic extends AbstractResourceInterfaceLogic<GasStack, IAEGasStack, GasStackKey> {

    /**
     * Host interface for gas interfaces.
     */
    public interface Host extends AbstractResourceInterfaceLogic.Host {
    }

    /** External handler exposed via capabilities. */
    private final IGasHandler externalHandler;

    public GasInterfaceLogic(Host host) {
        super(host, GasStack.class);

        // Create appropriate external handler based on direction
        if (host.isExport()) {
            this.externalHandler = new ExportGasHandler(this);
        } else {
            this.externalHandler = new FilteredGasHandler(this);
        }
    }

    /**
     * @return The external handler to expose via capabilities.
     */
    public IGasHandler getExternalHandler() {
        return this.externalHandler;
    }

    @Nullable
    public GasStack getGasInTank(int slot) {
        return getResourceInSlot(slot);
    }

    @Nullable
    public IAEGasStack getFilterGas(int slot) {
        return getFilterResource(slot);
    }

    public void setFilterGas(int slot, @Nullable IAEGasStack gas) {
        setFilterResource(slot, gas);
    }

    public int insertGasIntoTank(int slot, GasStack gas) {
        return insertIntoSlot(slot, gas);
    }

    @Nullable
    public GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlot(slot, maxDrain, doDrain);
    }

    public int insertGasIntoNetwork(GasStack gas) {
        return insertIntoNetwork(gas);
    }

    public boolean isTankEmpty(int slot) {
        return isSlotEmpty(slot);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected GasStackKey createKey(GasStack resource) {
        return GasStackKey.of(resource);
    }

    @Override
    protected boolean resourcesMatch(GasStack a, GasStack b) {
        if (a == null || b == null) return false;
        return a.getGas() == b.getGas();
    }

    @Override
    protected int getAmount(GasStack resource) {
        return resource.amount;
    }

    @Override
    protected void setAmount(GasStack resource, int amount) {
        resource.amount = amount;
    }

    @Override
    protected GasStack copyWithAmount(GasStack resource, int amount) {
        return new GasStack(resource.getGas(), amount);
    }

    @Override
    protected GasStack copy(GasStack resource) {
        return resource.copy();
    }

    @Override
    protected String getLocalizedName(GasStack resource) {
        return resource.getGas().getLocalizedName();
    }

    @Override
    protected IAEGasStack toAEStack(GasStack resource) {
        return AEGasStack.of(resource);
    }

    @Override
    protected GasStack fromAEStack(IAEGasStack aeStack) {
        return aeStack.getGasStack();
    }

    @Override
    protected long getAEStackSize(IAEGasStack aeStack) {
        return aeStack.getStackSize();
    }

    @Override
    protected void writeResourceToNBT(GasStack resource, NBTTagCompound tag) {
        resource.write(tag);
    }

    @Override
    @Nullable
    protected GasStack readResourceFromNBT(NBTTagCompound tag) {
        return GasStack.readFromNBT(tag);
    }

    @Override
    protected String getResourceName(GasStack resource) {
        return resource.getGas().getName();
    }

    @Override
    @Nullable
    protected GasStack getResourceByName(String name, int amount) {
        Gas gas = GasRegistry.getGas(name);
        if (gas == null) return null;
        return new GasStack(gas, amount);
    }

    // Gases use ID-based stream serialization for efficiency

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
            int gasId = data.readShort();
            int amount = data.readInt();

            if (slot < 0 || slot >= STORAGE_SLOTS) continue;

            Gas gas = GasRegistry.getGas(gasId);
            if (gas != null) {
                this.storage[slot] = new GasStack(gas, amount);
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void writeStorageToStream(ByteBuf data) {
        // Count non-empty storage slots first
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null && this.storage[i].amount > 0) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            GasStack gas = this.storage[i];
            if (gas == null || gas.amount <= 0) continue;

            data.writeShort(i);
            data.writeShort(gas.getGas().getID());
            data.writeInt(gas.amount);
        }
    }

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
            int gasId = data.readShort();

            if (slot < 0 || slot >= FILTER_SLOTS) continue;

            Gas gas = GasRegistry.getGas(gasId);
            if (gas != null) {
                this.filters[slot] = new GasStack(gas, 1);
                changed = true;
            }
        }

        if (changed) this.refreshFilterMap();

        return changed;
    }

    @Override
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        int count = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < FILTER_SLOTS; i++) {
            GasStack gas = this.filters[i];
            if (gas == null) continue;

            data.writeShort(i);
            data.writeShort(gas.getGas().getID());
        }
    }

    @Override
    protected IMEInventory<IAEGasStack> getMEInventory(IStorageGrid storage) {
        return storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class)
        );
    }

    @Override
    public String getTypeName() {
        return "gas";
    }

    @Override
    protected ItemStack createRecoveryItem(GasStack resource) {
        return ItemRecoveryContainer.createForGas(resource.getGas().getName(), resource.amount);
    }

    // ============================== External handlers ==============================

    /**
     * Filtered gas insertion handler for import interfaces.
     * Uses the base class helper methods for filter/overflow/trash logic.
     */
    private static class FilteredGasHandler implements IExtendedGasHandler {
        private final GasInterfaceLogic logic;

        public FilteredGasHandler(GasInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public GasTankInfo[] getTankInfo() {
            List<GasTankInfo> infos = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                GasStack contents = logic.storage[slot];
                int capacity = logic.maxSlotSize;

                final GasStack stored = contents != null ? contents.copy() : null;

                infos.add(new GasTankInfo() {
                    @Override
                    public GasStack getGas() {
                        return stored;
                    }

                    @Override
                    public int getStored() {
                        return stored != null ? stored.amount : 0;
                    }

                    @Override
                    public int getMaxGas() {
                        return capacity;
                    }
                });
            }

            return infos.toArray(new GasTankInfo[0]);
        }

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            return logic.receiveFiltered(stack, doTransfer);
        }

        @Override
        public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
            // Import interface does not allow external extraction
            return null;
        }

        @Override
        public GasStack drawGas(EnumFacing side, GasStack request, boolean doTransfer) {
            // Import interface does not allow external extraction
            return null;
        }

        @Override
        public boolean canReceiveGas(EnumFacing side, Gas type) {
            if (type == null) return false;
            // Create minimal stack to check filter
            return logic.canReceive(new GasStack(type, 1));
        }

        @Override
        public boolean canDrawGas(EnumFacing side, Gas type) {
            return false;
        }
    }

    /**
     * Export gas handler that exposes storage for extraction only.
     * Uses the base class helper methods for drain logic.
     */
    private static class ExportGasHandler implements IExtendedGasHandler {
        private final GasInterfaceLogic logic;

        public ExportGasHandler(GasInterfaceLogic logic) {
            this.logic = logic;
        }

        @Override
        public GasTankInfo[] getTankInfo() {
            List<GasTankInfo> infos = new ArrayList<>();

            for (int slot : logic.filterSlotList) {
                GasStack contents = logic.storage[slot];
                int capacity = logic.maxSlotSize;

                final GasStack stored = contents != null ? contents.copy() : null;

                infos.add(new GasTankInfo() {
                    @Override
                    public GasStack getGas() {
                        return stored;
                    }

                    @Override
                    public int getStored() {
                        return stored != null ? stored.amount : 0;
                    }

                    @Override
                    public int getMaxGas() {
                        return capacity;
                    }
                });
            }

            return infos.toArray(new GasTankInfo[0]);
        }

        @Override
        public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
            // Export interface does not allow external insertion
            return 0;
        }

        @Override
        public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
            return logic.drainAny(amount, doTransfer);
        }

        @Override
        public GasStack drawGas(EnumFacing side, GasStack request, boolean doTransfer) {
            return logic.drainSpecific(request, doTransfer);
        }

        @Override
        public boolean canReceiveGas(EnumFacing side, Gas type) {
            return false;
        }

        @Override
        public boolean canDrawGas(EnumFacing side, Gas type) {
            if (type == null) return false;
            // Create minimal stack to check filter
            return logic.canDrain(new GasStack(type, 1));
        }
    }
}
