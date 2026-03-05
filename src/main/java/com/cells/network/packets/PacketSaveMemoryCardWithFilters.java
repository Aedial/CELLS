package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.util.SettingsFrom;

import com.cells.blocks.exportinterface.IExportInterfaceInventoryHost;
import com.cells.blocks.exportinterface.TileExportInterface;
import com.cells.blocks.fluidexportinterface.IFluidExportInterfaceInventoryHost;
import com.cells.blocks.fluidimportinterface.IFluidImportInterfaceInventoryHost;
import com.cells.blocks.fluidexportinterface.TileFluidExportInterface;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.blocks.importinterface.IImportInterfaceInventoryHost;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.network.MemoryCardSaveTracker;
import com.cells.parts.PartExportInterface;
import com.cells.parts.PartFluidExportInterface;
import com.cells.parts.PartFluidImportInterface;
import com.cells.parts.PartImportInterface;


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
                EnumHand hand = null;
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

                if (message.isPart) {
                    // Handle part
                    if (!(te instanceof IPartHost)) return;
                    IPartHost host = (IPartHost) te;
                    IPart part = host.getPart(AEPartLocation.fromFacing(message.side));

                    if (part instanceof PartImportInterface) {
                        IImportInterfaceInventoryHost importPart = (PartImportInterface) part;
                        data = importPart.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.import_interface";
                    } else if (part instanceof PartFluidImportInterface) {
                        IFluidImportInterfaceInventoryHost fluidPart = (PartFluidImportInterface) part;
                        data = fluidPart.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.import_fluid_interface";
                    } else if (part instanceof PartExportInterface) {
                        IExportInterfaceInventoryHost exportPart = (PartExportInterface) part;
                        data = exportPart.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.export_interface";
                    } else if (part instanceof PartFluidExportInterface) {
                        IFluidExportInterfaceInventoryHost fluidExportPart = (PartFluidExportInterface) part;
                        data = fluidExportPart.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.export_fluid_interface";
                    }
                } else {
                    // Handle tile entity
                    if (te instanceof TileImportInterface) {
                        IImportInterfaceInventoryHost tile = (TileImportInterface) te;
                        data = tile.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.import_interface";
                    } else if (te instanceof TileFluidImportInterface) {
                        IFluidImportInterfaceInventoryHost tile = (TileFluidImportInterface) te;
                        data = tile.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.import_fluid_interface";
                    } else if (te instanceof TileExportInterface) {
                        IExportInterfaceInventoryHost tile = (TileExportInterface) te;
                        data = tile.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.export_interface";
                    } else if (te instanceof TileFluidExportInterface) {
                        IFluidExportInterfaceInventoryHost tile = (TileFluidExportInterface) te;
                        data = tile.downloadSettings(SettingsFrom.DISMANTLE_ITEM);
                        name = "tile.cells.export_fluid_interface";
                    }
                }

                if (data != null && name != null && !data.isEmpty()) {
                    memoryCard.setMemoryCardContents(heldItem, name, data);
                    player.sendMessage(new TextComponentTranslation("message.cells.memory_card.filters_saved"));
                }
            });

            return null;
        }
    }
}
