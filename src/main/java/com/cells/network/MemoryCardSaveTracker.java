package com.cells.network;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;


/**
 * Tracks players who are performing a "save memory card with filters" action.
 * This is used to prevent the normal memory card and block handling from running
 * when we're handling a custom save-with-filters operation.
 *
 * The flag is set when the client sends a PacketSaveMemoryCardWithFilters packet,
 * and cleared after the RightClickBlock event is handled on the server.
 */
public class MemoryCardSaveTracker {

    // Map of player UUID to the position they're interacting with
    // Using ConcurrentHashMap for thread safety since packets and events may run on different threads
    private static final Map<UUID, PendingSave> pendingSaves = new ConcurrentHashMap<>();

    /**
     * Mark a player as having a pending "save with filters" action.
     * Call this when receiving PacketSaveMemoryCardWithFilters.
     */
    public static void markPendingSave(EntityPlayer player, BlockPos pos) {
        pendingSaves.put(player.getUniqueID(), new PendingSave(pos, System.currentTimeMillis()));
    }

    /**
     * Check if a player has a pending "save with filters" action for a specific position.
     * Also clears stale entries (older than 1 second) to prevent memory leaks.
     */
    public static boolean hasPendingSave(EntityPlayer player, BlockPos pos) {
        UUID uuid = player.getUniqueID();
        PendingSave pending = pendingSaves.get(uuid);

        if (pending == null) return false;

        // Expire old entries (more than 1 second old)
        if (System.currentTimeMillis() - pending.timestamp > 1000) {
            pendingSaves.remove(uuid);
            return false;
        }

        // Check if position matches
        return pos.equals(pending.pos);
    }

    /**
     * Clear the pending save flag for a player.
     * Call this after handling the custom save.
     */
    public static void clearPendingSave(EntityPlayer player) {
        pendingSaves.remove(player.getUniqueID());
    }

    /**
     * Clear all pending saves (used for cleanup on server shutdown).
     */
    public static void clearAll() {
        pendingSaves.clear();
    }

    private static class PendingSave {
        final BlockPos pos;
        final long timestamp;

        PendingSave(BlockPos pos, long timestamp) {
            this.pos = pos;
            this.timestamp = timestamp;
        }
    }
}
