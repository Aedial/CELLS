package com.cells.network.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.cells.integration.waila.WailaProbeData;
import com.cells.network.CellsNetworkHandler;


public class PacketRequestProbeData implements IMessage {

    private int dimension;
    private BlockPos pos;
    private int partSide;
    private long requestId;

    public PacketRequestProbeData() {}

    public PacketRequestProbeData(int dimension, BlockPos pos, int partSide, long requestId) {
        this.dimension = dimension;
        this.pos = pos;
        this.partSide = partSide;
        this.requestId = requestId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dimension = buf.readInt();
        this.pos = BlockPos.fromLong(buf.readLong());
        this.partSide = buf.readInt();
        this.requestId = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.dimension);
        buf.writeLong(this.pos.toLong());
        buf.writeInt(this.partSide);
        buf.writeLong(this.requestId);
    }

    public static class Handler implements IMessageHandler<PacketRequestProbeData, IMessage> {

        @Override
        public IMessage onMessage(PacketRequestProbeData message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> sendData(player, message));
            return null;
        }

        @SuppressWarnings("deprecation")
        private void sendData(EntityPlayerMP player, PacketRequestProbeData message) {
            World world = player.getServerWorld();
            if (world.provider.getDimension() != message.dimension || !world.isBlockLoaded(message.pos)) return;
            double reach = player.interactionManager.getBlockReachDistance() + 2.0;
            if (player.getDistanceSq(message.pos.getX() + 0.5, message.pos.getY() + 0.5,
                    message.pos.getZ() + 0.5) > reach * reach) return;

            TileEntity tile = world.getTileEntity(message.pos);
            Object target = tile;
            if (message.partSide >= 0) {
                AEPartLocation[] sides = AEPartLocation.values();
                if (!(tile instanceof IPartHost) || message.partSide >= sides.length) return;
                target = ((IPartHost) tile).getPart(sides[message.partSide]);
            } else if (message.partSide != -1) {
                return;
            }

            NBTTagCompound tag = new NBTTagCompound();
            WailaProbeData.write(target, tag);
            CellsNetworkHandler.INSTANCE.sendTo(new PacketProbeData(
                    message.dimension, message.pos, message.partSide, message.requestId, tag), player);
        }
    }
}
