package com.cells.parts.subnetproxy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPartHost;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;


/**
 * Weak location of a provider (drive, storage bus, etc.) within the subnet proxy node.
 */
public final class SubnetProxyProvider {

    /** Weak reference to the provider's network node, for interaction with the grid */
    private final WeakReference<IGridNode> node;

    /** Weak reference to the provider's tile entity, to detect unloading or replacement */
    private final WeakReference<TileEntity> tile;

    /** Captured multipart side, or null for a non-part tile */
    private final AEPartLocation side;

    /** Whether the machine provides a passthrough for the given channel **/
    private final Map<IStorageChannel<?>, Boolean> passthrough = new IdentityHashMap<>();

    /** Channels (item, fluid, etc.) that the machine provides */
    private final Set<IStorageChannel<?>> channels = Collections.newSetFromMap(new IdentityHashMap<>());

    public SubnetProxyProvider(IGridNode node) {
        this.node = new WeakReference<>(node);
        IGridHost host = node.getMachine();
        TileEntity tile = tile(node);
        this.tile = new WeakReference<>(tile);
        this.side = side(tile, host);
    }

    /**
     * Get the tile entity associated with the node, if it is still loaded.
     * Returns null if the tile has been unloaded or replaced.
     */
    public static TileEntity tile(IGridNode node) {
        DimensionalCoord location = node.getGridBlock().getLocation();
        if (location == null || node.getWorld() == null) return null;

        BlockPos pos = location.getPos();
        return node.getWorld().isBlockLoaded(pos) ? node.getWorld().getTileEntity(pos) : null;
    }

    /**
     * Determine the side of the multipart of the given tile, if applicable.
     * Returns null for non-part tiles or if the side cannot be determined.
     */
    public static AEPartLocation side(TileEntity tile, IGridHost host) {
        if (!(tile instanceof IPartHost)) return null;

        for (AEPartLocation side : AEPartLocation.values()) {
            if (((IPartHost) tile).getPart(side) == host) return side;
        }

        return null;
    }

    /**
     * Attempt to resolve the current host for this provider,
     * ensuring it is still valid and part of the owner's grid.
     * Returns null if the provider is no longer valid or has been unloaded.
     */
    public IGridHost resolve(SubnetProxyNode owner) {
        IGridNode currentNode = this.node.get();
        TileEntity currentTile = this.tile.get();
        if (currentNode == null || currentTile == null || currentTile.isInvalid()) return null;

        if (currentTile.getWorld() == null) return null;
        if (!owner.acceptsWorld(currentTile.getWorld())) return null;
        if (!currentTile.getWorld().isBlockLoaded(currentTile.getPos())) return null;
        if (currentTile.getWorld().getTileEntity(currentTile.getPos()) != currentTile) return null;

        IGridHost host = currentNode.getMachine();
        if (host.getGridNode(AEPartLocation.INTERNAL) != currentNode) return null;

        if (this.side != null && (!(currentTile instanceof IPartHost)
            || ((IPartHost) currentTile).getPart(this.side) != host)) return null;

        if (currentNode.getGrid() != owner.getGrid()) return null;
        if (host instanceof IActionHost && !currentNode.isActive()) return null;

        return host;
    }

    /**
     * Capture the current state of the channels provided by this source.
     * It records which channels are passthrough and which have active storage.
     * Both cells and storage buses are considered when capturing the channels.
     */
    public void captureChannels(List<SubnetProxyChannel<?>> channels) {
        IGridNode current = this.node.get();
        if (current == null) return;

        IGridHost host = current.getMachine();
        for (SubnetProxyChannel<?> channel : channels) {
            this.passthrough.put(channel.getChannel(), channel.isPassthrough(host));
            if (!this.passthrough.get(channel.getChannel())
                && !((ICellProvider) host).getCellArray(channel.getChannel()).isEmpty()) {
                this.channels.add(channel.getChannel());
            }
        }
    }

    public boolean capturedPassthrough(SubnetProxyChannel<?> channel) {
        return this.passthrough.get(channel.getChannel());
    }

    /**
     * List the inventory handlers for the given channel provided by this machine.
     * The handler is the underlying inventory interface for a cell or storage bus.
     */
    public <T extends IAEStack<T>> List<Handler<T>> handlers(
            SubnetProxyNode owner, SubnetProxyChannel<T> channel) {

        IGridHost host = this.resolve(owner, channel);
        if (host == null) return Collections.emptyList();

        List<IMEInventoryHandler<T>> inventories = inventories(host, channel);
        List<Handler<T>> handlers = new ArrayList<>(inventories.size());
        for (IMEInventoryHandler<T> inventory : inventories) {
            Handler<T> handler = channel.captureHandler(this, host, inventory);
            if (handler != null) handlers.add(handler);
        }

        return handlers;
    }

    /**
     * Resolve the selected inventory while validating the provider and its backing container.
     */
    public <T extends IAEStack<T>> Handler<T> resolveHandler(
            SubnetProxyNode owner, SubnetProxyChannel<T> channel, Handler<T> selected) {

        IGridHost host = this.resolve(owner, channel);
        if (host == null) return null;

        for (IMEInventoryHandler<T> inventory : inventories(host, channel)) {
            if (selected.container == null && selected.inventory != inventory) continue;

            Handler<T> current = channel.captureHandler(this, host, inventory);
            if (selected.equals(current)) return current;
        }

        return null;
    }

    private IGridHost resolve(SubnetProxyNode owner, SubnetProxyChannel<?> channel) {
        IGridHost host = this.resolve(owner);
        return host instanceof ICellProvider && this.channels.contains(channel.getChannel())
            && !channel.isPassthrough(host) ? host : null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> List<IMEInventoryHandler<T>> inventories(
            IGridHost host, SubnetProxyChannel<T> channel) {

        return (List<IMEInventoryHandler<T>>) (List<?>) ((ICellProvider) host).getCellArray(channel.getChannel());
    }

    /**
     * Resolved inventory access for the current call or simulation request. Ordinary handlers compare
     * their inventory references; bus handlers with recreated wrappers compare their host and target tile.
     */
    public static final class Handler<T extends IAEStack<T>> {

        public final SubnetProxyProvider provider;
        public final IMEInventoryHandler<T> inventory;

        /** Bus connected to the captured container, or null for ordinary inventories */
        private final IGridHost host;
        /** Captured target container, retained with the handler until its call or request ends */
        private final TileEntity container;

        public Handler(SubnetProxyProvider provider, IMEInventoryHandler<T> inventory) {
            this(provider, inventory, null, null);
        }

        public Handler(SubnetProxyProvider provider, IMEInventoryHandler<T> inventory,
                IGridHost host, TileEntity container) {
            this.provider = provider;
            this.inventory = inventory;
            this.host = host;
            this.container = container;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Handler<?>)) return false;

            Handler<?> handler = (Handler<?>) other;
            if (this.container == null || handler.container == null) {
                return this.container == handler.container && this.inventory == handler.inventory;
            }

            return this.host == handler.host && this.container == handler.container;
        }

        @Override
        public int hashCode() {
            if (this.container == null) return System.identityHashCode(this.inventory);

            return 31 * System.identityHashCode(this.host) + System.identityHashCode(this.container);
        }
    }
}
