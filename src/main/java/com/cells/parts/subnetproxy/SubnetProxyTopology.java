package com.cells.parts.subnetproxy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.function.Predicate;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.storage.IStorageChannel;

import com.cells.Cells;


/**
 * Cached edges and adjacency information for subnet proxy nodes, enabling efficient
 * neighborhood and destination queries.
 * The depth constraint is established there, ensuring that neighborhood queries
 * do not traverse too far.
 */
public final class SubnetProxyTopology {

    /** Maximum number of proxy edges a resource can cross */
    private static final int MAX_READ_DEPTH = 2;

    /** Captured channel snapshots and the revisions of their shared edges */
    private final Map<SubnetProxySnapshot<?>, EdgeRevision> inputs = new IdentityHashMap<>();

    /** Channel adjacency being updated for changed connections */
    private final Map<IStorageChannel<?>, ChannelEdges> pending = new IdentityHashMap<>();

    /** Cached incoming and outgoing adjacency, indexed by channel and grid */
    private Map<IStorageChannel<?>, ChannelEdges> channels = new IdentityHashMap<>();

    /**
     * Current graph revision, for applying topology transactions.
     * Captured path changes advance this revision for affected contributions.
     */
    private long revision;

    public long revision() {
        return this.revision;
    }

    /**
     * Collects the nodes that are within the read depth from the given root nodes.
     */
    public Set<SubnetProxyNode> neighborhood(Set<SubnetProxyNode> roots) {
        Set<SubnetProxyNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(roots);

        List<SubnetProxyNode> current = new ArrayList<>(roots);

        for (int depth = 0; depth < MAX_READ_DEPTH; depth++) {
            List<SubnetProxyNode> next = new ArrayList<>();

            for (SubnetProxyNode node : current) {
                for (ChannelEdges channel : this.channels.values()) {
                    for (Edge<?> edge : channel.outgoing.getOrDefault(node, Collections.emptyList())) {
                        if (result.add(edge.destination)) next.add(edge.destination);
                    }
                }

                for (ChannelEdges channel : this.channels.values()) {
                    for (Edge<?> edge : channel.incoming.getOrDefault(node, Collections.emptyList())) {
                        if (result.add(edge.source)) next.add(edge.source);
                    }
                }
            }

            if (next.isEmpty()) break;
            current = next;
        }

        return result;
    }

    /**
     * Collects the destination nodes reachable from the given subnet proxy view.
     */
    public <T extends IAEStack<T>> Set<SubnetProxyNode> destinations(SubnetProxyView<T> origin) {
        Set<SubnetProxyNode> result = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Edge<T> first : adjacent(this.channels, origin.channel().getChannel(), origin.node(), true)) {
            if (!first.available()) continue;

            result.add(first.destination);

            for (Edge<T> second : adjacent(this.channels, origin.channel().getChannel(), first.destination, true)) {
                if (second.available() && second.destination != origin.node()) result.add(second.destination);
            }
        }

        return result;
    }

    /**
     * Capture the current channel snapshots supplied by a subnet proxy Front.
     */
    public void capture(List<SubnetProxySnapshot<?>> snapshots) {
        for (SubnetProxySnapshot<?> snapshot : snapshots) {
            if (snapshot.available() && !this.inputs.containsKey(snapshot)) this.compile(snapshot);
        }
    }

    public void invalidate(List<SubnetProxySnapshot<?>> snapshots) {
        if (snapshots.isEmpty()) return;

        boolean changed = false;
        for (SubnetProxySnapshot<?> snapshot : snapshots) {
            if (snapshot.invalidate()) changed = true;

            EdgeRevision edgeRevision = this.inputs.remove(snapshot);
            if (edgeRevision == null) continue;

            edgeRevision.value++;
            this.edit(snapshot).snapshots.remove(snapshot);
        }

        if (changed) this.revision++;
    }

    /**
     * Commit changed channel edges while retaining unaffected adjacency and filters.
     */
    public void commit() {
        if (this.pending.isEmpty()) return;

        Map<IStorageChannel<?>, ChannelEdges> next = new IdentityHashMap<>(this.channels);
        for (Map.Entry<IStorageChannel<?>, ChannelEdges> entry : this.pending.entrySet()) {
            ChannelEdges channel = entry.getValue();
            for (Edge<?> edge : channel.changed) {
                if (edge.snapshots.isEmpty()) channel.replace(edge, null);
                else edge.compile();
            }

            channel.changed.clear();
            if (channel.outgoing.isEmpty()) next.remove(entry.getKey());
            else next.put(entry.getKey(), channel);
        }

        this.channels = next;
        this.pending.clear();
        this.revision++;
    }

    /**
     * Capture the snapshot for a subnet proxy and update the channel edges accordingly.
     */
    private <T extends IAEStack<T>> void compile(SubnetProxySnapshot<T> snapshot) {
        Edge<T> edge = this.edit(snapshot);
        edge.snapshots.add(snapshot);
        this.inputs.put(snapshot, edge.revision);
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> Edge<T> edit(SubnetProxySnapshot<T> snapshot) {
        ChannelEdges channel = this.pending.computeIfAbsent(snapshot.channel.getChannel(),
            key -> new ChannelEdges(this.channels.get(key)));

        List<Edge<?>> outgoing = channel.outgoing.getOrDefault(snapshot.back, Collections.emptyList());
        for (Edge<?> candidate : outgoing) {
            if (candidate.destination != snapshot.front) continue;
            if (channel.changed.contains(candidate)) return (Edge<T>) candidate;

            Edge<T> edge = new Edge<>((Edge<T>) candidate);
            channel.replace(candidate, edge);
            channel.changed.add(edge);

            return edge;
        }

        Edge<T> edge = new Edge<>(snapshot.back, snapshot.front);
        channel.replace(null, edge);
        channel.changed.add(edge);

        return edge;
    }

    /**
     * Check if a subnet proxy node participates in a given channel (either as source or destination)
     */
    public boolean participates(SubnetProxyNode node, IStorageChannel<?> channel) {
        return available(this.channels, node, channel, true) || available(this.channels, node, channel, false);
    }

    /**
     * Check if a subnet proxy node can forward changes for a given channel.
     */
    public boolean forwards(SubnetProxyNode node, IStorageChannel<?> channel) {
        return available(this.channels, node, channel, true);
    }

    /**
     * Check if a subnet proxy node has available edges for a given channel.
     */
    private static boolean available(Map<IStorageChannel<?>, ChannelEdges> index,
            SubnetProxyNode node, IStorageChannel<?> channel, boolean outgoing) {
        ChannelEdges adjacency = index.get(channel);
        if (adjacency == null) return false;

        Map<SubnetProxyNode, List<Edge<?>>> edges = outgoing ? adjacency.outgoing : adjacency.incoming;
        for (Edge<?> edge : edges.getOrDefault(node, Collections.emptyList())) {
            if (edge.available()) return true;
        }

        return false;
    }

    public <T extends IAEStack<T>> List<SubnetProxySnapshot<T>> getPeerSnapshots(
            SubnetProxyNode destination, IStorageChannel<T> channel) {
        List<SubnetProxySnapshot<T>> result = new ArrayList<>();

        for (Edge<T> edge : adjacent(this.channels, channel, destination, false)) {
            for (SubnetProxySnapshot<T> snapshot : edge.snapshots) {
                if (snapshot.available()) result.add(snapshot);
            }
        }

        return result;
    }

    /**
     * Retrieve the cached list of edges accessible from the given destination node and channel.
     */
    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> List<Edge<T>> adjacent(
            Map<IStorageChannel<?>, ChannelEdges> index, IStorageChannel<T> channel,
            SubnetProxyNode node, boolean outgoing) {
        ChannelEdges adjacency = index.get(channel);
        if (adjacency == null) return Collections.emptyList();

        Map<SubnetProxyNode, List<Edge<?>>> edges = outgoing ? adjacency.outgoing : adjacency.incoming;
        return (List<Edge<T>>) (List<?>) edges.getOrDefault(node, Collections.emptyList());
    }

    /**
     * Create a runnable dispatch() for the given origin view and owned physical changes.
     */
    public <T extends IAEStack<T>> Runnable frame(SubnetProxyView<T> origin, IItemList<T> changes,
            IActionSource source) {
        Map<IStorageChannel<?>, ChannelEdges> channels = this.channels;
        return () -> this.dispatch(origin, changes, channels, source);
    }

    /**
     * Dispatch the given input changes from the origin view to the connected subnet proxy nodes.
     * NOTE: This is the hot path for delta forwarding.
     * TODO: Check how it scales with large networks and high-frequency updates, and optimize if necessary.
     */
    private <T extends IAEStack<T>> void dispatch(SubnetProxyView<T> origin, IItemList<T> input,
            Map<IStorageChannel<?>, ChannelEdges> channels, IActionSource source) {
        if (!origin.node().isRegistered() || !origin.isValid(origin)) return;

        // Group each resource into a retained stack, and onward lists share those references
        Map<SubnetProxyNode, Set<T>> applied = new IdentityHashMap<>();

        Deque<Step<T>> queue = new ArrayDeque<>();
        queue.add(new Step<>(origin.node(), input, 0, null));
        while (!queue.isEmpty()) {
            Step<T> step = queue.remove();
            for (Edge<T> edge : adjacent(channels, origin.channel().getChannel(), step.node, true)) {
                if (edge.source != step.node || edge.destination == origin.node()) continue;

                List<T> onward = null;
                boolean onwardChecked = step.depth + 1 >= MAX_READ_DEPTH;
                for (T change : step.changes) {
                    if (step.incoming != null && !step.incoming.accepts(change)) continue;
                    if (!edge.accepts(change)) continue;

                    if (!onwardChecked) {
                        onwardChecked = true;
                        for (Edge<T> next : adjacent(channels, origin.channel().getChannel(), edge.destination, true)) {
                            if (next.destination == origin.node() || !next.available()) continue;

                            onward = new ArrayList<>();
                            break;
                        }
                    }

                    Set<T> delivered = applied.get(edge.destination);
                    if (delivered == null || !delivered.contains(change)) {
                        SubnetProxyView<T> target = edge.destination.view(origin.channel().getChannel());
                        if (target != null && target.isStable() && target.received(origin.node())) {
                            if (delivered == null) {
                                delivered = Collections.newSetFromMap(new IdentityHashMap<>());
                                applied.put(edge.destination, delivered);
                            }

                            delivered.add(change);
                            try {
                                target.add(origin.node(), change, source);
                            } catch (RuntimeException failure) {
                                Cells.LOGGER.error("Subnet Proxy destination publication failed", failure);
                            }
                        }
                    }

                    if (onward != null) onward.add(change);
                }

                if (onward != null && !onward.isEmpty()) {
                    queue.add(new Step<>(edge.destination, onward, step.depth + 1, edge));
                }
            }
        }
    }

    /**
     * Publish the initializing origin's local quantities to stable destinations.
     */
    public <T extends IAEStack<T>> void supplyInitialized(SubnetProxyView<T> origin) {
        for (Map.Entry<SubnetProxyNode, List<Predicate<T>>> entry : this.paths(origin, true).entrySet()) {
            SubnetProxyView<T> destination = entry.getKey().view(origin.channel().getChannel());
            if (destination != null && destination != origin && destination.isStable()) {
                this.updateContribution(origin.node(), destination, entry.getValue());
            }
        }
    }

    /**
     * Collect channel paths in the requested direction, retaining alternative filters.
     */
    private <T extends IAEStack<T>> Map<SubnetProxyNode, List<Predicate<T>>> paths(
            SubnetProxyView<T> view, boolean outgoing) {
        Map<IStorageChannel<?>, ChannelEdges> snapshot = this.channels;
        Map<SubnetProxyNode, List<Predicate<T>>> paths = new IdentityHashMap<>();

        for (Edge<T> first : adjacent(snapshot, view.channel().getChannel(), view.node(), outgoing)) {
            if (!first.available()) continue;

            SubnetProxyNode intermediate = outgoing ? first.destination : first.source;
            paths.computeIfAbsent(intermediate, key -> new ArrayList<>()).add(first::accepts);

            for (Edge<T> second : adjacent(snapshot, view.channel().getChannel(), intermediate, outgoing)) {
                SubnetProxyNode endpoint = outgoing ? second.destination : second.source;
                if (endpoint == view.node() || !second.available()) continue;

                Edge<T> start = outgoing ? first : second;
                Edge<T> end = outgoing ? second : first;
                paths.computeIfAbsent(endpoint, key -> new ArrayList<>())
                    .add(stack -> start.accepts(stack) && end.accepts(stack));
            }
        }

        return paths;
    }

    /**
     * Gather the local contributions from the remote networks into the specified destination view.
     * This builds the remote contributions for the destination view, after a grid reset.
     * TODO: Adapt `neighborhood()` to make the code clearer and more streamlined.
     */
    public <T extends IAEStack<T>> void gather(SubnetProxyView<T> destination) {
        Map<SubnetProxyNode, List<Predicate<T>>> paths = this.paths(destination, false);
        Set<SubnetProxyNode> origins = Collections.newSetFromMap(new IdentityHashMap<>());
        origins.addAll(paths.keySet());
        origins.addAll(destination.accountedOrigins());
        for (SubnetProxyNode origin : origins) {
            this.updateContribution(origin, destination, paths.get(origin));
        }
    }

    private <T extends IAEStack<T>> void updateContribution(SubnetProxyNode origin,
            SubnetProxyView<T> destination, List<Predicate<T>> predicates) {
        SubnetProxyView<T> source = origin.view(destination.channel().getChannel());
        if (predicates != null && source != null && !source.isStable()) return;

        if (source != null && predicates != null) {
            destination.replace(source, stack -> {
                for (Predicate<T> predicate : predicates) {
                    if (predicate.test(stack)) return true;
                }

                return false;
            });
        } else {
            destination.replace(origin, destination.channel().getChannel().createList());
        }

        if (predicates == null) destination.forget(origin);
    }

    public void clear() {
        for (Map.Entry<SubnetProxySnapshot<?>, EdgeRevision> entry : this.inputs.entrySet()) {
            entry.getKey().invalidate();
            entry.getValue().value++;
        }

        this.inputs.clear();
        this.pending.clear();
        this.channels = new IdentityHashMap<>();
        this.revision++;
    }

    private static final class ChannelEdges {

        private final Map<SubnetProxyNode, List<Edge<?>>> outgoing = new IdentityHashMap<>();
        private final Map<SubnetProxyNode, List<Edge<?>>> incoming = new IdentityHashMap<>();
        private final Set<Edge<?>> changed = Collections.newSetFromMap(new IdentityHashMap<>());

        private ChannelEdges(ChannelEdges previous) {
            if (previous == null) return;

            this.outgoing.putAll(previous.outgoing);
            this.incoming.putAll(previous.incoming);
        }

        private void replace(Edge<?> previous, Edge<?> replacement) {
            Edge<?> edge = previous != null ? previous : replacement;
            replace(this.outgoing, edge.source, previous, replacement);
            replace(this.incoming, edge.destination, previous, replacement);
        }

        private static void replace(Map<SubnetProxyNode, List<Edge<?>>> adjacency,
                SubnetProxyNode node, Edge<?> previous, Edge<?> replacement) {
            List<Edge<?>> edges = new ArrayList<>(adjacency.getOrDefault(node, Collections.emptyList()));
            if (previous == null) edges.add(replacement);
            else if (replacement == null) edges.remove(previous);
            else edges.set(edges.indexOf(previous), replacement);

            if (edges.isEmpty()) adjacency.remove(node);
            else adjacency.put(node, edges);
        }
    }

    /**
     * Invalidation counter shared by committed and queued versions of the same edge
     */
    private static final class EdgeRevision {

        private long value;
    }

    private static final class Edge<T extends IAEStack<T>> {

        private final SubnetProxyNode source;
        private final SubnetProxyNode destination;
        private final List<SubnetProxySnapshot<T>> snapshots = new ArrayList<>();
        private final EdgeRevision revision;
        private long filterRevision;
        private Predicate<T> filter;

        private Edge(SubnetProxyNode source, SubnetProxyNode destination) {
            this.source = source;
            this.destination = destination;
            this.revision = new EdgeRevision();
        }

        private Edge(Edge<T> previous) {
            this.source = previous.source;
            this.destination = previous.destination;
            this.snapshots.addAll(previous.snapshots);
            this.revision = previous.revision;
        }

        private void compile() {
            this.filter = this.snapshots.get(0).channel.mergeFilters(this.snapshots);
            this.filterRevision = this.revision.value;
        }

        private boolean available() {
            for (SubnetProxySnapshot<T> snapshot : this.snapshots) {
                if (snapshot.available()) return true;
            }

            return false;
        }

        private boolean accepts(T stack) {
            // TODO: Pre-compute and cache projected `origin -> A -> B` paths for faster acceptance checks
            //       This would allow us to only do 1 precise check for the 2nd hop, instead of 2.
            //       Issue is added memory and processing overhead in dense networks (100 -> 100 = 10k paths)
            //       If we reset often, rebuilding these lists may become a significant overhead.

            if (stack == null) return false;
            if (this.filterRevision == this.revision.value) return this.filter.test(stack);

            // Recheck surviving connections after an edit changes a captured filter
            // Should be really rare in practice, as the invalidation lasts a couple of ticks tops
            for (SubnetProxySnapshot<T> snapshot : this.snapshots) {
                if (snapshot.accepts(stack)) return true;
            }

            return false;
        }
    }

    private static final class Step<T extends IAEStack<T>> {

        private final SubnetProxyNode node;
        private final Iterable<T> changes;
        private final int depth;
        private final Edge<T> incoming;

        private Step(SubnetProxyNode node, Iterable<T> changes, int depth, Edge<T> incoming) {
            this.node = node;
            this.changes = changes;
            this.depth = depth;
            this.incoming = incoming;
        }
    }
}
