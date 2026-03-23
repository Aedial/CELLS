package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.essentia.ContainerCreativeEssentiaCell;


/**
 * Packet to set an essentia filter at a specific slot in a Creative Essentia Cell.
 * Sent from client when an essentia is dropped into a filter slot via JEI.
 */
public class PacketSetCreativeEssentiaFilter implements IMessage {

    private int slot;
    private EssentiaStack essentiaStack;

    public PacketSetCreativeEssentiaFilter() {
    }

    public PacketSetCreativeEssentiaFilter(int slot, EssentiaStack essentiaStack) {
        this.slot = slot;
        this.essentiaStack = essentiaStack != null ? essentiaStack.copy() : null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.slot = buf.readInt();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.essentiaStack = readEssentiaStack(tag);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.slot);
        NBTTagCompound tag = new NBTTagCompound();
        if (this.essentiaStack != null) this.essentiaStack.write(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    private static EssentiaStack readEssentiaStack(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("Aspect")) return null;

        return new EssentiaStack(tag.getString("Aspect"), tag.getInteger("Amount"));
    }

    public static class Handler implements IMessageHandler<PacketSetCreativeEssentiaFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketSetCreativeEssentiaFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerCreativeEssentiaCell)) return;

                ContainerCreativeEssentiaCell essentiaContainer = (ContainerCreativeEssentiaCell) container;

                // Validate slot is in range
                if (message.slot < 0 || message.slot >= 63) return;

                essentiaContainer.setEssentiaFilter(message.slot, message.essentiaStack);
            });

            return null;
        }
    }
}
