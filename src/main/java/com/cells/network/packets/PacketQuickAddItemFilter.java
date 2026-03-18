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

import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.blocks.interfacebase.ContainerItemInterface;
import com.cells.blocks.interfacebase.IItemInterfaceHost;
import com.cells.blocks.interfacebase.ItemInterfaceLogic;
import com.cells.util.ItemStackKey;


/**
 * Packet to quick-add an item to the first available filter slot.
 * Sent from client when the quick-add keybind is pressed.
 */
public class PacketQuickAddItemFilter implements IMessage {

    private ItemStack itemStack;

    public PacketQuickAddItemFilter() {
    }

    public PacketQuickAddItemFilter(ItemStack itemStack) {
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

    public static class Handler implements IMessageHandler<PacketQuickAddItemFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddItemFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerItemInterface)) return;

                ContainerItemInterface importContainer = (ContainerItemInterface) container;
                IItemInterfaceHost host = importContainer.getHost();
                AppEngInternalInventory filterInventory = host.getFilterInventory();
                AppEngInternalInventory storageInventory = host.getStorageInventory();

                ItemStack toAdd = message.itemStack.copy();
                toAdd.setCount(1);

                // Only check slots within effective capacity (based on installed capacity upgrades)
                int effectiveSlots = host.getTotalPages() * ItemInterfaceLogic.SLOTS_PER_PAGE;

                // Check if this filter already exists
                ItemStackKey newKey = ItemStackKey.of(toAdd);
                for (int i = 0; i < effectiveSlots; i++) {
                    ItemStack existing = filterInventory.getStackInSlot(i);
                    if (!existing.isEmpty() && ItemStackKey.of(existing).equals(newKey)) {
                        player.sendMessage(new TextComponentTranslation("message.cells.import_interface.filter_duplicate"));
                        return;
                    }
                }

                // Find first empty filter slot whose storage slot is also empty
                for (int i = 0; i < effectiveSlots; i++) {
                    ItemStack existingFilter = filterInventory.getStackInSlot(i);
                    ItemStack existingStorage = storageInventory.getStackInSlot(i);

                    // Slot is available if both filter and storage are empty
                    if (existingFilter.isEmpty() && existingStorage.isEmpty()) {
                        filterInventory.setStackInSlot(i, toAdd);
                        host.refreshFilterMap();
                        return;
                    }
                }

                // No space available
                player.sendMessage(new TextComponentTranslation("message.cells.import_interface.no_filter_space"));
            });

            return null;
        }
    }
}
