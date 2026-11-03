package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.items.upgrades.pullpush.ContainerPullPushCard;


public class PacketSetPullUnselected implements IMessage {

    private boolean enabled;

    public PacketSetPullUnselected() {}

    public PacketSetPullUnselected(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.enabled = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.enabled);
    }

    public static class Handler implements IMessageHandler<PacketSetPullUnselected, IMessage> {

        @Override
        public IMessage onMessage(PacketSetPullUnselected message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;
                if (container instanceof ContainerPullPushCard && container.canInteractWith(player)) {
                    ((ContainerPullPushCard) container).setPullUnselected(message.enabled);
                }
            });

            return null;
        }
    }
}
