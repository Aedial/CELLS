package com.cells.blocks.interfacebase;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.EnumFacing;

import appeng.api.storage.data.IAEStack;


/**
 * Typed filter, buffer, and adjacent-transfer access for a pattern's ingredient channel.
 */
public final class InterfaceRatioChannel<R, AE extends IAEStack<AE>, K> {

    private final AbstractResourceInterfaceLogic<R, AE, K> logic;

    public InterfaceRatioChannel(AbstractResourceInterfaceLogic<R, AE, K> logic) {
        this.logic = logic;
    }

    @SuppressWarnings("unchecked")
    public int getSlot(IAEStack<?> input) {
        K key = this.logic.createKey(this.logic.fromAEStack((AE) input));
        return key == null ? -1 : this.logic.inventoryManager.findSlotByKey(key);
    }

    @SuppressWarnings("unchecked")
    public void mergeFilters(List<IAEStack<?>> inputs) {
        if (inputs.isEmpty()) return;

        List<R> filters = new ArrayList<>(inputs.size());
        for (IAEStack<?> input : inputs) {
            filters.add(this.logic.fromAEStack((AE) input.copy().setStackSize(1)));
        }

        int previousCount = this.logic.inventoryManager.getFilterSlotList().size();
        this.logic.inventoryManager.mergeFilters(filters, null);
        if (this.logic.inventoryManager.getFilterSlotList().size() != previousCount) {
            this.logic.host.markDirtyAndSave();
            this.logic.wakeUpIfAdaptive();
        }
    }

    public long getAmount(IAEStack<?> input) {
        int slot = getSlot(input);
        if (slot < 0 || this.logic.inventoryManager.isOrphanedSlot(slot)) return 0;

        return this.logic.inventoryManager.getSlotAmount(slot);
    }

    @SuppressWarnings("unchecked")
    public long getAdjacentAmount(EnumFacing facing, IAEStack<?> input) {
        Object handler = getAdjacentHandler(facing);
        if (handler == null) return 0;

        K key = this.logic.createKey(this.logic.fromAEStack((AE) input));
        return key == null ? 0 : this.logic.countResourceInHandler(handler, key, facing);
    }

    @SuppressWarnings("unchecked")
    public int getInsertionLimit(EnumFacing facing, IAEStack<?> input, int amount) {
        Object handler = getAdjacentHandler(facing);
        if (handler == null || amount <= 0) return 0;

        R resource = this.logic.fromAEStack((AE) input);
        int accepted = this.logic.getRatioInsertionLimit(handler, resource, amount, facing);
        return Math.max(0, Math.min(amount, accepted));
    }

    @SuppressWarnings("unchecked")
    public long push(EnumFacing facing, IAEStack<?> input, int amount) {
        Object handler = getAdjacentHandler(facing);
        int slot = getSlot(input);
        if (handler == null || slot < 0 || this.logic.inventoryManager.isOrphanedSlot(slot)) return 0;

        amount = (int) Math.min(amount, this.logic.inventoryManager.getSlotAmount(slot));
        if (amount <= 0) return 0;

        R resource = this.logic.fromAEStack((AE) input);
        long accepted = this.logic.insertResourceIntoHandler(handler, resource, amount, facing);
        accepted = Math.max(0, Math.min(amount, accepted));
        if (accepted > 0) {
            this.logic.drainFromSlot(slot, (int) accepted, true);
            this.logic.host.markDirtyAndSave();
        }

        return accepted;
    }

    private Object getAdjacentHandler(EnumFacing facing) {
        if (!this.logic.adjacentHandler.isCachePopulated()) this.logic.refreshCapabilityCache();

        return this.logic.adjacentHandler.getValidCachedCapability(facing);
    }

    public void beginPush() {
        this.logic.beginRatioPush();
    }

    public void finishPush() {
        this.logic.finishRatioPush();
    }

    public void flushCaches() {
        this.logic.flushOperationCaches();
    }
}
