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
 * Bidirectional packet for compacting pattern multiplier sync.
 * <p>
 * Client -> Server: sends per-slot multiplier edits from the exposer GUI.
 * Server -> Client: keeps the open container in sync after validation or reload.
 */
public class PacketCompactingPatternMultiplier implements IMessage {

    private final Map<Integer, Long> multipliers = new HashMap<>();

    public PacketCompactingPatternMultiplier() {
    }

    public PacketCompactingPatternMultiplier(int slot, long multiplier) {
        this.multipliers.put(slot, multiplier);
    }

    public PacketCompactingPatternMultiplier(Map<Integer, Long> multipliers) {
        this.multipliers.putAll(multipliers);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.multipliers.clear();

        int count = buf.readInt();
        for (int index = 0; index < count; index++) {
            this.multipliers.put(buf.readInt(), buf.readLong());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.multipliers.size());

        for (Map.Entry<Integer, Long> entry : this.multipliers.entrySet()) {
            buf.writeInt(entry.getKey());
            buf.writeLong(entry.getValue());
        }
    }

    public Map<Integer, Long> getMultipliers() {
        return this.multipliers;
    }

    public static class ClientHandler implements IMessageHandler<PacketCompactingPatternMultiplier, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCompactingPatternMultiplier message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketCompactingPatternMultiplier message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            Container container = player.openContainer;
            if (!(container instanceof ICompactingPatternMultiplierSyncContainer)) return;

            ((ICompactingPatternMultiplierSyncContainer) container).receivePatternMultipliers(message.getMultipliers());
        }
    }

    public static class ServerHandler implements IMessageHandler<PacketCompactingPatternMultiplier, IMessage> {

        @Override
        public IMessage onMessage(PacketCompactingPatternMultiplier message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                Container container = ctx.getServerHandler().player.openContainer;
                if (!(container instanceof ICompactingPatternMultiplierSyncContainer)) return;

                ((ICompactingPatternMultiplierSyncContainer) container).receivePatternMultipliers(message.getMultipliers());
            });
            return null;
        }
    }
}