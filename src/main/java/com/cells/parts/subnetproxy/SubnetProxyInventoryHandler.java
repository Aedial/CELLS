package com.cells.parts.subnetproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

import com.cells.config.CellsConfig;


/**
 * Filtered, read-only passthrough handler for the Subnet Proxy.
 * <p>
 * Exposes a filtered, extract-only view of Grid A's storage to Grid B.
 * Items not matching the filter are invisible and cannot be extracted.
 * Injection is always rejected (read-only).
 * <p>
 * <b>Anti-loop design:</b> To prevent subnet loops, listing
 * ({@link #getAvailableItems}) reads only from <em>local cell handlers</em>
 * collected by the front part. These are cell arrays from Grid A's providers
 * that do NOT have a {@code STORAGE_MONITORABLE_ACCESSOR}-based passthrough
 * target (i.e. drives, ME chests, and storage buses pointing at vanilla
 * inventories), but NOT storage buses pointing at ME Interfaces
 * (which would create passthrough to another grid).
 * <p>
 * Extraction ({@link #extractItems}) uses Grid A's full {@link IMEMonitor}
 * so the grid receives proper notifications when items are removed.
 *
 * @param <T> The AE stack type (IAEItemStack, IAEFluidStack, etc.)
 */
public class SubnetProxyInventoryHandler<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

    private final IStorageChannel<T> channel;
    private int priority;

    /**
     * Cell handlers from Grid A's non-passthrough providers (drives, chests,
     * storage buses on local inventories). Used for listing only, to prevent
     * subnet loop inflation. Rebuilt on grid events by the front part.
     */
    private List<IMEInventoryHandler<T>> localCells = Collections.emptyList();

    /**
     * Filter predicate: returns true if the given stack should pass through.
     * Null means "allow all" (no filter configured).
     */
    private Predicate<T> filter;

    /**
     * Previous snapshot of this handler's listing, used for delta computation.
     * Managed by the front part's delta-forwarding logic. Null before first snapshot.
     */
    private IItemList<T> lastSnapshot;

    /**
     * Grid A's monitor this handler's listener is registered on, for unregistration.
     * Only used by gas/essentia helpers; item/fluid are tracked on the front part.
     */
    private IMEMonitor<T> registeredMonitor;

    /**
     * Owning front part. When non-null, {@link #getAvailableItems} delegates
     * peer-aggregation to it (so peer fronts on the back-grid can contribute
     * their own back-grid local items into our exposed listing, subject to
     * per-grid election in the front-grid coordinator).
     * <p>
     * Set via {@link #setFrontPart} after construction (helpers used by gas/
     * essentia integrations may leave this null, in which case peer aggregation
     * is silently skipped).
     */
    private PartSubnetProxyFront frontPart;

    public SubnetProxyInventoryHandler(IStorageChannel<T> channel, int priority) {
        this.channel = channel;
        this.priority = priority;
    }

    /**
     * Update the priority reported to AE2. Called by the front part when the
     * user changes priority through the priority GUI. AE2 re-sorts storage
     * handlers on the next {@code MENetworkCellArrayUpdate}, so callers must
     * post that event after changing priority.
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Wire the owning front part. Required for peer aggregation in
     * {@link #getAvailableItems}. Pass null to disable.
     */
    public void setFrontPart(PartSubnetProxyFront front) {
        this.frontPart = front;
    }

    /**
     * Set the local cell handlers from Grid A's non-passthrough providers.
     * These are used for listing to prevent loop inflation.
     */
    public void setLocalCells(List<IMEInventoryHandler<T>> cells) {
        this.localCells = cells != null ? cells : Collections.emptyList();
    }

    /**
     * Stable identity hash of the current local-cell set, ignoring iteration order.
     * Used by the front part to detect structural source changes without scanning
     * full contents.
     */
    int getLocalCellIdentityHash() {
        if (this.localCells.isEmpty()) return 0;

        List<Integer> ids = new ArrayList<>(this.localCells.size());
        for (IMEInventoryHandler<T> cell : this.localCells) {
            ids.add(System.identityHashCode(cell));
        }

        Collections.sort(ids);

        int hash = 1;
        for (int id : ids) hash = 31 * hash + id;

        return hash;
    }

    int getLocalCellCount() {
        return this.localCells.size();
    }

    /** Clear all sources (Grid A unavailable). */
    public void clearSources() {
        this.localCells = Collections.emptyList();
    }

    public void setFilter(java.util.function.Predicate<T> filter) {
        this.filter = filter;
    }

    /** @return the current filter predicate, or null if no filter is set (allow all) */
    public Predicate<T> getFilter() {
        return this.filter;
    }

    private void ensureFiltersCurrent() {
        if (this.frontPart != null) this.frontPart.ensureFiltersCurrent();
    }

    private void ensureSourcesCurrent() {
        if (this.frontPart != null) this.frontPart.ensureSourcesCurrent();
    }

    private boolean isReadChannelExposed() {
        return this.frontPart == null || this.frontPart.isReadChannelExposed(this.channel);
    }

    private Predicate<T> getVisibleFilter() {
        this.ensureFiltersCurrent();
        if (this.frontPart == null) return this.filter;

        return this.frontPart.buildVisibleReadFilter(this.channel, this.filter);
    }

    boolean matchesVisibleStack(T stack) {
        if (stack == null) return false;
        if (!this.isReadChannelExposed()) return false;

        Predicate<T> visibleFilter = this.getVisibleFilter();
        return visibleFilter == null || visibleFilter.test(stack);
    }

    // ========================= Snapshot / Monitor for Delta Forwarding =========================

    public IItemList<T> getLastSnapshot() {
        return this.lastSnapshot;
    }

    public void setLastSnapshot(IItemList<T> snapshot) {
        this.lastSnapshot = snapshot;
    }

    public IMEMonitor<T> getRegisteredMonitor() {
        return this.registeredMonitor;
    }

    public void setRegisteredMonitor(IMEMonitor<T> monitor) {
        this.registeredMonitor = monitor;
    }

    /**
     * Quick scan to check if any item in the given iterable passes this handler's filter.
     * Used by the Grid A listener to avoid unnecessary wake-ups for non-matching changes.
     *
     * @return true if at least one item in {@code changes} matches the filter (or no filter is set)
     */
    public boolean matchesAny(Iterable<T> changes) {
        if (!this.isReadChannelExposed()) return false;

        for (T change : changes) {
            if (this.matchesVisibleStack(change)) return true;
        }

        return false;
    }

    /**
     * Own local cells are only publishable when this front is the elected
     * publisher for its back-grid on its front-grid. Without this gate,
     * duplicate same-origin fronts can both expose the same inventory surface.
     */
    private boolean shouldExposeLocalCells() {
        return this.frontPart == null || this.frontPart.shouldExposeOwnOrigin();
    }

    // ========================= IMEInventory =========================

    @Override
    public T injectItems(final T input, final Actionable type, final IActionSource src) {
        // Read-only: reject all injections by returning the full input
        return input;
    }

    @Override
    public T extractItems(final T request, final Actionable type, final IActionSource src) {
        // Symmetric with listing: extract only from the same source set we
        // publish. If an item is not in our listing (because it would be a
        // 2+-hop passthrough item), we must not extract it either, otherwise
        // I/O becomes inconsistent with the reported inventory.
        if (request == null) return null;
        if (!this.matchesVisibleStack(request)) return null;

        this.ensureSourcesCurrent();

        long remaining = request.getStackSize();
        if (remaining <= 0) return null;

        T extracted = null;

        // FIXME: need to aggregate the cells + peer fronts into a single view and extract from that, to properly
        // account for priority across the combined inventory. Otherwise we might extract from a lower-priority cell
        // 1) Own back-grid local cells (same set used by appendLocalAvailableItems).
        // Sort by cell priority desc so AE2's per-cell priority is honored
        // even within this aggregating handler. AE2 itself extracts highest
        // priority first; we mirror that here for cells living under us.
        if (shouldExposeLocalCells()) {
            for (IMEInventoryHandler<T> cell : sortedByPriorityDesc(this.localCells)) {
                if (remaining <= 0) break;

                T sub = request.copy();
                sub.setStackSize(remaining);
                T got = cell.extractItems(sub, type, src);
                if (got == null || got.getStackSize() <= 0) continue;

                remaining -= got.getStackSize();
                if (extracted == null) {
                    extracted = got;
                } else {
                    extracted.incStackSize(got.getStackSize());
                }
            }
        }

        // 2) Peer aggregation (1-hop): extract from peer fronts' local cells
        // for origins where we are the elected publisher. Symmetric with
        // appendPeerItemsForListing.
        if (remaining > 0 && this.frontPart != null) {
            T peerRequest = request.copy();
            peerRequest.setStackSize(remaining);
            T fromPeers = this.frontPart.extractFromPeerLocals(
                peerRequest, type, src, this.channel, this::matchesVisibleStack);

            if (fromPeers != null && fromPeers.getStackSize() > 0) {
                remaining -= fromPeers.getStackSize();
                if (extracted == null) {
                    extracted = fromPeers;
                } else {
                    extracted.incStackSize(fromPeers.getStackSize());
                }
            }
        }

        // Cannot report any faults in the following cases, so skip the probe
        if (type != Actionable.MODULATE || remaining <= 0 || this.frontPart == null) return extracted;

        // Skip the visibility probe if disabled, so we avoid the scan overhead and warning logs
        if (!CellsConfig.subnetProxyReportExtractionFaults) return extracted;

        // Some callers may request more than visible amount (without doing SIMULATE first),
        // so we ignore mismatches in the other direction. They are normal and expected.
        // It *can* still be a fault, but we have no way of differentiating that from a normal request.
        long visibleAmount = this.getVisibleAmount(request);
        if (visibleAmount > 0) {
            this.frontPart.recordExtractionMismatch(this.channel, request, extracted, visibleAmount, type);
        }

        return extracted;
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out) {
        if (!this.isReadChannelExposed()) return out;

        this.ensureSourcesCurrent();

        // 1) Local back-grid items, but only when this front is the elected
        // publisher for its own origin on the current front-grid.
        appendLocalAvailableItems(out);

        // 2) Peer aggregation (1-hop): for each peer front on our back-grid
        // for which we are the elected publisher in our front-grid coordinator,
        // append the peer's local back-grid items (filtered by the peer first,
        // then by us). This gives chained visibility (B → A → C makes B's
        // items visible from C) while electing dedups diamond topologies.
        if (this.frontPart != null) {
            this.frontPart.appendPeerItemsForListing(out, this.channel, this::matchesVisibleStack);
        }

        return out;
    }

    /**
     * Append items from THIS handler's local cells (back-grid only, no peers)
     * into {@code out}, applying the filter. Public so the front part can use
     * it for cross-front peer aggregation without recursing through the full
     * peer-aware {@link #getAvailableItems} (which would re-fetch peers and
     * potentially loop).
     */
    public IItemList<T> appendLocalAvailableItems(final IItemList<T> out) {
        if (!shouldExposeLocalCells()) return out;
        if (!this.isReadChannelExposed()) return out;

        this.ensureSourcesCurrent();

        Predicate<T> visibleFilter = this.getVisibleFilter();

        // List from local cells only (not the full monitor) to prevent subnet
        // loop inflation. Items that Grid A sees through passthrough storage buses
        // (storage bus → ME Interface) are NOT listed here, which breaks feedback
        // loops like A→B→proxy→A.
        if (visibleFilter == null) {
            // No filter: write directly into output list, zero extra allocations
            for (IMEInventoryHandler<T> cell : this.localCells) {
                cell.getAvailableItems(out);
            }
        } else {
            // Filtered: allocate a single reusable scratch list and clear it
            // between cells via resetStatus(). Cells write additively, so we
            // can't filter in-place against {@code out}; the scratch list is
            // the minimum viable indirection. resetStatus() zeros stack sizes
            // so AE2 will not re-iterate them.
            IItemList<T> scratch = null;
            for (IMEInventoryHandler<T> cell : this.localCells) {
                if (scratch == null) {
                    scratch = this.channel.createList();
                } else {
                    scratch.resetStatus();
                }
                cell.getAvailableItems(scratch);

                for (T item : scratch) {
                    if (item.getStackSize() <= 0) continue;
                    if (visibleFilter.test(item)) out.add(item);
                }
            }
        }

        return out;
    }

    /**
     * Extract from THIS handler's local cells only (back-grid local, no peers,
     * no full monitor). Applies this handler's own filter in addition to any
     * caller-supplied check on {@code request}. Used by the owning front part
     * when aggregating extraction across peers: a peer's contribution must
     * pass BOTH the peer's filter (enforced here) and the caller's filter
     * (enforced by the caller before invoking this method).
     *
     * @return non-null extracted stack with actual size, or null if nothing
     *         was extracted.
     */
    public T extractFromLocalCells(final T request, final Actionable type, final IActionSource src) {
        if (request == null) return null;
        if (!shouldExposeLocalCells()) return null;
        if (!this.isReadChannelExposed()) return null;

        this.ensureSourcesCurrent();

        Predicate<T> visibleFilter = this.getVisibleFilter();
        if (visibleFilter != null && !visibleFilter.test(request)) return null;

        long remaining = request.getStackSize();
        if (remaining <= 0) return null;

        T extracted = null;
        for (IMEInventoryHandler<T> cell : sortedByPriorityDesc(this.localCells)) {
            if (remaining <= 0) break;

            T sub = request.copy();
            sub.setStackSize(remaining);
            T got = cell.extractItems(sub, type, src);
            if (got == null || got.getStackSize() <= 0) continue;

            remaining -= got.getStackSize();
            if (extracted == null) {
                extracted = got;
            } else {
                extracted.incStackSize(got.getStackSize());
            }
        }

        return extracted;
    }

    @Override
    public IStorageChannel<T> getChannel() {
        return this.channel;
    }

    /**
     * Return {@code cells} sorted by descending {@link IMEInventoryHandler#getPriority()}.
     * Falls through unchanged when the list has 0 or 1 entries (the common
     * case in the proxy: each provider on Grid A typically contributes a
     * single handler), avoiding allocations on the hot extract path.
     */
    private static <T extends IAEStack<T>> List<IMEInventoryHandler<T>> sortedByPriorityDesc(
            List<IMEInventoryHandler<T>> cells) {
        if (cells.size() < 2) return cells;
        List<IMEInventoryHandler<T>> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparingInt(IMEInventoryHandler<T>::getPriority).reversed());
        return sorted;
    }

    private long getVisibleAmount(T request) {
        IItemList<T> visible = this.channel.createList();
        this.getAvailableItems(visible);

        T visibleStack = visible.findPrecise(request);
        return visibleStack == null ? 0 : visibleStack.getStackSize();
    }

    // ========================= IMEInventoryHandler =========================

    @Override
    public AccessRestriction getAccess() {
        // Read-only: only extraction is allowed
        return AccessRestriction.READ;
    }

    @Override
    public boolean isPrioritized(final T input) {
        return false;
    }

    @Override
    public boolean canAccept(final T input) {
        // No injection allowed
        return false;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        // Passes only matter for injection routing in NetworkInventoryHandler
        // (pass 1 = prioritized/filtered, pass 2 = non-prioritized fallback).
        // Since this proxy is read-only (canAccept returns false, getAccess returns READ),
        // injection never reaches us, so the pass value is irrelevant.
        return true;
    }

    // ========================= Utility =========================

    /**
     * Wrap this handler in a singleton list for ICellProvider.getCellArray().
     * Returns empty list ONLY if we have neither local cells nor a wired
     * front part (peer aggregation source). With a front part wired, we still
     * expose the handler so peer items remain visible even when our own
     * back-grid has no local cells (e.g. pure relay node).
     */
    @SuppressWarnings("rawtypes")
    public List<IMEInventoryHandler> asCellArray() {
        if (this.localCells.isEmpty() && this.frontPart == null) return Collections.emptyList();
        return Collections.singletonList(this);
    }
}
