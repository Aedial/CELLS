package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.scanner.ContainerInterfaceScanner;


/**
 * Subnet scope changes for the currently open scanner view.
 */
public class PacketInterfaceScannerSubnets implements IMessage {

    private int windowId;
    private boolean includeSubnets;

    public PacketInterfaceScannerSubnets() {}

    public PacketInterfaceScannerSubnets(int windowId, boolean includeSubnets) {
        this.windowId = windowId;
        this.includeSubnets = includeSubnets;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.windowId = buf.readInt();
        this.includeSubnets = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.windowId);
        buf.writeBoolean(this.includeSubnets);
    }

    public static class Handler implements IMessageHandler<PacketInterfaceScannerSubnets, IMessage> {

        @Override
        public IMessage onMessage(PacketInterfaceScannerSubnets message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerInterfaceScanner)) return;
                if (player.openContainer.windowId != message.windowId) return;

                ContainerInterfaceScanner container = (ContainerInterfaceScanner) player.openContainer;
                if (container.canInteractWith(player)) container.setIncludeSubnets(message.includeSubnets);
            });

            return null;
        }
    }
}
