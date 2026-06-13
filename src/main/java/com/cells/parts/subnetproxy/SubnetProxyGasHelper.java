package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.tile.inventory.AppEngInternalInventory;

import com.mekeng.github.common.item.ItemDummyGas;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.storage.IGasStorageChannel;

import mekanism.api.gas.GasStack;

import com.cells.integration.mekanismenergistics.GasStackKey;


/**
 * Isolates all MekanismEnergistics class references for the Subnet Proxy.
 * <p>
 * This class is only loaded when "mekeng" is present, preventing
 * {@link ClassNotFoundException} when the mod is absent. All calls
 * must be guarded by {@code MekanismEnergisticsIntegration.isModLoaded()}.
 */
final class SubnetProxyGasHelper {

    static volatile IGasStorageChannel CACHED_GAS_CHANNEL = null;

    private SubnetProxyGasHelper() {}

    /** Get the gas storage channel from AE2. */
    static IStorageChannel<IAEGasStack> getChannel() {
        IGasStorageChannel ch = CACHED_GAS_CHANNEL;
        if (ch == null) {
            ch = AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class);
            CACHED_GAS_CHANNEL = ch;
        }

        return CACHED_GAS_CHANNEL;
    }

    /** Create a new SubnetProxyInventoryHandler for gas. */
    static SubnetProxyInventoryHandler<IAEGasStack> createHandler(int priority) {
        return new SubnetProxyInventoryHandler<>(getChannel(), priority);
    }

    /** Create a new SubnetProxyInsertionHandler for gas (front-grid &rarr; back-grid). */
    static SubnetProxyInsertionHandler<IAEGasStack> createInsertionHandler() {
        return new SubnetProxyInsertionHandler<>(getChannel());
    }

    /** True if the given AE2 storage channel is the gas channel. */
    static boolean matchesChannel(IStorageChannel<?> ch) {
        return ch == getChannel();
    }

    /** Wire the back-grid gas monitor + read-side filter into the insertion handler. */
    static void wireInsertionHandler(
            SubnetProxyInsertionHandler<IAEGasStack> insertion,
            SubnetProxyInventoryHandler<IAEGasStack> read,
            IStorageGrid backStorage,
            int priority) {
        insertion.setMonitor(backStorage.getInventory(getChannel()));
        insertion.setFilter(read.getFilter());
        insertion.setPriority(priority);
    }

    /**
     * Collect local cell handlers from Grid A for the gas channel,
     * and update the handler's sources from the grid.
     */
    @SuppressWarnings("unchecked")
    static void updateSources(
            SubnetProxyInventoryHandler<IAEGasStack> handler,
            IGrid gridA,
            IStorageGrid sg) {

        IStorageChannel<IAEGasStack> gasChannel = getChannel();
        List<IMEInventoryHandler<IAEGasStack>> localGasCells = new ArrayList<>();

        for (IGridNode node : gridA.getNodes()) {
            IGridHost host = node.getMachine();
            if (!PartSubnetProxyFront.isActiveCellProviderNode(node, host)) continue;
            if (!(host instanceof ICellProvider)) continue;
            if (host instanceof PartSubnetProxyFront) continue;
            if (PartSubnetProxyFront.isPassthroughBusStatic(host)) continue;

            ICellProvider provider = (ICellProvider) host;
            for (IMEInventoryHandler<?> h : provider.getCellArray(gasChannel)) {
                localGasCells.add((IMEInventoryHandler<IAEGasStack>) h);
            }
        }

        handler.setLocalCells(localGasCells);
    }

    /**
     * Return a singleton cell array list for getCellArray.
     */
    @SuppressWarnings("rawtypes")
    static List<IMEInventoryHandler> asCellArray(
            SubnetProxyInventoryHandler<IAEGasStack> handler,
            IStorageChannel<?> requestedChannel) {

        if (requestedChannel == getChannel()) return handler.asCellArray();

        return Collections.emptyList();
    }

    /**
     * Check if a config slot ItemStack is a gas dummy item.
     */
    static boolean isGasDummy(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyGas;
    }

    /**
     * Extract a GasStackKey from a gas dummy ItemStack.
     */
    static GasStackKey extractGasKey(ItemStack stack) {
        GasStack gasStack = ((ItemDummyGas) stack.getItem()).getGasStack(stack);
        return GasStackKey.of(gasStack);
    }

    /**
     * Build a filter predicate for gas stacks.
     * Returns null if no filter needed (empty set + whitelist).
     */
    static Predicate<IAEGasStack> buildFilter(Set<GasStackKey> filterSet, boolean hasInverter) {
        if (filterSet.isEmpty() && !hasInverter) return null;

        return aeStack -> {
            if (aeStack == null) return false;

            GasStack gs = aeStack.getGasStack();
            boolean matchesAny = false;
            for (GasStackKey key : filterSet) {
                if (key.matches(gs)) {
                    matchesAny = true;
                    break;
                }
            }

            return hasInverter != matchesAny;
        };
    }

    /**
     * Scan the config inventory for gas dummy items, build a filter set,
     * and apply the resulting predicate to the handler.
     * <p>
     * This keeps all GasStackKey references isolated in this class.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void rebuildFilter(SubnetProxyInventoryHandler handler, AppEngInternalInventory config,
                              int totalSlots, boolean hasInverter) {
        Set<GasStackKey> filterSet = new HashSet<>();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = config.getStackInSlot(i);
            if (stack.isEmpty() || !isGasDummy(stack)) continue;

            GasStackKey key = extractGasKey(stack);
            if (key != null) filterSet.add(key);
        }

        handler.setFilter(buildFilter(filterSet, hasInverter));
    }

    // ========================= Listener Registration =========================

    /** Register the raw listener on Grid A's gas monitor. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void registerListener(SubnetProxyInventoryHandler<IAEGasStack> handler,
                                 IStorageGrid sg, IMEMonitorHandlerReceiver listener, Object token) {
        IMEMonitor<IAEGasStack> monitor = sg.getInventory(getChannel());
        if (monitor != null) {
            handler.setRegisteredMonitor(monitor);
            monitor.addListener((IMEMonitorHandlerReceiver) listener, token);
        }
    }
}
