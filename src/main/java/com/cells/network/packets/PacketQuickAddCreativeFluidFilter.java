package com.cells.network.packets;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.cells.creative.fluid.ContainerCreativeFluidCell;


/**
 * Packet to quick-add a fluid to the first available filter slot in a Creative Fluid Cell.
 * Sent from client when the quick-add keybind is pressed.
 *
 * TODO: Investigate NBT handling - reported that Quick Add doesn't preserve fluid NBT
 * while drag-and-drop (PacketFluidSlot) does. Both serialize FluidStack via writeToNBT/
 * loadFluidStackFromNBT which should preserve the tag field. The issue may be in:
 * - How FluidUtil.getFluidContained() returns the fluid in getFluidUnderCursor
 * - Comparison logic in isInFilter (FluidStackKey equality)
 * - Something specific to certain fluid types or container items
 */
public class PacketQuickAddCreativeFluidFilter implements IMessage {

    private FluidStack fluidStack;

    public PacketQuickAddCreativeFluidFilter() {
    }

    public PacketQuickAddCreativeFluidFilter(FluidStack fluidStack) {
        this.fluidStack = fluidStack != null ? fluidStack.copy() : null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.fluidStack = tag != null ? FluidStack.loadFluidStackFromNBT(tag) : null;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        if (this.fluidStack != null) this.fluidStack.writeToNBT(tag);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketQuickAddCreativeFluidFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddCreativeFluidFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerCreativeFluidCell)) return;

                ContainerCreativeFluidCell fluidContainer = (ContainerCreativeFluidCell) container;

                if (message.fluidStack == null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.not_valid_content",
                        new TextComponentTranslation("cells.type.fluid")));
                    return;
                }

                if (fluidContainer.getFilterHandler().isInFilter(message.fluidStack)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.filter_duplicate"));
                    return;
                }

                if (!fluidContainer.addToFilter(message.fluidStack)) {
                    player.sendMessage(new TextComponentTranslation("message.cells.no_filter_space"));
                }
            });

            return null;
        }
    }
}
