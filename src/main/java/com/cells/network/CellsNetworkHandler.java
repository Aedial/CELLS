package com.cells.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import com.cells.Tags;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketOverlayMessage;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.network.packets.PacketSetConfigurableCellCapacity;
import com.cells.network.packets.PacketSetConfigurableCellMaxTypes;
import com.cells.network.packets.PacketSetMaxSlotSize;
import com.cells.network.packets.PacketSetPollingRate;
import com.cells.network.packets.PacketSetPullPushKeepQuantity;
import com.cells.network.packets.PacketSetPullPushQuantity;
import com.cells.network.packets.PacketSetPullPushRate;
import com.cells.network.packets.PacketsetMaxSlotSizeOverride;
import com.cells.network.packets.PacketOpenSlotOverrideGui;
import com.cells.network.packets.PacketSwitchTab;
import com.cells.network.packets.PacketSyncSlotSizeOverride;
import com.cells.network.packets.PacketChangeFilterMode;
import com.cells.network.packets.PacketJEISubnetProxyFilter;
import com.cells.network.packets.PacketSetProxyPriority;
import com.cells.network.packets.PacketOpenProxyPriority;
import com.cells.network.packets.PacketToggleProxyChannel;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.PacketResourceSlot;
import com.cells.network.sync.PacketStorageSync;


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

        // Storage sync packet (server→client: syncs storage identity + amount per slot)
        INSTANCE.registerMessage(PacketStorageSync.ClientHandler.class, PacketStorageSync.class, packetId++, Side.CLIENT);

        // Tab switching for combined interface
        INSTANCE.registerMessage(PacketSwitchTab.Handler.class, PacketSwitchTab.class, packetId++, Side.SERVER);

        // Server→client overlay messages (displayed above hotbar alongside chat messages)
        INSTANCE.registerMessage(PacketOverlayMessage.ClientHandler.class, PacketOverlayMessage.class, packetId++, Side.CLIENT);

        // Per-slot size override: client→server to set, server→client to sync
        INSTANCE.registerMessage(PacketsetMaxSlotSizeOverride.Handler.class, PacketsetMaxSlotSizeOverride.class, packetId++, Side.SERVER);
        INSTANCE.registerMessage(PacketSyncSlotSizeOverride.ClientHandler.class, PacketSyncSlotSizeOverride.class, packetId++, Side.CLIENT);

        // Open per-slot size override GUI (stores slot index + opens MaxSlotSize GUI)
        INSTANCE.registerMessage(PacketOpenSlotOverrideGui.Handler.class, PacketOpenSlotOverrideGui.class, packetId++, Side.SERVER);

        // Subnet Proxy: filter mode cycling
        INSTANCE.registerMessage(PacketChangeFilterMode.Handler.class, PacketChangeFilterMode.class, packetId++, Side.SERVER);

        // Subnet Proxy: JEI recipe transfer batch filter add
        INSTANCE.registerMessage(PacketJEISubnetProxyFilter.Handler.class, PacketJEISubnetProxyFilter.class, packetId++, Side.SERVER);

        // Subnet Proxy: priority setting for insertion card
        INSTANCE.registerMessage(PacketSetProxyPriority.Handler.class, PacketSetProxyPriority.class, packetId++, Side.SERVER);

        // Subnet Proxy: open AE2 priority GUI
        INSTANCE.registerMessage(PacketOpenProxyPriority.Handler.class, PacketOpenProxyPriority.class, packetId++, Side.SERVER);

        // Subnet Proxy: toggle a single channel's exposure on the front-grid
        INSTANCE.registerMessage(PacketToggleProxyChannel.Handler.class, PacketToggleProxyChannel.class, packetId++, Side.SERVER);
    }
}
