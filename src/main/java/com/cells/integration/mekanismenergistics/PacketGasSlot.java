package com.cells.integration.mekanismenergistics;

import java.io.IOException;
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

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;


/**
 * Packet for synchronizing gas slot updates between client and server.
 * Used by ContainerGasInterface to sync filter gas configurations.
 */
public class PacketGasSlot implements IMessage {

    private final Map<Integer, IAEGasStack> gases;

    public PacketGasSlot() {
        this.gases = new HashMap<>();
    }

    /**
     * Create a packet from pre-serialized data.
     */
    public PacketGasSlot(ByteBuf data) {
        this.gases = new HashMap<>();
        readGasMap(data, this.gases);
    }

    /**
     * Create a packet with a single slot update.
     */
    public PacketGasSlot(int slot, IAEGasStack gas) {
        this.gases = new HashMap<>();
        this.gases.put(slot, gas);
    }

    /**
     * Create a packet with multiple slot updates.
     */
    public PacketGasSlot(Map<Integer, IAEGasStack> gases) {
        this.gases = new HashMap<>(gases);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.gases.clear();
        readGasMap(buf, this.gases);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeGasMap(buf, this.gases);
    }

    private static void readGasMap(ByteBuf buf, Map<Integer, IAEGasStack> target) {
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            int slot = buf.readInt();
            boolean hasGas = buf.readBoolean();
            if (hasGas) {
                IAEGasStack gas = AEGasStack.of(buf);
                target.put(slot, gas);
            } else {
                target.put(slot, null);
            }
        }
    }

    private static void writeGasMap(ByteBuf buf, Map<Integer, IAEGasStack> source) {
        buf.writeInt(source.size());
        for (Map.Entry<Integer, IAEGasStack> entry : source.entrySet()) {
            buf.writeInt(entry.getKey());
            IAEGasStack gas = entry.getValue();
            if (gas == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                try {
                    gas.writeToPacket(buf);
                } catch (IOException e) {
                    // Should not happen for ByteBuf
                }
            }
        }
    }

    /**
     * Client-side message handler.
     * Updates the container's cached gas filters for rendering.
     */
    public static class ClientHandler implements IMessageHandler<PacketGasSlot, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketGasSlot message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketGasSlot message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            Container container = player.openContainer;
            if (container instanceof IGasSyncContainer) {
                ((IGasSyncContainer) container).receiveGasSlots(message.gases);
            }
        }
    }

    /**
     * Server-side message handler.
     * Handles gas filter updates from client.
     */
    public static class ServerHandler implements IMessageHandler<PacketGasSlot, IMessage> {

        @Override
        public IMessage onMessage(PacketGasSlot message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                Container container = ctx.getServerHandler().player.openContainer;
                if (container instanceof IGasSyncContainer) {
                    ((IGasSyncContainer) container).receiveGasSlots(message.gases);
                }
            });
            return null;
        }
    }
}
