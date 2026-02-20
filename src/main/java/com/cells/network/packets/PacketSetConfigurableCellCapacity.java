package com.cells.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.cells.configurable.ContainerConfigurableCell;


/**
 * Packet to set the per-type capacity limit for the Configurable Storage Cell.
 */
public class PacketSetConfigurableCellCapacity implements IMessage {

    private long maxPerType;

    public PacketSetConfigurableCellCapacity() {
    }

    public PacketSetConfigurableCellCapacity(long maxPerType) {
        this.maxPerType = maxPerType;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.maxPerType = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.maxPerType);
    }

    public static class Handler implements IMessageHandler<PacketSetConfigurableCellCapacity, IMessage> {
        @Override
        public IMessage onMessage(PacketSetConfigurableCellCapacity message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerConfigurableCell) {
                    ((ContainerConfigurableCell) container).setMaxPerType(message.maxPerType);
                }
            });

            return null;
        }
    }
}
