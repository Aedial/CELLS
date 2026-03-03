package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.cells.configurable.ContainerConfigurableCell;


/**
 * Packet to set the user-configured max types limit for the Configurable Storage Cell.
 */
public class PacketSetConfigurableCellMaxTypes implements IMessage {

    private int maxTypes;

    public PacketSetConfigurableCellMaxTypes() {
    }

    public PacketSetConfigurableCellMaxTypes(int maxTypes) {
        this.maxTypes = maxTypes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.maxTypes = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.maxTypes);
    }

    public static class Handler implements IMessageHandler<PacketSetConfigurableCellMaxTypes, IMessage> {

        @Override
        public IMessage onMessage(PacketSetConfigurableCellMaxTypes message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerConfigurableCell) {
                    String err = ((ContainerConfigurableCell) container).setUserMaxTypes(message.maxTypes);
                    // Send error message back to player
                    if (err != null) player.sendMessage(new TextComponentTranslation(err));
                }
            });

            return null;
        }
    }
}
