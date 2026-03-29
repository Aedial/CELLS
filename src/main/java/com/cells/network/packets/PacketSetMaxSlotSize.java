package com.cells.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.interfacebase.item.ContainerItemInterface;
import com.cells.blocks.interfacebase.fluid.ContainerFluidInterface;
import com.cells.blocks.interfacebase.ContainerMaxSlotSize;


/**
 * Packet to set the max slot size for Import Interface (item or fluid).
 */
public class PacketSetMaxSlotSize implements IMessage {

    private long maxSlotSize;

    public PacketSetMaxSlotSize() {
    }

    public PacketSetMaxSlotSize(long maxSlotSize) {
        this.maxSlotSize = maxSlotSize;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.maxSlotSize = buf.readLong();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(this.maxSlotSize);
    }

    public static class Handler implements IMessageHandler<PacketSetMaxSlotSize, IMessage> {
        @Override
        public IMessage onMessage(PacketSetMaxSlotSize message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerItemInterface) {
                    ((ContainerItemInterface) container).setMaxSlotSize(message.maxSlotSize);
                } else if (container instanceof ContainerFluidInterface) {
                    ((ContainerFluidInterface) container).setMaxSlotSize(message.maxSlotSize);
                } else if (container instanceof ContainerMaxSlotSize) {
                    ((ContainerMaxSlotSize) container).setMaxSlotSize(message.maxSlotSize);
                }
            });

            return null;
        }
    }
}
