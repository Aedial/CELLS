package com.cells.network;

import com.cells.Tags;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.mekanismenergistics.PacketGasSlot;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketQuickAddCreativeEssentiaFilter;
import com.cells.network.packets.PacketQuickAddCreativeFluidFilter;
import com.cells.network.packets.PacketQuickAddCreativeGasFilter;
import com.cells.network.packets.PacketQuickAddCreativeItemFilter;
import com.cells.network.packets.PacketQuickAddFluidFilter;
import com.cells.network.packets.PacketQuickAddItemFilter;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.network.packets.PacketSetConfigurableCellCapacity;
import com.cells.network.packets.PacketSetConfigurableCellMaxTypes;
import com.cells.network.packets.PacketSetCreativeEssentiaFilter;
import com.cells.network.packets.PacketSetMaxSlotSize;
import com.cells.network.packets.PacketSetPollingRate;
import com.cells.network.packets.PacketSyncCreativeEssentiaFilter;

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
        INSTANCE.registerMessage(PacketQuickAddCreativeItemFilter.Handler.class, PacketQuickAddCreativeItemFilter.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketQuickAddCreativeFluidFilter.Handler.class, PacketQuickAddCreativeFluidFilter.class, packetId++, Side.SERVER);

        // Register gas-related packets if MekanismEnergistics is loaded
        if (MekanismEnergisticsIntegration.isModLoaded()) registerGasPackets();

        // Register essentia-related packets if ThaumicEnergistics is loaded
        if (ThaumicEnergisticsIntegration.isModLoaded()) registerEssentiaPackets();
    }

    /**
     * Register gas interface packets.
     * Called only when MekanismEnergistics is loaded.
     */
    private static void registerGasPackets() {
        // PacketGasSlot is bidirectional: client sends filter updates, server sends sync
        INSTANCE.registerMessage(PacketGasSlot.ServerHandler.class, PacketGasSlot.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketGasSlot.ClientHandler.class, PacketGasSlot.class, packetId++, Side.CLIENT);

        // Creative gas cell quick-add
        INSTANCE.registerMessage(PacketQuickAddCreativeGasFilter.Handler.class,
            PacketQuickAddCreativeGasFilter.class, packetId++, Side.SERVER);
    }

    /**
     * Register essentia-related packets.
     * Called only when ThaumicEnergistics is loaded.
     */
    private static void registerEssentiaPackets() {
        INSTANCE.registerMessage(PacketQuickAddCreativeEssentiaFilter.Handler.class,
            PacketQuickAddCreativeEssentiaFilter.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSetCreativeEssentiaFilter.Handler.class,
            PacketSetCreativeEssentiaFilter.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSyncCreativeEssentiaFilter.Handler.class,
            PacketSyncCreativeEssentiaFilter.class, packetId++, Side.CLIENT);
    }
}
