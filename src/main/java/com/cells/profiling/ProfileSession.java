package com.cells.profiling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.parts.AEBasePart;

import com.cells.Cells;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;


/**
 * Server-thread capture state and INFO reports for the requested profiling mode.
 */
public final class ProfileSession {

    public enum Mode {
        ADAPTIVE, DEVICES, SOURCES
    }

    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final double TICK_BUDGET_MILLIS = 50.0;
    private static final double PERCENT = 100.0;

    private final Thread serverThread = Thread.currentThread();
    private final Map<Object, ProfileEntry> entries = new IdentityHashMap<>();
    private final long durationNanos;
    private long started;
    private long ticks;
    private boolean stopRequested;

    public final Mode mode;
    @Nullable
    public final UUID playerId;

    public ProfileSession(Mode mode, int seconds, @Nullable UUID playerId) {
        this.mode = mode;
        this.playerId = playerId;
        this.durationNanos = seconds * NANOS_PER_SECOND;
    }

    public void start() {
        if (this.mode == Mode.ADAPTIVE) ProfileDiscovery.collect(this);
        this.started = System.nanoTime();
    }

    public boolean isServerThread() {
        return Thread.currentThread() == this.serverThread;
    }

    public void nextTick() {
        this.ticks++;
    }

    public long getTicks() {
        return this.ticks;
    }

    public void requestStop() {
        this.stopRequested = true;
    }

    public boolean isFinished() {
        return this.stopRequested || System.nanoTime() - this.started >= this.durationNanos;
    }

    @Nullable
    public ProfileEntry getDeviceEntry(@Nullable Object container) {
        if (this.mode != Mode.DEVICES || container == null) return null;

        return this.getEntry(container, null);
    }

    @Nullable
    public ProfileEntry getCellEntry(Object inventory, @Nullable IActionSource source) {
        if (this.mode == Mode.DEVICES && inventory instanceof ICellProfilingInventory) {
            return this.getDeviceEntry(((ICellProfilingInventory) inventory).getProfilingContainer());
        }

        if (this.mode != Mode.SOURCES || source == null) return null;

        IActionHost machine = source.machine().orElse(null);
        return machine != null ? this.getEntry(machine, null) : null;
    }

    @Nullable
    public ProfileEntry getAdaptiveEntry(IInterfaceHost host, IInterfaceLogic logic, boolean exporting) {
        if (this.mode != Mode.ADAPTIVE || logic.getPollingRate() != 0) return null;

        ProfileEntry entry = this.entries.get(logic);
        if (entry != null) return entry;

        String detail = logic.getTypeName() + (exporting ? " export" : " import");
        return this.getEntry(logic, describe(host) + " [" + detail + "]");
    }

    private ProfileEntry getEntry(Object owner, @Nullable String description) {
        ProfileEntry entry = this.entries.get(owner);
        if (entry == null) {
            entry = new ProfileEntry(this, description != null ? description : describe(owner));
            this.entries.put(owner, entry);
        }

        return entry;
    }

    public int logResults(String reason) {
        double elapsedSeconds = (System.nanoTime() - this.started) / (double) NANOS_PER_SECOND;
        List<ProfileEntry> sorted = new ArrayList<>(this.entries.values());
        Comparator<ProfileEntry> comparator = this.mode == Mode.ADAPTIVE
            ? Comparator.comparingLong(ProfileEntry::getPolls)
            : Comparator.comparingLong(ProfileEntry::getTotalNanos);
        sorted.sort(comparator.reversed().thenComparing(entry -> entry.description));

        Cells.LOGGER.info(String.format(Locale.ROOT,
            "CELLS %s profile: %.3f seconds, %d server ticks, %d entries (%s)",
            this.mode.name().toLowerCase(Locale.ROOT), elapsedSeconds, this.ticks, sorted.size(), reason));

        if (this.mode == Mode.ADAPTIVE) {
            Cells.LOGGER.info("Scheduled network polling of loaded CELLS adaptive logics; sorted by polls/tick");
        } else {
            Cells.LOGGER.info("CELLS measurements only; averages include idle server ticks; tick budget = 50 ms");
            Cells.LOGGER.info(this.mode == Mode.SOURCES
                ? "Machine sources of CELLS cell calls; enumeration and deferred work have no caller attribution"
                : "Device times include downstream work and each container's deferred work; rows can overlap");
        }

        for (ProfileEntry entry : sorted) {
            if (this.mode == Mode.ADAPTIVE) this.logAdaptive(entry);
            else this.logTiming(entry);
        }

        return sorted.size();
    }

    private void logAdaptive(ProfileEntry entry) {
        double capturedTicks = Math.max(1L, this.ticks);
        String interval = entry.getPolls() == 0 ? "n/a"
            : String.format(Locale.ROOT, "%.3f ticks", capturedTicks / entry.getPolls());
        double useful = entry.getPolls() == 0 ? 0.0
            : PERCENT * entry.getSuccessfulPolls() / entry.getPolls();
        Cells.LOGGER.info(String.format(Locale.ROOT,
            "%s | polls/tick=%.6f | average interval=%s | polls=%d | useful=%.2f%%",
            entry.description, entry.getPolls() / capturedTicks, interval, entry.getPolls(), useful));
    }

    private void logTiming(ProfileEntry entry) {
        double capturedTicks = Math.max(1L, this.ticks);
        double averageMillis = entry.getTotalNanos() / NANOS_PER_MILLISECOND / capturedTicks;
        long calls = this.mode == Mode.SOURCES ? entry.getCellCalls() : entry.getCalls();
        double averageCallMillis = calls == 0 ? 0.0
            : entry.getTotalNanos() / NANOS_PER_MILLISECOND / calls;
        Cells.LOGGER.info(String.format(Locale.ROOT,
            "%s | avg=%.6f ms/tick | budget=%.3f%% | max=%.6f ms/tick | %s=%.6f | avg/call=%.6f ms",
            entry.description, averageMillis, PERCENT * averageMillis / TICK_BUDGET_MILLIS,
            entry.getMaxTickNanos() / NANOS_PER_MILLISECOND,
            this.mode == Mode.SOURCES ? "cell calls/tick" : "calls/tick", calls / capturedTicks, averageCallMillis));

        if (entry.getCellCalls() > 0) {
            Cells.LOGGER.info("  cell calls={} | simulations={} | actual={} | empty={}",
                entry.getCellCalls(), entry.getSimulations(),
                entry.getCellCalls() - entry.getSimulations(), entry.getEmptyCalls());
        }

        for (ProfileOperation operation : ProfileOperation.values()) {
            long operationCalls = entry.getCalls(operation);
            if (operationCalls == 0) continue;

            Cells.LOGGER.info(String.format(Locale.ROOT, "  %s: %.6f ms/tick | calls=%d",
                operation.label, entry.getNanos(operation) / NANOS_PER_MILLISECOND / capturedTicks, operationCalls));
        }
    }

    private static String describe(Object owner) {
        String name = owner.getClass().getSimpleName();
        if (owner instanceof IInterfaceHost) {
            IInterfaceHost host = (IInterfaceHost) owner;
            return location(name, host.getHostWorld(), host.getHostPos(), host.getPartSide());
        }

        if (owner instanceof TileEntity) {
            TileEntity tile = (TileEntity) owner;
            return location(name, tile.getWorld(), tile.getPos(), null);
        }

        if (owner instanceof IActionHost) {
            IGridNode node = ((IActionHost) owner).getActionableNode();
            DimensionalCoord coordinate = node != null ? node.getGridBlock().getLocation() : null;
            if (coordinate != null) {
                AEPartLocation side = owner instanceof AEBasePart ? ((AEBasePart) owner).getSide() : null;
                return location(name, coordinate.getWorld(), coordinate.getPos(), side);
            }
        }

        return name + " [location unavailable]";
    }

    private static String location(String name, @Nullable World world, BlockPos pos,
                                   @Nullable AEPartLocation side) {
        String dimension = world != null ? Integer.toString(world.provider.getDimension()) : "unavailable";
        String facing = side != null && side != AEPartLocation.INTERNAL ? " side=" + side.name() : "";
        return name + " [DIM " + dimension + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
            + facing + "]";
    }
}
