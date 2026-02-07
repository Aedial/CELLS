package com.cells.util;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Optional;

/**
 * A custom IActionSource that wraps a machine but ensures it's never
 * considered equal to other sources (including MachineSource).
 * <p>
 * This is necessary because AE2's NetworkMonitor uses source equality
 * to detect nested/re-entrant notifications and drops them. When a compacting
 * cell needs to notify cross-tier changes, we need a distinct source identity
 * so our notifications aren't dropped when DriveWatcher also posts with a
 * MachineSource from the same machine.
 * </p>
 */
public class CrossTierActionSource implements IActionSource {

    private final IActionHost machine;

    // Unique instance identity - each CrossTierActionSource is unique
    // This ensures hashCode/equals never match another source
    private final long uniqueId = System.nanoTime() ^ System.identityHashCode(this);

    public CrossTierActionSource(IActionHost machine) {
        this.machine = machine;
    }

    @Override
    public Optional<EntityPlayer> player() {
        return Optional.empty();
    }

    @Override
    public Optional<IActionHost> machine() {
        return Optional.ofNullable(machine);
    }

    @Override
    public <T> Optional<T> context(Class<T> key) {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        // Each CrossTierActionSource instance is unique - never equal to anything else
        return this == o;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(uniqueId);
    }

    @Override
    public String toString() {
        return "CrossTierActionSource{machine=" + (machine != null ? machine.getClass().getSimpleName() : "null") + ", id=" + uniqueId + "}";
    }
}
