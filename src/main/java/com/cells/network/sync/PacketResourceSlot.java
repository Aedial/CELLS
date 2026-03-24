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
 * Unified packet for synchronizing resource slot updates between client and server.
 * <p>
 * This single packet replaces all type-specific packets (PacketFluidSlot, PacketGasSlot, etc.)
 * by including a ResourceType discriminator that determines how to serialize/deserialize
 * the resource data.
 * <p>
 * Usage:
 * <ul>
 *   <li>Client → Server: Send filter changes from GUI</li>
 *   <li>Server → Client: Sync filter state on container open and changes</li>
 * </ul>
 */
public class PacketResourceSlot implements IMessage {

    private ResourceType type;
    private final Map<Integer, Object> resources;

    /**
     * Default constructor for deserialization.
     */
    public PacketResourceSlot() {
        this.resources = new HashMap<>();
    }

    /**
     * Create a packet for a single slot update.
     */
    public PacketResourceSlot(ResourceType type, int slot, Object resource) {
        this.type = type;
        this.resources = new HashMap<>();
        this.resources.put(slot, resource);
    }

    /**
     * Create a packet with multiple slot updates.
     */
    public PacketResourceSlot(ResourceType type, Map<Integer, Object> resources) {
        this.type = type;
        this.resources = new HashMap<>(resources);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = ResourceType.values()[buf.readByte()];
        int count = buf.readInt();

        this.resources.clear();
        for (int i = 0; i < count; i++) {
            int slot = buf.readInt();
            Object resource = this.type.read(buf);
            this.resources.put(slot, resource);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type.ordinal());
        buf.writeInt(this.resources.size());

        for (Map.Entry<Integer, Object> entry : this.resources.entrySet()) {
            buf.writeInt(entry.getKey());
            this.type.write(buf, entry.getValue());
        }
    }

    /**
     * @return The resource type being synced.
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * @return The map of slot index to resource.
     */
    public Map<Integer, Object> getResources() {
        return resources;
    }

    // ================================= Handlers =================================

    /**
     * Client-side message handler.
     * Updates the container's filters for rendering.
     */
    public static class ClientHandler implements IMessageHandler<PacketResourceSlot, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketResourceSlot message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketResourceSlot message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            Container container = player.openContainer;
            if (container instanceof IResourceSyncContainer) {
                ((IResourceSyncContainer) container).receiveResourceSlots(
                    message.type, message.resources
                );
            }
        }
    }

    /**
     * Server-side message handler.
     * Handles filter updates from client.
     */
    public static class ServerHandler implements IMessageHandler<PacketResourceSlot, IMessage> {

        @Override
        public IMessage onMessage(PacketResourceSlot message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                Container container = ctx.getServerHandler().player.openContainer;
                if (container instanceof IResourceSyncContainer) {
                    ((IResourceSyncContainer) container).receiveResourceSlots(
                        message.type, message.resources
                    );
                }
            });
            return null;
        }
    }
}
