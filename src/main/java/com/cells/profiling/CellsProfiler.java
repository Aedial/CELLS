package com.cells.profiling;

import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.cells.Tags;


/**
 * Server-owned profiling sessions that finish independently of the initiating player's connection.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class CellsProfiler {

    private static volatile ProfileSession active;
    private static ProfileSession pending;
    private static MinecraftServer server;

    private CellsProfiler() {}

    @Nullable
    public static ProfileSession getSession() {
        ProfileSession session = active;
        return session != null && session.isServerThread() ? session : null;
    }

    @Nullable
    public static ProfileEntry getDeviceEntry(@Nullable Object container) {
        ProfileSession session = getSession();
        return session != null ? session.getDeviceEntry(container) : null;
    }

    public static boolean start(MinecraftServer minecraftServer, ProfileSession.Mode mode,
                                int seconds, @Nullable UUID playerId) {
        if (active != null || pending != null) return false;

        server = minecraftServer;
        pending = new ProfileSession(mode, seconds, playerId);
        return true;
    }

    public static boolean requestStop() {
        ProfileSession session = active != null ? active : pending;
        if (session == null) return false;

        session.requestStop();
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (pending != null) {
            pending.start();
            active = pending;
            pending = null;
        }

        if (active != null) active.nextTick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active == null || !active.isFinished()) return;

        finish("capture finished");
    }

    private static void finish(String reason) {
        ProfileSession session = active;
        active = null;
        if (session == null) return;

        int entries = session.logResults(reason);
        if (server != null && session.playerId != null) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(session.playerId);
            if (player != null) {
                player.sendMessage(new TextComponentTranslation("commands.cells.profile.finished",
                    session.mode.name().toLowerCase(Locale.ROOT), entries));
            }
        }

        server = null;
    }

    public static void serverStopped() {
        finish("server stopped");
        pending = null;
        server = null;
    }
}
