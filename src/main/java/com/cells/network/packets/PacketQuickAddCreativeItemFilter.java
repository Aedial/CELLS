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
import com.cells.cells.creative.item.CreativeCellFilterHandler;
import com.cells.util.ItemStackKey;


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

                ContainerCreativeCell itemContainer = (ContainerCreativeCell) container;
                CreativeCellFilterHandler filterHandler = itemContainer.getFilterHandler();

                ItemStack toAdd = message.itemStack.copy();
                toAdd.setCount(1);

                // Check if this filter already exists
                ItemStackKey newKey = ItemStackKey.of(toAdd);
                for (int i = 0; i < CreativeCellFilterHandler.SLOT_COUNT; i++) {
                    ItemStack existing = filterHandler.getStackInSlot(i);
                    if (!existing.isEmpty() && ItemStackKey.of(existing).equals(newKey)) {
                        player.sendMessage(new TextComponentTranslation("message.cells.creative_cell.filter_duplicate"));
                        return;
                    }
                }

                // Find first empty filter slot
                for (int i = 0; i < CreativeCellFilterHandler.SLOT_COUNT; i++) {
                    if (filterHandler.getStackInSlot(i).isEmpty()) {
                        filterHandler.setStackInSlot(i, toAdd);
                        return;
                    }
                }

                // No space available
                player.sendMessage(new TextComponentTranslation("message.cells.creative_cell.no_filter_space"));
            });

            return null;
        }
    }
}
