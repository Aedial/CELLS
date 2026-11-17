package com.cells.network.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.scanner.ContainerInterfaceScanner;
import com.cells.scanner.InterfaceScanEntry;


/**
 * Incremental transport for a static interface scan, scoped to the open window and scan revision.
 */
public class PacketInterfaceScannerSync implements IMessage {

    private int windowId;
    private int revision;
    private boolean reset;
    private boolean complete;
    private boolean failed;
    private boolean includeSubnets;
    private boolean canScan;
    private InterfaceScanEntry entry;

    public PacketInterfaceScannerSync() {}

    public PacketInterfaceScannerSync(int windowId, int revision, boolean reset, boolean complete,
                                      boolean failed, boolean includeSubnets, boolean canScan,
                                      InterfaceScanEntry entry) {
        this.windowId = windowId;
        this.revision = revision;
        this.reset = reset;
        this.complete = complete;
        this.failed = failed;
        this.includeSubnets = includeSubnets;
        this.canScan = canScan;
        this.entry = entry;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.windowId = buf.readInt();
        this.revision = buf.readInt();
        this.reset = buf.readBoolean();
        this.complete = buf.readBoolean();
        this.failed = buf.readBoolean();
        this.includeSubnets = buf.readBoolean();
        this.canScan = buf.readBoolean();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.entry = tag != null ? InterfaceScanEntry.readFromNBT(tag) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.windowId);
        buf.writeInt(this.revision);
        buf.writeBoolean(this.reset);
        buf.writeBoolean(this.complete);
        buf.writeBoolean(this.failed);
        buf.writeBoolean(this.includeSubnets);
        buf.writeBoolean(this.canScan);
        ByteBufUtils.writeTag(buf, this.entry != null ? this.entry.writeToNBT() : null);
    }

    public static class ClientHandler implements IMessageHandler<PacketInterfaceScannerSync, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketInterfaceScannerSync message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> handleClient(message));
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClient(PacketInterfaceScannerSync message) {
            EntityPlayer player = Minecraft.getMinecraft().player;
            if (player == null || !(player.openContainer instanceof ContainerInterfaceScanner)) return;
            if (player.openContainer.windowId != message.windowId) return;

            ((ContainerInterfaceScanner) player.openContainer).receive(message.revision, message.reset,
                message.complete, message.failed, message.includeSubnets, message.canScan, message.entry);
        }
    }
}
