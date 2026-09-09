package com.cells.network.sync;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Server→client packet for synchronizing storage slot data (resource identity + amount).
 * <p>
 * Structurally similar to {@link PacketResourceSlot} but one-directional (server→client only)
 * and routes to {@link IStorageSyncContainer#receiveStorageSlots} instead of
 * {@link IResourceSyncContainer#receiveResourceSlots}.
 */
public class PacketStorageSync implements IMessage {

    private ResourceType type;
    private int directionTab = -1;
    private final Map<Integer, Object> storageSlots;

    /**
     * Default constructor for deserialization.
     */
    public PacketStorageSync() {
        this.storageSlots = new HashMap<>();
    }

    /**
     * Create a packet for a single slot update.
     */
    public PacketStorageSync(ResourceType type, int slot, Object stack) {
        this.type = type;
        this.storageSlots = new HashMap<>();
        this.storageSlots.put(slot, stack);
    }

    public PacketStorageSync(ResourceType type, int directionTab, int slot, Object stack) {
        this(type, slot, stack);
        this.directionTab = directionTab;
    }

    /**
     * Create a packet with multiple slot updates (bulk sync).
     */
    public PacketStorageSync(ResourceType type, Map<Integer, Object> storageSlots) {
        this.type = type;
        this.storageSlots = new HashMap<>(storageSlots);
    }

    public PacketStorageSync(ResourceType type, int directionTab, Map<Integer, Object> storageSlots) {
        this(type, storageSlots);
        this.directionTab = directionTab;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = ResourceType.values()[buf.readByte()];
        this.directionTab = buf.readByte();
        int count = buf.readInt();

        this.storageSlots.clear();
        for (int i = 0; i < count; i++) {
            int slot = buf.readInt();
            Object resource = this.type.read(buf);
            this.storageSlots.put(slot, resource);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type.ordinal());
        buf.writeByte(this.directionTab);
        buf.writeInt(this.storageSlots.size());

        for (Map.Entry<Integer, Object> entry : this.storageSlots.entrySet()) {
            buf.writeInt(entry.getKey());
            this.type.write(buf, entry.getValue());
        }
    }

    // ================================= Client Handler =================================

    /**
     * Client-side message handler.
     * Updates the container's storage slots for rendering.
     */
    public static class ClientHandler implements IMessageHandler<PacketStorageSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketStorageSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketStorageSync message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            Container container = player.openContainer;
            if (container instanceof IStorageSyncContainer) {
                ((IStorageSyncContainer) container).receiveStorageSlots(
                    message.type, message.directionTab, message.storageSlots
                );
            }
        }
    }
}
