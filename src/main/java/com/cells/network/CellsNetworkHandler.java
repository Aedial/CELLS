package com.cells.network;

import com.cells.Tags;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketQuickAddFluidFilter;
import com.cells.network.packets.PacketQuickAddItemFilter;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.network.packets.PacketSetConfigurableCellCapacity;
import com.cells.network.packets.PacketSetConfigurableCellMaxTypes;
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
        INSTANCE.registerMessage(PacketSetConfigurableCellCapacity.Handler.class, PacketSetConfigurableCellCapacity.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetConfigurableCellMaxTypes.Handler.class, PacketSetConfigurableCellMaxTypes.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketQuickAddItemFilter.Handler.class, PacketQuickAddItemFilter.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketQuickAddFluidFilter.Handler.class, PacketQuickAddFluidFilter.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSaveMemoryCardWithFilters.Handler.class, PacketSaveMemoryCardWithFilters.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketClearFilters.Handler.class, PacketClearFilters.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketChangePage.Handler.class, PacketChangePage.class, packetId++, Side.SERVER);
    }
}
