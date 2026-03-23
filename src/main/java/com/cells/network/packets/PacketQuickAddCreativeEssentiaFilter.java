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

import thaumicenergistics.api.EssentiaStack;

import com.cells.cells.creative.essentia.ContainerCreativeEssentiaCell;
import com.cells.network.CellsNetworkHandler;


/**
 * Packet to quick-add an essentia to the first available filter slot in a Creative Essentia Cell.
 * Sent from client when the quick-add keybind is pressed.
 */
public class PacketQuickAddCreativeEssentiaFilter implements IMessage {

    private EssentiaStack essentiaStack;

    public PacketQuickAddCreativeEssentiaFilter() {
    }

    public PacketQuickAddCreativeEssentiaFilter(EssentiaStack essentiaStack) {
        this.essentiaStack = essentiaStack != null ? essentiaStack.copy() : null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.essentiaStack = readEssentiaStack(tag);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.essentiaStack != null) this.essentiaStack.write(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    private static EssentiaStack readEssentiaStack(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("Aspect")) return null;

        return new EssentiaStack(tag.getString("Aspect"), tag.getInteger("Amount"));
    }

    public static class Handler implements IMessageHandler<PacketQuickAddCreativeEssentiaFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddCreativeEssentiaFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerCreativeEssentiaCell)) return;

                ContainerCreativeEssentiaCell essentiaContainer = (ContainerCreativeEssentiaCell) container;

                if (message.essentiaStack == null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.not_valid_content",
                        new TextComponentTranslation("cells.type.essentia")));
                    return;
                }

                if (essentiaContainer.getFilterHandler().isInFilter(message.essentiaStack)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return;
                }

                // Find the first available slot and add the filter
                int addedSlot = essentiaContainer.addToFilterAndGetSlot(message.essentiaStack);
                if (addedSlot < 0) {
                    player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
                    return;
                }

                // Sync back to client: send the slot and essentia that was added
                CellsNetworkHandler.INSTANCE.sendTo(
                    new PacketSyncCreativeEssentiaFilter(addedSlot, message.essentiaStack),
                    player);
            });

            return null;
        }
    }
}
