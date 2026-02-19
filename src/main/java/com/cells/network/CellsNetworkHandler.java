package com.cells.network;

import com.cells.Tags;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketSetMaxSlotSize;
import com.cells.network.packets.PacketSetPollingRate;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;


/**
 * Network handler for CELLS mod packets.
 */
public class CellsNetworkHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MODID);

    private static int packetId = 0;

    public static void init() {
        INSTANCE.registerMessage(PacketSetMaxSlotSize.Handler.class, PacketSetMaxSlotSize.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketOpenGui.Handler.class, PacketOpenGui.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetPollingRate.Handler.class, PacketSetPollingRate.class, packetId++, Side.SERVER);
    }
}
