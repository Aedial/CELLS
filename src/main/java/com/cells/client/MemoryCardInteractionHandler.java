package com.cells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.EnumActionResult;
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

import com.cells.blocks.exportinterface.TileExportInterface;
import com.cells.blocks.fluidexportinterface.TileFluidExportInterface;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.blocks.importinterface.TileImportInterface;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSaveMemoryCardWithFilters;
import com.cells.parts.PartExportInterface;
import com.cells.parts.PartFluidExportInterface;
import com.cells.parts.PartFluidImportInterface;
import com.cells.parts.PartImportInterface;


/**
 * Client-side event handler for memory card interaction with Import/Export Interfaces.
 * When the player sneak+right-clicks with a memory card while holding the
 * "include filters" keybind, sends a packet to save settings WITH filters.
 *
 * The corresponding server-side handler (MemoryCardServerHandler) intercepts the
 * RightClickBlock event and cancels it when our packet has been received, preventing
 * both the normal AE2 memory card save and the card clearing behavior.
 */
@SideOnly(Side.CLIENT)
public class MemoryCardInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getWorld().isRemote) return;

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
        boolean isImportExportInterface = false;

        // Check if it's a direct Import Interface tile
        if (te instanceof TileImportInterface || te instanceof TileFluidImportInterface
            || te instanceof TileExportInterface || te instanceof TileFluidExportInterface) {
            isImportExportInterface = true;
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
                    if (part instanceof PartImportInterface || part instanceof PartFluidImportInterface
                        || part instanceof PartExportInterface || part instanceof PartFluidExportInterface) {
                        isImportExportInterface = true;
                        partSide = selectedPart.side.getFacing();
                    }
                }
            }
        }

        if (!isImportExportInterface) return;

        // Prevent both the item (memory card) and block from handling this interaction.
        // This stops AE2's memory card from clearing/saving while we handle it ourselves.
        // Note: The server-side MemoryCardServerHandler also cancels the event there,
        // ensuring the cancellation is effective on both sides.
        event.setUseItem(Event.Result.DENY);
        event.setUseBlock(Event.Result.DENY);
        event.setCanceled(true);

        // Default cancellation result is PASS, which causes Minecraft's
        // rightClickMouse() to also call processRightClick() after processRightClickBlock().
        // That triggers ToolMemoryCard.onItemRightClick() which clears the card when sneaking.
        // By returning SUCCESS, we tell Minecraft the interaction was fully handled.
        event.setCancellationResult(EnumActionResult.SUCCESS);

        // Send our packet to save with filters
        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSaveMemoryCardWithFilters(pos, partSide));
    }
}
