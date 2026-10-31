package com.cells.network.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.client.WailaProbeClient;


public class PacketProbeData implements IMessage {

    private int dimension;
    private BlockPos pos;
    private int partSide;
    private long requestId;
    private NBTTagCompound tag;

    public PacketProbeData() {}

    public PacketProbeData(int dimension, BlockPos pos, int partSide, long requestId, NBTTagCompound tag) {
        this.dimension = dimension;
        this.pos = pos;
        this.partSide = partSide;
        this.requestId = requestId;
        this.tag = tag;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dimension = buf.readInt();
        this.pos = BlockPos.fromLong(buf.readLong());
        this.partSide = buf.readInt();
        this.requestId = buf.readLong();
        this.tag = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.dimension);
        buf.writeLong(this.pos.toLong());
        buf.writeInt(this.partSide);
        buf.writeLong(this.requestId);
        ByteBufUtils.writeTag(buf, this.tag);
    }

    public static class Handler implements IMessageHandler<PacketProbeData, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketProbeData message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> WailaProbeClient.receive(
                    message.dimension, message.pos, message.partSide, message.requestId, message.tag));
            return null;
        }
    }
}
