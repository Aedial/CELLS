package com.cells.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.importinterface.ContainerImportInterface;
import com.cells.blocks.importinterface.ContainerPollingRate;


/**
 * Packet to set the polling rate for Import Interface.
 */
public class PacketSetPollingRate implements IMessage {

    private int pollingRate;

    public PacketSetPollingRate() {
    }

    public PacketSetPollingRate(int pollingRate) {
        this.pollingRate = pollingRate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pollingRate = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pollingRate);
    }

    public static class Handler implements IMessageHandler<PacketSetPollingRate, IMessage> {
        @Override
        public IMessage onMessage(PacketSetPollingRate message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerImportInterface) {
                    ((ContainerImportInterface) container).setPollingRate(message.pollingRate);
                } else if (container instanceof ContainerPollingRate) {
                    ((ContainerPollingRate) container).setPollingRate(message.pollingRate);
                }
            });

            return null;
        }
    }
}
