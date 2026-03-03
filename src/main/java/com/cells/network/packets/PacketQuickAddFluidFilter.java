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

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.IAEFluidTank;

import com.cells.blocks.fluidimportinterface.ContainerFluidImportInterface;
import com.cells.blocks.fluidimportinterface.IFluidImportInterfaceInventoryHost;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;


/**
 * Packet to quick-add a fluid to the first available filter slot.
 * Sent from client when the quick-add keybind is pressed in the Fluid Import Interface.
 */
public class PacketQuickAddFluidFilter implements IMessage {

    private FluidStack fluidStack;

    public PacketQuickAddFluidFilter() {
    }

    public PacketQuickAddFluidFilter(FluidStack fluidStack) {
        this.fluidStack = fluidStack.copy();
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

    public static class Handler implements IMessageHandler<PacketQuickAddFluidFilter, IMessage> {
        @Override
        public IMessage onMessage(PacketQuickAddFluidFilter message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;

            player.getServerWorld().addScheduledTask(() -> {
                Container container = player.openContainer;

                if (!(container instanceof ContainerFluidImportInterface)) return;

                ContainerFluidImportInterface fluidContainer = (ContainerFluidImportInterface) container;
                IFluidImportInterfaceInventoryHost host = fluidContainer.getHost();
                IAEFluidTank filterInventory = host.getFilterInventory();

                if (message.fluidStack == null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_fluid_interface.not_fluid_container"));
                    return;
                }

                IAEFluidStack toAdd = AEFluidStack.fromFluidStack(message.fluidStack);
                if (toAdd == null) {
                    player.sendMessage(new TextComponentTranslation("message.cells.import_fluid_interface.not_fluid_container"));
                    return;
                }

                // Check if this fluid filter already exists
                for (int i = 0; i < TileFluidImportInterface.FILTER_SLOTS; i++) {
                    IAEFluidStack existing = filterInventory.getFluidInSlot(i);
                    if (existing != null && existing.getFluid() == toAdd.getFluid()) {
                        player.sendMessage(new TextComponentTranslation("message.cells.import_fluid_interface.filter_duplicate"));
                        return;
                    }
                }

                // Find first empty filter slot whose tank is also empty
                for (int i = 0; i < TileFluidImportInterface.FILTER_SLOTS; i++) {
                    IAEFluidStack existingFilter = filterInventory.getFluidInSlot(i);
                    boolean tankEmpty = host.isTankEmpty(i);

                    // Slot is available if both filter and tank are empty
                    if (existingFilter == null && tankEmpty) {
                        host.setFilterFluid(i, toAdd);
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
