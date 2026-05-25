package com.cells.network.sync;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.blocks.compactingpatternexposer.TileCompactingPatternExposer;


/**
 * Server->client packet for compacting pattern preview synchronization.
 */
public class PacketCompactingPatternPreview implements IMessage {

    private final Map<Integer, TileCompactingPatternExposer.PatternPreview> upwardPreviews = new HashMap<>();
    private final Map<Integer, TileCompactingPatternExposer.PatternPreview> downwardPreviews = new HashMap<>();

    public PacketCompactingPatternPreview() {
    }

    public PacketCompactingPatternPreview(Map<Integer, TileCompactingPatternExposer.PatternPreview> upwardPreviews,
                                          Map<Integer, TileCompactingPatternExposer.PatternPreview> downwardPreviews) {
        this.upwardPreviews.putAll(upwardPreviews);
        this.downwardPreviews.putAll(downwardPreviews);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.upwardPreviews.clear();
        this.downwardPreviews.clear();

        readMap(buf, this.upwardPreviews);
        readMap(buf, this.downwardPreviews);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeMap(buf, this.upwardPreviews);
        writeMap(buf, this.downwardPreviews);
    }

    public Map<Integer, TileCompactingPatternExposer.PatternPreview> getUpwardPreviews() {
        return this.upwardPreviews;
    }

    public Map<Integer, TileCompactingPatternExposer.PatternPreview> getDownwardPreviews() {
        return this.downwardPreviews;
    }

    private static void readMap(ByteBuf buf, Map<Integer, TileCompactingPatternExposer.PatternPreview> target) {
        int count = buf.readInt();

        for (int index = 0; index < count; index++) {
            int slot = buf.readInt();
            boolean present = buf.readBoolean();
            if (!present) {
                target.put(slot, null);
                continue;
            }

            ItemStack output = ByteBufUtils.readItemStack(buf);
            long inputCount = buf.readLong();
            long outputCount = buf.readLong();
            boolean upward = buf.readBoolean();
            long multiplier = buf.readLong();

            target.put(slot, TileCompactingPatternExposer.PatternPreview.create(
                output,
                inputCount,
                outputCount,
                upward,
                multiplier
            ));
        }
    }

    private static void writeMap(ByteBuf buf, Map<Integer, TileCompactingPatternExposer.PatternPreview> source) {
        buf.writeInt(source.size());

        for (Map.Entry<Integer, TileCompactingPatternExposer.PatternPreview> entry : source.entrySet()) {
            buf.writeInt(entry.getKey());

            TileCompactingPatternExposer.PatternPreview preview = entry.getValue();
            if (preview == null) {
                buf.writeBoolean(false);
                continue;
            }

            buf.writeBoolean(true);
            ByteBufUtils.writeItemStack(buf, preview.getOutput());
            buf.writeLong(preview.getInputCount());
            buf.writeLong(preview.getOutputCount());
            buf.writeBoolean(preview.isUpward());
            buf.writeLong(preview.getMultiplier());
        }
    }

    public static class ClientHandler implements IMessageHandler<PacketCompactingPatternPreview, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketCompactingPatternPreview message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketCompactingPatternPreview message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null) return;

            Container container = player.openContainer;
            if (!(container instanceof ICompactingPatternPreviewSyncContainer)) return;

            ((ICompactingPatternPreviewSyncContainer) container).receivePatternPreviews(
                message.getUpwardPreviews(),
                message.getDownwardPreviews()
            );
        }
    }
}
