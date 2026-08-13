package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.blocks.combinedinterface.ContainerCombinedInterface;
import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.blocks.iointerface.ContainerIOInterface;
import com.cells.gui.subnetproxy.ContainerSubnetProxy;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


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

                if (container instanceof AbstractContainerInterface) {
                    ((AbstractContainerInterface<?, ?, ?>) container).clearFilters();
                } else if (container instanceof ContainerCombinedInterface) {
                    ((ContainerCombinedInterface) container).clearFilters();
                } else if (container instanceof ContainerIOInterface) {
                    ((ContainerIOInterface) container).clearFilters();
                } else if (container instanceof ContainerSubnetProxy) {
                    // Clear all filter slots in the Subnet Proxy config inventory
                    PartSubnetProxyFront part = ((ContainerSubnetProxy) container).getPart();
                    AppEngInternalInventory config = part.getConfigInventory();
                    for (int i = 0; i < config.getSlots(); i++) {
                        config.setStackInSlot(i, ItemStack.EMPTY);
                    }
                } else {
                    return;
                }

                container.detectAndSendChanges();

            });

            return null;
        }
    }
}
