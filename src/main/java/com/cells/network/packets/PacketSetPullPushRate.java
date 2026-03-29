package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.items.pullpush.ContainerPullPushCard;


/**
 * Packet to set the interval for Pull/Push Cards.
 */
public class PacketSetPullPushRate implements IMessage {

    private int interval;

    public PacketSetPullPushRate() {
    }

    public PacketSetPullPushRate(int interval) {
        this.interval = interval;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.interval = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.interval);
    }

    public static class Handler implements IMessageHandler<PacketSetPullPushRate, IMessage> {
        @Override
        public IMessage onMessage(PacketSetPullPushRate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerPullPushCard) {
                    ((ContainerPullPushCard) container).setInterval(message.interval);
                }
            });

            return null;
        }
    }
}
