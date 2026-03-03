package com.cells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;

import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.parts.PartFluidImportInterface;
import com.cells.parts.PartImportInterface;


/**
 * Client-side event handler for memory card interaction with Import Interfaces.
 * When the player sneak+right-clicks with a memory card while holding the
 * "include filters" keybind, cancels the normal AE2 save and sends a packet
 * to save settings WITH filters instead.
 */
@SideOnly(Side.CLIENT)
public class MemoryCardInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote) return;

        // FIXME: fix this shit
        /*
        EntityPlayer player = event.getEntityPlayer();

        // Must be sneaking (AE2's "save to card" action)
        if (!player.isSneaking()) return;

        // Check if holding memory card
        ItemStack heldItem = player.getHeldItem(event.getHand());
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) return;

        // Check if our keybind is pressed
        if (!KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.isKeyDown()) return;

        BlockPos pos = event.getPos();
        TileEntity te = event.getWorld().getTileEntity(pos);
        if (te == null) return;

        EnumFacing partSide = null;
        boolean isImportInterface = false;

        // Check if it's a direct Import Interface tile
        if (te instanceof TileImportInterface || te instanceof TileFluidImportInterface) {
            isImportInterface = true;
        }
        // Check if it's a part host containing an Import Interface part
        else if (te instanceof IPartHost) {
            IPartHost host = (IPartHost) te;

            // Use raytrace to find which part is being targeted
            Minecraft mc = Minecraft.getMinecraft();
            RayTraceResult rayTrace = mc.objectMouseOver;
            if (rayTrace != null && rayTrace.typeOfHit == RayTraceResult.Type.BLOCK && rayTrace.getBlockPos().equals(pos)) {
                SelectedPart selectedPart = host.selectPart(rayTrace.hitVec.subtract(pos.getX(), pos.getY(), pos.getZ()));
                if (selectedPart.part != null) {
                    IPart part = selectedPart.part;
                    if (part instanceof PartImportInterface || part instanceof PartFluidImportInterface) {
                        isImportInterface = true;
                        partSide = selectedPart.side.getFacing();
                    }
                }
            }
        }

        if (!isImportInterface) return;

        // Prevent both the item (memory card) and block from handling this interaction.
        // This stops AE2's memory card from clearing/saving while we handle it ourselves.
        event.setUseItem(Event.Result.DENY);
        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);

        // FIXME: both the normal shift right-click of the card AND card clearing run. It's completely broken.

        // Send our packet to save with filters
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSaveMemoryCardWithFilters(pos, partSide));
        */
    }
}
