package com.cells.network.packets;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.util.AEPartLocation;

import com.cells.Cells;
import com.cells.gui.CellsGuiHandler;


/**
 * Packet to request opening a GUI at a specific position.
 * <p>
 * For parts, the GUI ID should be pre-encoded with side information using
 * {@link CellsGuiHandler#encodePartGuiId(int, AEPartLocation)}.
 */
public class PacketOpenGui implements IMessage {

    private int x, y, z;
    private int guiId;

    public PacketOpenGui() {
    }

    /**
     * Create a packet to open a block-based GUI.
     */
    public PacketOpenGui(int x, int y, int z, int guiId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.guiId = guiId;
    }

    /**
     * Create a packet to open a GUI, encoding part side if applicable.
     *
     * @param pos The block position
     * @param baseGuiId The base GUI ID (use part GUI IDs like GUI_PART_MAX_SLOT_SIZE for parts)
     * @param partSide The part side (null for blocks)
     */
    public PacketOpenGui(BlockPos pos, int baseGuiId, @Nullable AEPartLocation partSide) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();

        if (partSide != null) {
            this.guiId = CellsGuiHandler.encodePartGuiId(baseGuiId, partSide);
        } else {
            this.guiId = baseGuiId;
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.guiId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.guiId);
    }

    public static class Handler implements IMessageHandler<PacketOpenGui, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenGui message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                BlockPos pos = new BlockPos(message.x, message.y, message.z);

                // Verify player is close enough to the block
                if (player.getDistanceSq(pos) < 64) {
                    player.openGui(Cells.instance, message.guiId, player.world, message.x, message.y, message.z);
                }
            });

            return null;
        }
    }
}
