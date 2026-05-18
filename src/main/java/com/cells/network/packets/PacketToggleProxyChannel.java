package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.gui.subnetproxy.ContainerSubnetProxy;
import com.cells.network.sync.ResourceType;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


/**
 * Client &rarr; server packet that toggles the exposure of a single channel
 * on the Subnet Proxy. The server flips the bit on the part, which then
 * propagates to the front-grid (cell array changed) and to all listeners
 * (synced via {@link ContainerSubnetProxy#enabledChannels}).
 * <p>
 * Sent by {@code GuiChannelToggleButton} when the player clicks one of the
 * side channel buttons under the clear button.
 */
public class PacketToggleProxyChannel implements IMessage {

    private int typeOrdinal;

    public PacketToggleProxyChannel() {
    }

    public PacketToggleProxyChannel(ResourceType type) {
        this.typeOrdinal = type.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.typeOrdinal = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.typeOrdinal);
    }

    public static class Handler implements IMessageHandler<PacketToggleProxyChannel, IMessage> {

        @Override
        public IMessage onMessage(PacketToggleProxyChannel message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            ResourceType type = ResourceType.fromOrdinal(message.typeOrdinal);

            // Reject toggling channels whose backing mod isn't loaded: the GUI
            // never renders a button for them, but the client could still send
            // a packet by mistake. Ignore it rather than risking a crash.
            if (!type.isAvailable()) return null;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;
                if (!(container instanceof ContainerSubnetProxy)) return;

                PartSubnetProxyFront part = ((ContainerSubnetProxy) container).getPart();
                part.setChannelEnabled(type, !part.isChannelEnabled(type));
            });

            return null;
        }
    }
}
