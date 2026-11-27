package com.cells.parts.subnetproxy;

import java.lang.reflect.Field;
import java.util.Collection;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.hooks.TickHandler;


/**
 * Recovery handler for a grid. It gates the recovery (ensuring it is stable enough for a safe baseline),
 * withdraws previous quantities, captures a live baseline, and imports the new quantities from neighbors.
 */
public final class SubnetProxyRefresh {

    /** Optional AE2 queue accessor for pending deltas, null if unavailable */
    private static final Field SERVER_QUEUE = findQueue();

    /** Current (or previous) view of the subnet proxy */
    private final SubnetProxyView<?> view;

    /** Tick this job was submitted, for recovery at the next tick (if valid) */
    private long submitTick = -1;

    /** Listing revision captured during submission */
    private long viewRevision;

    /** Source-membership revision captured during submission */
    private long membershipRevision;

    /** Participation generation captured during submission */
    private long generation;

    /** Grid input revision captured during submission */
    private long inputRevision;

    public SubnetProxyRefresh(SubnetProxyView<?> view) {
        this.view = view;
    }

    /**
     * Submit a reset request for this view if it is eligible.
     * It ensures that we have been stable (no resets) for long enough and the tasks queue is empty.
     * The reset <i>should</i> take place at the next tick, if all conditions remain valid.
     */
    public void submitReset() {
        if (!this.view.isTracking() || !this.view.node().isRegistered()
            || SubnetProxyManager.hasPendingInput(this.view.node())
            || this.view.node().awaitingReset(this.view)
            || SubnetProxyManager.tick() < this.view.deadline() || !queueEmpty()) return;

        this.viewRevision = this.view.revision();
        this.membershipRevision = this.view.node().getMembershipRevision();
        this.generation = this.view.node().getGeneration();
        this.inputRevision = this.view.node().getInputRevision();
        this.submitTick = SubnetProxyManager.tick();
    }

    /**
     * Check if this job is still valid for execution based on the current state of the view and the system.
     * A change in the grid's own revisions will invalidate the job.
     */
    private boolean isValid() {
        return this.view.isTracking() && this.view.node().isRegistered()
            && this.view.isValid(this.view)
            && this.inputRevision == this.view.node().getInputRevision()
            && !SubnetProxyManager.hasPendingInput(this.view.node())
            && !this.view.node().awaitingReset(this.view)
            && this.view.revision() == this.viewRevision
            && this.view.node().getMembershipRevision() == this.membershipRevision
            && this.view.node().getGeneration() == this.generation
            && SubnetProxyManager.tick() >= this.view.deadline() && queueEmpty();
    }

    public boolean start() {
        if (this.submitTick != SubnetProxyManager.tick() - 1) return false;

        this.submitTick = -1;
        return this.isValid() && this.acquireBaseline(this.view);
    }

    private <T extends IAEStack<T>> boolean acquireBaseline(SubnetProxyView<T> target) {
        target.withdraw();

        if (!this.isValid()) return false;

        IItemList<T> baseline = target.channel().getChannel().createList();
        target.beginAcquisition();

        // TODO: Add baseline aquisition optimization when all tests pass
        //       -> Iif we have ABSOLUTELY no passthrough on the grid,
        //          we can fetch the grid cache directly, without using getAvailableItems
        //       -> There is no need for tasks queue checking, as the cache is synchronized with it already

        try {
            for (SubnetProxyProvider.Handler<T> handler : target.node().resolve(target.channel())) {
                IItemList<T> contents = target.channel().getChannel().createList();
                handler.inventory.getAvailableItems(contents);

                for (T stack : contents) {
                    if (stack.getStackSize() > 0) baseline.addStorage(SubnetProxyView.quantity(stack, stack.getStackSize()));
                }

                if (handler.provider.resolve(target.node()) == null || !this.isValid()) return false;
            }
        } finally {
            target.endAcquisition();
        }

        if (!this.isValid()) return false;

        // Set up baseline, advertise it, and import remote contributions
        target.install(baseline);
        SubnetProxyManager.topology().gather(target);
        SubnetProxyManager.supplyInitialized(target);

        if (!this.isValid() || !target.isStable()) {
            target.rejectCandidate();

            return false;
        }

        target.enableEnumeration();

        return true;
    }

    /**
     * Get the AE2 queue for remaining forwarding tasks
     */
    private static Field findQueue() {
        try {
            Class<?> type = Class.forName("appeng.hooks.TickHandler");
            Field field = type.getDeclaredField("serverQueue");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * Check if the AE2 tasks queue is empty (or not accessible).
     * A non-accessible queue is not a failure, as it would shut down the proxy otherwise.
     */
    public static boolean queueEmpty() {
        if (SERVER_QUEUE == null) return true;

        try {
            Object queue = SERVER_QUEUE.get(TickHandler.INSTANCE);
            return !(queue instanceof Collection) || ((Collection<?>) queue).isEmpty();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return true;
        }
    }
}
