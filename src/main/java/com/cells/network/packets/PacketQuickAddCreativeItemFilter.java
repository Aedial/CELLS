package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.cells.creative.item.ContainerCreativeCell;


/**
 * Packet to quick-add an item to the first available filter slot in a Creative Item Cell.
 * Sent from client when the quick-add keybind is pressed.
 */
public class PacketQuickAddCreativeItemFilter implements IMessage {

    private ItemStack itemStack;

    public PacketQuickAddCreativeItemFilter() {
    }

    public PacketQuickAddCreativeItemFilter(ItemStack itemStack) {
        this.itemStack = itemStack.copy();
        this.itemStack.setCount(1);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.itemStack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, this.itemStack);
    }

    public static class Handler implements IMessageHandler<PacketQuickAddCreativeItemFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddCreativeItemFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerCreativeCell)) return;

                ItemStack toAdd = message.itemStack.copy();
                toAdd.setCount(1);

                ContainerCreativeCell itemContainer = (ContainerCreativeCell) container;
                if (itemContainer.getFilterHandler().isInFilter(toAdd)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return;
                }

                if (!itemContainer.addToFilter(toAdd)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
                }
            });

            return null;
        }
    }
}
