package com.cells.integration.mekanismenergistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;

import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;

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

    /**
     * Set the gas in a specific tank slot.
     * Used by GUI gas pouring in import mode.
     *
     * @param slot The tank slot index
     * @param gas The gas to set, or null to clear
     */
    public void setGasInTank(int slot, @Nullable GasStack gas) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        setResourceInSlot(slot, gas);
        this.host.markDirtyAndSave();
    }

    @Nullable
    public GasStack drainGasFromTank(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlot(slot, maxDrain, doDrain);
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected GasStackKey createKey(GasStack resource) {
        return GasStackKey.of(resource);
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
        boolean changed = this.inventoryManager.clearAllStorageForDeserialization();

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int gasId = data.readShort();
            long amount = data.readLong();

            Gas gas = GasRegistry.getGas(gasId);
            this.setResourceInSlotWithAmount(slot, new GasStack(gas, 1), amount);
            changed = true;
        }

        return changed;
    }

    @Override
    public void writeStorageToStream(ByteBuf data) {
        // Gather non-empty storage slots
        List<Integer> slots = this.inventoryManager.getNonEmptyStorageSlots();
        data.writeShort(slots.size());

        for (int slot : slots) {
            data.writeShort(slot);
            data.writeShort(getStorageIdentity(slot).getGas().getID());
            data.writeLong(getSlotAmount(slot));
        }
    }

    @Override
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = this.inventoryManager.clearAllFiltersForDeserialization();

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int gasId = data.readShort();

            Gas gas = GasRegistry.getGas(gasId);
            this.inventoryManager.setFilterDirect(slot, new GasStack(gas, 1));
            changed = true;
        }

        if (changed) this.refreshFilterMap();

        return changed;
    }

    @Override
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        List<Integer> slots = this.inventoryManager.getFilterSlotList();
        data.writeShort(slots.size());

        for (int slot : slots) {
            data.writeShort(slot);
            data.writeShort(getRawFilter(slot).getGas().getID());
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
    protected ItemStack createRecoveryItem(GasStack identity, long amount) {
        return ItemRecoveryContainer.createForGas(identity.getGas().getName(), amount);
    }

    // ============================== Auto-Pull/Push capability methods ==============================

    @Override
    protected List<Capability<?>> getAdjacentCapabilities() {
        return Collections.singletonList(Capabilities.GAS_HANDLER_CAPABILITY);
    }

    @Override
    protected long countResourceInHandler(Object handler, GasStackKey key, EnumFacing facing) {
        if (!(handler instanceof IGasHandler)) return 0;

        long total = 0;
        Gas targetGas = key.getGas();
        for (GasTankInfo tank : ((IGasHandler) handler).getTankInfo()) {
            GasStack contents = tank.getGas();
            if (contents != null && contents.getGas() == targetGas) total += contents.amount;
        }
        return total;
    }

    @Override
    @Nullable
    protected Map<GasStackKey, Long> buildResourceCountMap(Object handler, EnumFacing facing) {
        if (!(handler instanceof IGasHandler)) return null;

        Map<GasStackKey, Long> map = new HashMap<>();
        for (GasTankInfo tank : ((IGasHandler) handler).getTankInfo()) {
            GasStack contents = tank.getGas();
            if (contents == null || contents.amount <= 0) continue;

            GasStackKey key = GasStackKey.of(contents);
            if (key != null) map.merge(key, (long) contents.amount, Long::sum);
        }

        return map;
    }

    /**
     * Extract gas from an adjacent IGasHandler.
     * <p>
     * Note: {@code IGasHandler.drawGas()} is type-agnostic: it draws whatever gas is in the
     * handler, so we simulate first to verify the gas type matches before committing.
     */
    @Override
    protected long extractResourceFromHandler(Object handler, GasStackKey key, int maxAmount, EnumFacing facing) {
        if (!(handler instanceof IGasHandler)) return 0;

        IGasHandler gasHandler = (IGasHandler) handler;
        EnumFacing pullSide = facing.getOpposite();
        Gas targetGas = key.getGas();

        if (!gasHandler.canDrawGas(pullSide, targetGas)) return 0;

        // drawGas is type-agnostic; simulate first to verify we get the right gas
        GasStack simulated = gasHandler.drawGas(pullSide, maxAmount, false);
        if (simulated == null || simulated.getGas() != targetGas || simulated.amount <= 0) return 0;

        // Commit the verified amount
        GasStack drawn = gasHandler.drawGas(pullSide, simulated.amount, true);
        return drawn != null ? drawn.amount : 0;
    }

    @Override
    protected long insertResourceIntoHandler(Object handler, GasStack identity, int maxAmount, EnumFacing facing) {
        if (!(handler instanceof IGasHandler)) return 0;

        IGasHandler gasHandler = (IGasHandler) handler;
        EnumFacing pushSide = facing.getOpposite();
        Gas targetGas = identity.getGas();

        if (!gasHandler.canReceiveGas(pushSide, targetGas)) return 0;

        return gasHandler.receiveGas(pushSide, new GasStack(targetGas, maxAmount), true);
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
        @Nonnull
        public GasTankInfo[] getTankInfo() {
            List<GasTankInfo> infos = new ArrayList<>();

            final int capacity = logic.inventoryManager.getMaxSlotSizeInt();

            for (int slot : logic.getFilterSlotList()) {
                final int s = slot;

                infos.add(new GasTankInfo() {
                    @Override
                    public GasStack getGas() {
                        // Create stack with actual amount from parallel array
                        GasStack identity = logic.getStorageIdentity(s);
                        if (identity == null) return null;

                        long amount = logic.getSlotAmount(s);
                        if (amount <= 0) return null;

                        // Clamp to int for external API
                        return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
                    }

                    @Override
                    public int getStored() {
                        // Clamp to int for external API
                        return (int) Math.min(logic.getSlotAmount(s), Integer.MAX_VALUE);
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
        @Nonnull
        public GasTankInfo[] getTankInfo() {
            List<GasTankInfo> infos = new ArrayList<>();

            final int capacity = logic.inventoryManager.getMaxSlotSizeInt();

            for (int slot : logic.getFilterSlotList()) {
                final int s = slot;

                infos.add(new GasTankInfo() {
                    @Override
                    public GasStack getGas() {
                        // Create stack with actual amount from parallel array
                        GasStack identity = logic.getStorageIdentity(s);
                        if (identity == null) return null;

                        long amount = logic.getSlotAmount(s);
                        if (amount <= 0) return null;

                        // Clamp to int for external API
                        return logic.copyWithAmount(identity, (int) Math.min(amount, Integer.MAX_VALUE));
                    }

                    @Override
                    public int getStored() {
                        // Clamp to int for external API
                        return (int) Math.min(logic.getSlotAmount(s), Integer.MAX_VALUE);
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
