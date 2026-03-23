package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.essentia.ContainerCreativeEssentiaCell;


/**
 * Packet to sync an essentia filter slot from server to client.
 * Sent after quick-add to update the client's view.
 */
public class PacketSyncCreativeEssentiaFilter implements IMessage {

    private int slot;
    private EssentiaStack essentiaStack;

    public PacketSyncCreativeEssentiaFilter() {
    }

    public PacketSyncCreativeEssentiaFilter(int slot, EssentiaStack essentiaStack) {
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

    public static class Handler implements IMessageHandler<PacketSyncCreativeEssentiaFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketSyncCreativeEssentiaFilter message, MessageContext ctx) {
            // This packet is received on the client side
            handleOnClient(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void handleOnClient(PacketSyncCreativeEssentiaFilter message) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player == null) return;

                Container container = mc.player.openContainer;

                if (!(container instanceof ContainerCreativeEssentiaCell)) return;

                ContainerCreativeEssentiaCell essentiaContainer = (ContainerCreativeEssentiaCell) container;

                // Update the filter in the client's view
                essentiaContainer.setEssentiaFilter(message.slot, message.essentiaStack);
            });
        }
    }
}
