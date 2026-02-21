package com.cells.network.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.importinterface.ContainerImportInterface;
import com.cells.blocks.importinterface.ContainerMaxSlotSize;
import com.cells.blocks.fluidimportinterface.ContainerFluidImportInterface;


/**
 * Packet to set the max slot size for Import Interface (item or fluid).
 */
public class PacketSetMaxSlotSize implements IMessage {

    private int maxSlotSize;

    public PacketSetMaxSlotSize() {
    }

    public PacketSetMaxSlotSize(int maxSlotSize) {
        this.maxSlotSize = maxSlotSize;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.maxSlotSize = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.maxSlotSize);
    }

    public static class Handler implements IMessageHandler<PacketSetMaxSlotSize, IMessage> {
        @Override
        public IMessage onMessage(PacketSetMaxSlotSize message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerImportInterface) {
                    ((ContainerImportInterface) container).setMaxSlotSize(message.maxSlotSize);
                } else if (container instanceof ContainerFluidImportInterface) {
                    ((ContainerFluidImportInterface) container).setMaxSlotSize(message.maxSlotSize);
                } else if (container instanceof ContainerMaxSlotSize) {
                    ((ContainerMaxSlotSize) container).setMaxSlotSize(message.maxSlotSize);
                }
            });

            return null;
        }
    }
}
