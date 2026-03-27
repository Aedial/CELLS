package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.network.MemoryCardSaveTracker;


/**
 * Packet to save memory card settings including filters.
 * Sent from client when the player right-clicks with a memory card
 * while holding the "include filters" keybind.
 */
public class PacketSaveMemoryCardWithFilters implements IMessage {

    private BlockPos pos;
    private EnumFacing side;  // For parts, null for blocks
    private boolean isPart;

    public PacketSaveMemoryCardWithFilters() {
    }

    public PacketSaveMemoryCardWithFilters(BlockPos pos, EnumFacing side) {
        this.pos = pos;
        this.side = side;
        this.isPart = (side != null);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        this.isPart = buf.readBoolean();
        if (this.isPart) this.side = EnumFacing.byIndex(buf.readByte());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.pos.getX());
        buf.writeInt(this.pos.getY());
        buf.writeInt(this.pos.getZ());
        buf.writeBoolean(this.isPart);
        if (this.isPart) buf.writeByte(this.side.getIndex());
    }

    public static class Handler implements IMessageHandler<PacketSaveMemoryCardWithFilters, IMessage> {
        @Override
        public IMessage onMessage(PacketSaveMemoryCardWithFilters message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                // Mark this player as having a pending "save with filters" action.
                // This will cause MemoryCardServerHandler to cancel the normal memory card handling.
                MemoryCardSaveTracker.markPendingSave(player, message.pos);

                // Find which hand has the memory card
                ItemStack heldItem = player.getHeldItemMainhand();
                if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) {
                    heldItem = player.getHeldItemOffhand();
                }
                if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) return;

                IMemoryCard memoryCard = (IMemoryCard) heldItem.getItem();
                TileEntity te = player.world.getTileEntity(message.pos);
                if (te == null) return;

                NBTTagCompound data = null;
                String name = null;

                // Resolve the IInterfaceHost from either a part or tile entity
                IInterfaceHost interfaceHost = null;

                if (message.isPart) {
                    if (!(te instanceof IPartHost)) return;
                    IPartHost host = (IPartHost) te;
                    IPart part = host.getPart(AEPartLocation.fromFacing(message.side));
                    if (part instanceof IInterfaceHost) interfaceHost = (IInterfaceHost) part;
                } else {
                    if (te instanceof IInterfaceHost) interfaceHost = (IInterfaceHost) te;
                }

                if (interfaceHost != null) {
                    // Use downloadSettingsWithFilter() to include filters but NOT upgrades
                    // (upgrades stay in the source interface, we're just copying settings)
                    data = interfaceHost.downloadSettingsWithFilter();

                    // Determine the memory card name based on host type
                    // For blocks: use block's getTranslationKey()
                    // For parts: Item parts use no suffix (tile.cells.export_interface)
                    //            Fluid/Gas parts use type suffix (tile.cells.export_interface.fluid)
                    if (message.isPart) {
                        String typeName = interfaceHost.getTypeName();
                        String ioType = interfaceHost.isExport() ? "export" : "import";
                        // Item parts don't use type suffix to match Item blocks
                        if ("item".equals(typeName)) {
                            name = String.format("tile.cells.%s_interface", ioType);
                        } else {
                            name = String.format("tile.cells.%s_interface.%s", ioType, typeName);
                        }
                    } else {
                        // For blocks, get the translation key from the actual block
                        name = player.world.getBlockState(message.pos).getBlock().getTranslationKey();
                    }
                }

                if (data != null && !data.isEmpty()) {
                    memoryCard.setMemoryCardContents(heldItem, name, data);
                    player.sendMessage(new TextComponentTranslation("message.cells.memory_card.filters_saved"));
                }
            });

            return null;
        }
    }
}
