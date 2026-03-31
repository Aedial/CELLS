package com.cells.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.cells.Tags;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.network.packets.PacketSetConfigurableCellCapacity;
import com.cells.network.packets.PacketSetConfigurableCellMaxTypes;
import com.cells.network.packets.PacketSetMaxSlotSize;
import com.cells.network.packets.PacketSetPollingRate;
import com.cells.network.packets.PacketSetPullPushKeepQuantity;
import com.cells.network.packets.PacketSetPullPushQuantity;
import com.cells.network.packets.PacketSetPullPushRate;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.PacketResourceSlot;


/**
 * Network handler for CELLS mod packets.
 * <p>
 * Uses unified PacketResourceSlot for all resource type sync (item, fluid, gas, essentia).
 * Uses unified PacketQuickAddFilter for all quick-add operations.
 */
public class CellsNetworkHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(PacketSetMaxSlotSize.Handler.class, PacketSetMaxSlotSize.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketOpenGui.Handler.class, PacketOpenGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPollingRate.Handler.class, PacketSetPollingRate.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetConfigurableCellCapacity.Handler.class, PacketSetConfigurableCellCapacity.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetConfigurableCellMaxTypes.Handler.class, PacketSetConfigurableCellMaxTypes.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSaveMemoryCardWithFilters.Handler.class, PacketSaveMemoryCardWithFilters.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketClearFilters.Handler.class, PacketClearFilters.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketChangePage.Handler.class, PacketChangePage.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPullPushRate.Handler.class, PacketSetPullPushRate.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPullPushQuantity.Handler.class, PacketSetPullPushQuantity.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPullPushKeepQuantity.Handler.class, PacketSetPullPushKeepQuantity.class, packetId++, Side.SERVER);

        // Unified resource slot packet (bidirectional: handles filter sync for ALL resource types)
        INSTANCE.registerMessage(PacketResourceSlot.ServerHandler.class, PacketResourceSlot.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketResourceSlot.ClientHandler.class, PacketResourceSlot.class, packetId++, Side.CLIENT);

        // Unified quick-add packet (handles quick-add for ALL resource types)
        INSTANCE.registerMessage(PacketQuickAddFilter.Handler.class, PacketQuickAddFilter.class, packetId++, Side.SERVER);
    }
}
