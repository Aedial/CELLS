package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.items.pullpush.ContainerPullPushCard;


/**
 * Packet to set the transfer quantity for Pull/Push Cards.
 */
public class PacketSetPullPushQuantity implements IMessage {

    private int quantity;

    public PacketSetPullPushQuantity() {
    }

    public PacketSetPullPushQuantity(int quantity) {
        this.quantity = quantity;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.quantity = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.quantity);
    }

    public static class Handler implements IMessageHandler<PacketSetPullPushQuantity, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPullPushQuantity message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerPullPushCard) {
                    ((ContainerPullPushCard) container).setQuantity(message.quantity);
                }
            });

            return null;
        }
    }
}
