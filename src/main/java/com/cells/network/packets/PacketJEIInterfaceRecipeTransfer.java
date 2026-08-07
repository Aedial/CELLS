package com.cells.network.packets;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.blocks.combinedinterface.ContainerCombinedInterface;
import com.cells.blocks.iointerface.ContainerIOInterface;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.network.sync.IQuickAddFilterContainer;
import com.cells.network.sync.ResourceType;


/**
 * JEI recipe transfer packet for CELLS interfaces.
 * <p>
 * Carries already-converted CELLS/AE filter resources so the server can route
 * them into single, combined, or IO interface containers without depending on
 * JEI classes server-side.
 */
public class PacketJEIInterfaceRecipeTransfer implements IMessage {

    public static final int DIRECTION_UNSPECIFIED = -1;

    public static final class TransferEntry {

        private final ResourceType type;
        private final int directionTab;
        private final Object resource;

        public TransferEntry(ResourceType type, int directionTab, Object resource) {
            this.type = type;
            this.directionTab = directionTab;
            this.resource = resource;
        }

        public ResourceType getType() {
            return this.type;
        }

        public int getDirectionTab() {
            return this.directionTab;
        }

        public Object getResource() {
            return this.resource;
        }
    }

    private List<TransferEntry> entries = new ArrayList<>();

    public PacketJEIInterfaceRecipeTransfer() {
    }

    // TODO: The packet is currently not protected against sending an "infinite" list.
    //       An easy fix would be to limit each <ResourceType, directionTab> pair
    //       to a maximum of 180 entries (max size of the interface).
    //       A better fix would be to ask the interface container how much space it has
    //       for each resource type and direction tab, and only send that many entries,
    //       but that would require another round-trip to the server...
    public PacketJEIInterfaceRecipeTransfer(@Nonnull List<TransferEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        this.entries = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ResourceType type = ResourceType.values()[buf.readByte()];
            int directionTab = buf.readByte();
            Object resource = type.read(buf);
            this.entries.add(new TransferEntry(type, directionTab, resource));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.entries.size());

        for (TransferEntry entry : this.entries) {
            buf.writeByte(entry.getType().ordinal());
            buf.writeByte(entry.getDirectionTab());
            entry.getType().write(buf, entry.getResource());
        }
    }

    public static class Handler implements IMessageHandler<PacketJEIInterfaceRecipeTransfer, IMessage> {

        @Override
        public IMessage onMessage(PacketJEIInterfaceRecipeTransfer message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> handle(player, message.entries));
            return null;
        }

        private static void handle(EntityPlayerMP player, List<TransferEntry> entries) {
            Container container = player.openContainer;

            if (container instanceof ContainerCombinedInterface) {
                Map<ResourceType, List<Object>> resourcesByType = new EnumMap<>(ResourceType.class);

                for (TransferEntry entry : entries) {
                    Object resource = entry.getResource();
                    if (resource == null) continue;

                    resourcesByType
                        .computeIfAbsent(entry.getType(), key -> new ArrayList<>())
                        .add(resource);
                }

                ((ContainerCombinedInterface) container).addRecipeTransferSilently(resourcesByType);
                return;
            }

            if (container instanceof ContainerIOInterface) {
                ContainerIOInterface ioContainer = (ContainerIOInterface) container;
                ResourceType containerType = ioContainer.getHost().getResourceType();
                Map<Integer, List<Object>> resourcesByTab = new HashMap<>();

                for (TransferEntry entry : entries) {
                    Object resource = entry.getResource();
                    int directionTab = entry.getDirectionTab();

                    if (resource == null) continue;
                    if (entry.getType() != containerType) continue;
                    if (directionTab != IIOInterfaceHost.TAB_IMPORT
                        && directionTab != IIOInterfaceHost.TAB_EXPORT) {
                        continue;
                    }

                    resourcesByTab
                        .computeIfAbsent(directionTab, key -> new ArrayList<>())
                        .add(resource);
                }

                ioContainer.addRecipeTransferSilently(resourcesByTab);
                return;
            }

            if (!(container instanceof IQuickAddFilterContainer)) return;

            IQuickAddFilterContainer quickAddContainer = (IQuickAddFilterContainer) container;

            for (TransferEntry entry : entries) {
                Object resource = entry.getResource();
                if (resource == null) continue;
                if (quickAddContainer.getQuickAddResourceType() != entry.getType()) continue;

                quickAddContainer.quickAddToFilter(resource, null);
            }
        }
    }
}