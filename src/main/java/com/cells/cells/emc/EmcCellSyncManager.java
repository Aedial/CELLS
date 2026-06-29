package com.cells.cells.emc;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Periodically flushes buffered EMC from loaded EMC cells into the bound player pool.
 * This ensures the EMC is not kept hostage in the cell's internal buffer for too long,
 * even if the cell is not being actively accessed.
 */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class EmcCellSyncManager {

    private static final Set<EmcCellInventory> trackedInventories =
        Collections.newSetFromMap(new WeakHashMap<EmcCellInventory, Boolean>());

    private static int ticksUntilFlush = 0;

    private EmcCellSyncManager() {}

    public static void track(EmcCellInventory inventory) {
        if (inventory != null) trackedInventories.add(inventory);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (trackedInventories.isEmpty()) return;

        int interval = Math.max(1, CellsConfig.general.emcCellSyncIntervalTicks);
        if (++ticksUntilFlush < interval) return;

        ticksUntilFlush = 0;

        // Could check EMC to avoid flushing when we're at max, but aggregating all providers is annoying
        for (EmcCellInventory inventory : trackedInventories.toArray(new EmcCellInventory[0])) {
            if (inventory != null) inventory.flushBufferedEmc();
        }
    }
}