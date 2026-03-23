package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.interfacebase.item.ContainerItemInterface;
import com.cells.blocks.interfacebase.fluid.ContainerFluidInterface;


/**
 * Packet to clear all filters in an Import/Export Interface.
 * For Import interfaces, only clears filters where the storage slot is empty.
 * For Export interfaces, clears all filters and sends orphaned items back to network.
 */
public class PacketClearFilters implements IMessage {

    public PacketClearFilters() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketClearFilters, IMessage> {

        @Override
        public IMessage onMessage(PacketClearFilters message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof ContainerItemInterface) {
                    ((ContainerItemInterface) container).clearFilters();
                } else if (container instanceof ContainerFluidInterface) {
                    ((ContainerFluidInterface) container).clearFilters();
                }
            });

            return null;
        }
    }
}
