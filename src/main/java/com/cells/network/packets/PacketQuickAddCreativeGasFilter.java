package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import mekanism.api.gas.GasStack;

import com.cells.cells.creative.gas.ContainerCreativeGasCell;


/**
 * Packet to quick-add a gas to the first available filter slot in a Creative Gas Cell.
 * Sent from client when the quick-add keybind is pressed.
 */
public class PacketQuickAddCreativeGasFilter implements IMessage {

    private GasStack gasStack;

    public PacketQuickAddCreativeGasFilter() {
    }

    public PacketQuickAddCreativeGasFilter(GasStack gasStack) {
        this.gasStack = gasStack != null ? gasStack.copy() : null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.gasStack = tag != null ? GasStack.readFromNBT(tag) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.gasStack != null) this.gasStack.write(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketQuickAddCreativeGasFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddCreativeGasFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerCreativeGasCell)) return;

                ContainerCreativeGasCell gasContainer = (ContainerCreativeGasCell) container;

                if (message.gasStack == null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.not_valid_content",
                        new TextComponentTranslation("cells.type.gas")));
                    return;
                }

                if (gasContainer.getFilterHandler().isInFilter(message.gasStack)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return;
                }

                if (!gasContainer.addToFilter(message.gasStack)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
                }
            });

            return null;
        }
    }
}
