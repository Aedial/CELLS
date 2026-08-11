package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.combinedinterface.ContainerCombinedInterface;
import com.cells.blocks.interfacebase.AbstractContainerInterface;
import com.cells.blocks.iointerface.ContainerIOInterface;


/**
 * Trigger one immediate interface polling cycle for the currently open interface GUI.
 */
public class PacketTriggerPollingAction implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
        // No data needed
    }

    @Override
    public void toBytes(ByteBuf buf) {
        // No data needed
    }

    public static class Handler implements IMessageHandler<PacketTriggerPollingAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTriggerPollingAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (container instanceof AbstractContainerInterface) {
                    ((AbstractContainerInterface<?, ?, ?>) container).triggerImmediatePollingAction();
                    container.detectAndSendChanges();
                    return;
                }

                if (container instanceof ContainerCombinedInterface) {
                    ((ContainerCombinedInterface) container).triggerImmediatePollingAction();
                    container.detectAndSendChanges();
                    return;
                }

                if (container instanceof ContainerIOInterface) {
                    ((ContainerIOInterface) container).triggerImmediatePollingAction();
                    container.detectAndSendChanges();
                }
            });

            return null;
        }
    }
}