package com.cells.scanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import appeng.api.util.AEPartLocation;

import com.cells.Tags;


/**
 * Last completed scan for each player's scanner during the current login session.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class InterfaceScannerCache {

    public static final class Snapshot {

        public final BlockPos rootPos;
        public final int dimension;
        public final AEPartLocation side;
        public final boolean includeSubnets;
        public final List<InterfaceScanEntry> entries;

        public Snapshot(BlockPos rootPos, int dimension, AEPartLocation side, boolean includeSubnets,
                         Collection<InterfaceScanEntry> entries) {
            this.rootPos = rootPos.toImmutable();
            this.dimension = dimension;
            this.side = side;
            this.includeSubnets = includeSubnets;
            this.entries = new ArrayList<>(entries);
        }
    }

    private static final Map<UUID, Map<UUID, Snapshot>> scans = new HashMap<>();

    private InterfaceScannerCache() {}

    public static UUID getId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (!tag.hasUniqueId("ScannerId")) tag.setUniqueId("ScannerId", UUID.randomUUID());
        return tag.getUniqueId("ScannerId");
    }

    @Nullable
    public static Snapshot get(EntityPlayer player, UUID scannerId) {
        Map<UUID, Snapshot> playerScans = scans.get(player.getUniqueID());
        return playerScans != null ? playerScans.get(scannerId) : null;
    }

    public static void put(EntityPlayer player, UUID scannerId, Snapshot snapshot) {
        scans.computeIfAbsent(player.getUniqueID(), ignored -> new HashMap<>()).put(scannerId, snapshot);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        scans.remove(event.player.getUniqueID());
    }

    public static void clear() {
        scans.clear();
    }
}
