package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fluids.Fluid;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.util.AEPartLocation;
import appeng.capabilities.Capabilities;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;

import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.sync.ResourceType;


/**
 * Channel-specific cache for stack types, filtering rules, and supported passthrough detection.
 */
public abstract class SubnetProxyChannel<T extends IAEStack<T>> {

    /** The type of resource this channel handles */
    private final ResourceType type;

    /** The AE2 storage channel associated with this subnet proxy channel */
    private final IStorageChannel<T> channel;

    protected SubnetProxyChannel(ResourceType type, IStorageChannel<T> channel) {
        this.type = type;
        this.channel = channel;
    }

    public final ResourceType getType() {
        return this.type;
    }

    public final IStorageChannel<T> getChannel() {
        return this.channel;
    }

    /**
     * Captures a filter snapshot for the given Subnet Proxy front.
     */
    public abstract Filter<T> captureFilter(PartSubnetProxyFront front);

    /**
     * Capture the returned inventory and any channel-specific backing container.
     */
    public SubnetProxyProvider.Handler<T> captureHandler(SubnetProxyProvider provider,
            IGridHost host, IMEInventoryHandler<T> inventory) {
        return new SubnetProxyProvider.Handler<>(provider, inventory);
    }

    /**
     * Combine parallel precise filters and preserve the remaining fuzzy matching rules.
     * fuzzy => ignore NBT and durability
     * inverted => allow everything BUT entries in the filter (precise)
     * normal => standard precise matching
     */
    public final Predicate<T> mergeFilters(List<SubnetProxySnapshot<T>> snapshots) {
        if (snapshots.size() == 1) return snapshots.get(0).filter.predicate;

        IItemList<T> allowed = this.channel.createList();
        List<Filter<T>> inverted = new ArrayList<>();
        List<Predicate<T>> fuzzy = new ArrayList<>();
        for (SubnetProxySnapshot<T> snapshot : snapshots) {
            Filter<T> filter = snapshot.filter;
            if (filter.entries == null) {
                fuzzy.add(filter.predicate);

                continue;
            }

            if (filter.entries.isEmpty()) return stack -> true;
            if (filter.inverted) inverted.add(filter);
            else for (T entry : filter.entries) allowed.addStorage(entry);
        }

        Predicate<T> result = stack -> allowed.findPrecise(stack) != null;
        if (!inverted.isEmpty()) {
            IItemList<T> excluded = this.channel.createList();
            // Reject entries present in every blacklist and absent from the combined whitelist
            for (T entry : inverted.get(0).entries) {
                if (allowed.findPrecise(entry) != null) continue;

                boolean rejected = true;
                for (int index = 1; index < inverted.size(); index++) {
                    if (inverted.get(index).entries.findPrecise(entry) != null) continue;

                    rejected = false;

                    break;
                }

                if (rejected) excluded.addStorage(entry);
            }

            result = stack -> excluded.findPrecise(stack) == null;
        }

        for (Predicate<T> predicate : fuzzy) result = result.or(predicate);

        return result;
    }

    /**
     * Represents a captured filter for a specific channel type.
     * It contains the matching rule and whether it is prioritized.
     */
    public static final class Filter<T extends IAEStack<T>> implements Predicate<T> {

        /** Whether a nonempty whitelist prioritizes accepted insertion */
        public final boolean prioritized;
        /** Captured matching rule used by visibility and live access */
        public final Predicate<T> predicate;
        /** Filter entries in the Subnet Proxy for this channel (or null if using a predicate) */
        private final IItemList<T> entries;
        /** Whether this filter is inverted (blacklist) */
        private final boolean inverted;

        public Filter(boolean prioritized, Predicate<T> predicate) {
            this.prioritized = prioritized;
            this.predicate = predicate;
            this.entries = null;
            this.inverted = false;
        }

        public Filter(IItemList<T> entries, boolean inverted) {
            boolean empty = entries.isEmpty();
            this.prioritized = !empty && !inverted;
            this.predicate = empty ? stack -> true
                : stack -> inverted != (entries.findPrecise(stack) != null);
            this.entries = entries;
            this.inverted = inverted;
        }

        @Override
        public boolean test(T stack) {
            return this.predicate.test(stack);
        }
    }

    /**
     * Determines whether the given device is considered a passthrough for this channel.
     * A passthrough behaves like the Subnet Proxy itself, but without the required constraints
     * for exposed content, which shuts off some optimization opportunities.
     */
    public boolean isPassthrough(IGridHost host) {
        TileEntity target = target(host);
        if (target == null) return false;

        IGridNode node = host.getGridNode(AEPartLocation.INTERNAL);
        AEPartLocation side = SubnetProxyProvider.side(SubnetProxyProvider.tile(node), host);

        return target.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR,
            side.getFacing().getOpposite());
    }

    /**
     * Gets the TileEntity the given host is targeting, or null if none is found.
     */
    protected static TileEntity target(IGridHost host) {
        if (!(host instanceof IPart)) return null;

        IGridNode node = host.getGridNode(AEPartLocation.INTERNAL);
        if (node == null) return null;

        TileEntity tile = SubnetProxyProvider.tile(node);
        AEPartLocation side = SubnetProxyProvider.side(tile, host);
        if (tile == null || side == null || side == AEPartLocation.INTERNAL) return null;

        BlockPos pos = tile.getPos().offset(side.getFacing());
        return tile.getWorld().isBlockLoaded(pos) ? tile.getWorld().getTileEntity(pos) : null;
    }

    /**
     * Gets a list of all available Subnet Proxy channels.
     */
    public static List<SubnetProxyChannel<?>> available() {
        List<SubnetProxyChannel<?>> result = new ArrayList<>();
        result.add(new Items());
        result.add(new Fluids());
        if (MekanismEnergisticsIntegration.isModLoaded()) result.add(SubnetProxyGasHelper.channel());
        if (ThaumicEnergisticsIntegration.isModLoaded()) result.add(SubnetProxyEssentiaHelper.channel());

        return result;
    }

    /**
     * Subnet Proxy channel for items.
     */
    private static final class Items extends SubnetProxyChannel<IAEItemStack> {

        private Items() {
            super(ResourceType.ITEM, AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        }

        @Override
        public Filter<IAEItemStack> captureFilter(PartSubnetProxyFront front) {
            IItemList<IAEItemStack> filters = this.getChannel().createList();

            for (int slot = 0; slot < front.getFilterSlots(); slot++) {
                ItemStack stack = front.getFilter(slot);
                if (!stack.isEmpty() && !(stack.getItem() instanceof FluidDummyItem)
                    && !(MekanismEnergisticsIntegration.isModLoaded() && SubnetProxyGasHelper.isDummy(stack))
                    && !(ThaumicEnergisticsIntegration.isModLoaded() && SubnetProxyEssentiaHelper.isDummy(stack))) {
                    filters.add(AEItemStack.fromItemStack(stack));
                }
            }

            boolean invert = front.getInstalledUpgrades(Upgrades.INVERTER) > 0;
            boolean fuzzy = front.getInstalledUpgrades(Upgrades.FUZZY) > 0;
            FuzzyMode mode = front.getFuzzyMode();

            if (filters.isEmpty() || !fuzzy) return new Filter<>(filters, invert);

            return new Filter<>(!invert, stack -> invert != !filters.findFuzzy(stack, mode).isEmpty());
        }
    }

    /**
     * Subnet Proxy channel for fluids.
     */
    private static final class Fluids extends SubnetProxyChannel<IAEFluidStack> {

        private Fluids() {
            super(ResourceType.FLUID, AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        }

        @Override
        public Filter<IAEFluidStack> captureFilter(PartSubnetProxyFront front) {
            IItemList<IAEFluidStack> filters = this.getChannel().createList();

            for (int slot = 0; slot < front.getFilterSlots(); slot++) {
                ItemStack stack = front.getFilter(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof FluidDummyItem)) continue;

                IAEFluidStack fluid = AEFluidStack.fromFluidStack(
                    ((FluidDummyItem) stack.getItem()).getFluidStack(stack));

                if (fluid != null) filters.add(fluid);
            }

            boolean invert = front.getInstalledUpgrades(Upgrades.INVERTER) > 0;
            boolean fuzzy = front.getInstalledUpgrades(Upgrades.FUZZY) > 0;

            if (filters.isEmpty() || !fuzzy) return new Filter<>(filters, invert);

            Set<Fluid> fluids = new HashSet<>(filters.size());
            for (IAEFluidStack filter : filters) fluids.add(filter.getFluid());

            return new Filter<>(!invert, stack -> invert != fluids.contains(stack.getFluid()));
        }
    }
}
