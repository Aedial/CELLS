package com.cells.network.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.util.text.TextComponentTranslation;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;


/**
 * Unified packet for quick-adding any resource type to a filter container.
 * <p>
 * This single packet handles items/fluids/gases/essentia from both
 * creative cells and interfaces, using the ResourceType enum to determine
 * the specific type of resource being added.
 * <p>
 * Serialization is handled by {@link ResourceType}.
 */
public class PacketQuickAddFilter implements IMessage {

    private ResourceType type;
    private Object resource;

    /**
     * Default constructor for deserialization.
     */
    public PacketQuickAddFilter() {
    }

    /**
     * Create a quick-add packet for a resource.
     *
     * @param type     The resource type
     * @param resource The resource to add (ItemStack, IAEFluidStack, IAEGasStack, EssentiaStack)
     */
    public PacketQuickAddFilter(ResourceType type, Object resource) {
        this.type = type;
        this.resource = resource;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = ResourceType.values()[buf.readByte()];
        // Resource serialization uses the "present" flag internally
        this.resource = readResourceWithPresenceFlag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type.ordinal());
        writeResourceWithPresenceFlag(buf, this.resource);
    }

    private Object readResourceWithPresenceFlag(ByteBuf buf) {
        // type.read() already handles the presence flag internally
        return this.type.read(buf);
    }

    private void writeResourceWithPresenceFlag(ByteBuf buf, Object resource) {
        // type.write() already handles the presence flag internally
        this.type.write(buf, resource);
    }

    /**
     * Server-side handler for quick-add operations.
     */
    public static class Handler implements IMessageHandler<PacketQuickAddFilter, IMessage> {

        @Override
        public IMessage onMessage(PacketQuickAddFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                // Container must implement IQuickAddFilterContainer
                if (!(container instanceof IQuickAddFilterContainer)) return;

                IQuickAddFilterContainer quickAddContainer = (IQuickAddFilterContainer) container;

                // Verify resource type matches
                if (quickAddContainer.getQuickAddResourceType() != message.type) {
                    player.sendMessage(new TextComponentTranslation(
                        "message.cells.not_valid_content",
                        new TextComponentTranslation(quickAddContainer.getTypeLocalizationKey())
                    ));
                    return;
                }

                // Null/empty resource check
                if (message.resource == null) {
                    player.sendMessage(new TextComponentTranslation(
                        "message.cells.not_valid_content",
                        new TextComponentTranslation(quickAddContainer.getTypeLocalizationKey())
                    ));
                    return;
                }

                // Duplicate check
                if (quickAddContainer.isResourceInFilter(message.resource)) {
                    player.sendMessage(new TextComponentTranslation(
                        "message.cells.filter_duplicate"));
                    return;
                }

                // Attempt to add
                if (!quickAddContainer.quickAddToFilter(message.resource, player)) {
                    player.sendMessage(new TextComponentTranslation(
                        "message.cells.no_filter_space"));
                }
            });

            return null;
        }
    }
}
