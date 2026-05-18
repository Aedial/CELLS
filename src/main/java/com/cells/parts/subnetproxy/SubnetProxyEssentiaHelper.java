package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.item.ItemDummyAspect;

import thaumcraft.api.aspects.Aspect;

import com.cells.integration.thaumicenergistics.EssentiaStackKey;

import net.minecraft.item.ItemStack;


/**
 * Isolates all ThaumicEnergistics class references for the Subnet Proxy.
 * <p>
 * This class is only loaded when "thaumicenergistics" is present, preventing
 * {@link ClassNotFoundException} when the mod is absent. All calls
 * must be guarded by {@code ThaumicEnergisticsIntegration.isModLoaded()}.
 */
final class SubnetProxyEssentiaHelper {

    static volatile IEssentiaStorageChannel CACHED_ESSENTIA_CHANNEL = null;

    private SubnetProxyEssentiaHelper() {}

    /** Get the essentia storage channel from AE2. */
    static IStorageChannel<IAEEssentiaStack> getChannel() {
        IEssentiaStorageChannel ch = CACHED_ESSENTIA_CHANNEL;
        if (ch == null) {
            ch = AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class);
            CACHED_ESSENTIA_CHANNEL = ch;
        }

        return CACHED_ESSENTIA_CHANNEL;
    }

    /** Create a new SubnetProxyInventoryHandler for essentia. */
    static SubnetProxyInventoryHandler<IAEEssentiaStack> createHandler(int priority) {
        return new SubnetProxyInventoryHandler<>(getChannel(), priority);
    }

    /** Create a new SubnetProxyInsertionHandler for essentia (front-grid &rarr; back-grid). */
    static SubnetProxyInsertionHandler<IAEEssentiaStack> createInsertionHandler() {
        return new SubnetProxyInsertionHandler<>(getChannel());
    }

    /** True if the given AE2 storage channel is the essentia channel. */
    static boolean matchesChannel(IStorageChannel<?> ch) {
        return ch == getChannel();
    }

    /** Wire the back-grid essentia monitor + read-side filter into the insertion handler. */
    static void wireInsertionHandler(
            SubnetProxyInsertionHandler<IAEEssentiaStack> insertion,
            SubnetProxyInventoryHandler<IAEEssentiaStack> read,
            IStorageGrid backStorage,
            int priority) {
        insertion.setMonitor(backStorage.getInventory(getChannel()));
        insertion.setFilter(read.getFilter());
        insertion.setPriority(priority);
    }

    /**
     * Collect local cell handlers from Grid A for the essentia channel,
     * and update the handler's sources from the grid.
     */
    @SuppressWarnings("unchecked")
    static void updateSources(
            SubnetProxyInventoryHandler<IAEEssentiaStack> handler,
            IGrid gridA,
            IStorageGrid sg) {

        IStorageChannel<IAEEssentiaStack> essentiaChannel = getChannel();
        List<IMEInventoryHandler<IAEEssentiaStack>> localEssentiaCells = new ArrayList<>();

        for (IGridNode node : gridA.getNodes()) {
            IGridHost host = node.getMachine();
            if (!(host instanceof ICellProvider)) continue;
            if (host instanceof PartSubnetProxyFront) continue;
            if (PartSubnetProxyFront.isPassthroughBusStatic(host)) continue;

            ICellProvider provider = (ICellProvider) host;
            for (IMEInventoryHandler<?> h : provider.getCellArray(essentiaChannel)) {
                localEssentiaCells.add((IMEInventoryHandler<IAEEssentiaStack>) h);
            }
        }

        handler.setLocalCells(localEssentiaCells);
    }

    /**
     * Return a singleton cell array list for getCellArray.
     */
    @SuppressWarnings("rawtypes")
    static List<IMEInventoryHandler> asCellArray(
            SubnetProxyInventoryHandler<IAEEssentiaStack> handler,
            IStorageChannel<?> requestedChannel) {

        if (requestedChannel == getChannel()) {
            return handler.asCellArray();
        }
        return Collections.emptyList();
    }

    /**
     * Check if a config slot ItemStack is an essentia dummy item.
     */
    static boolean isEssentiaDummy(ItemStack stack) {
        return stack.getItem() instanceof ItemDummyAspect;
    }

    /**
     * Extract an EssentiaStackKey from an essentia dummy ItemStack.
     */
    static EssentiaStackKey extractEssentiaKey(ItemStack stack) {
        Aspect aspect = ((ItemDummyAspect) stack.getItem()).getAspect(stack);
        return EssentiaStackKey.of(aspect);
    }

    /**
     * Build a filter predicate for essentia stacks.
     * Returns null if no filter needed (empty set + whitelist).
     */
    static Predicate<IAEEssentiaStack> buildFilter(Set<EssentiaStackKey> filterSet, boolean hasInverter) {
        if (filterSet.isEmpty() && !hasInverter) return null;

        return aeStack -> {
            if (aeStack == null) return false;

            // Match by aspect tag
            boolean matchesAny = false;
            for (EssentiaStackKey key : filterSet) {
                if (key.matches(aeStack.getStack())) {
                    matchesAny = true;
                    break;
                }
            }

            return hasInverter != matchesAny;
        };
    }

    /**
     * Scan the config inventory for essentia dummy items, build a filter set,
     * and apply the resulting predicate to the handler.
     * <p>
     * This keeps all EssentiaStackKey references isolated in this class.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void rebuildFilter(SubnetProxyInventoryHandler handler, AppEngInternalInventory config,
                              int totalSlots, boolean hasInverter) {
        Set<EssentiaStackKey> filterSet = new HashSet<>();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = config.getStackInSlot(i);
            if (stack.isEmpty() || !isEssentiaDummy(stack)) continue;

            EssentiaStackKey key = extractEssentiaKey(stack);
            if (key != null) filterSet.add(key);
        }

        handler.setFilter(buildFilter(filterSet, hasInverter));
    }

    // ========================= Listener Registration =========================

    /** Register the raw listener on Grid A's essentia monitor. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void registerListener(SubnetProxyInventoryHandler<IAEEssentiaStack> handler,
                                 IStorageGrid sg, IMEMonitorHandlerReceiver listener, Object token) {
        IMEMonitor<IAEEssentiaStack> monitor = sg.getInventory(getChannel());
        if (monitor != null) {
            handler.setRegisteredMonitor(monitor);
            monitor.addListener((IMEMonitorHandlerReceiver) listener, token);
        }
    }
}
