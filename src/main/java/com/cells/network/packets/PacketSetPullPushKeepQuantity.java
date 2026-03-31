package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.items.pullpush.ContainerPullPushCard;


/**
 * Packet to set the keep quantity for Pull/Push Cards.
 */
public class PacketSetPullPushKeepQuantity implements IMessage {

    private int keepQuantity;

    public PacketSetPullPushKeepQuantity() {
    }

    public PacketSetPullPushKeepQuantity(int keepQuantity) {
        this.keepQuantity = keepQuantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.keepQuantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.keepQuantity);
    }

    public static class Handler implements IMessageHandler<PacketSetPullPushKeepQuantity, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPullPushKeepQuantity message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerPullPushCard) {
                    ((ContainerPullPushCard) container).setKeepQuantity(message.keepQuantity);
                }
            });

            return null;
        }
    }
}
