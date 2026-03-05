package com.cells.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPartHost;

import com.cells.blocks.exportinterface.TileExportInterface;
import com.cells.blocks.fluidexportinterface.TileFluidExportInterface;
import com.cells.blocks.fluidimportinterface.TileFluidImportInterface;
import com.cells.blocks.importinterface.TileImportInterface;


/**
 * Server-side event handler that intercepts memory card interactions with our interfaces.
 * When a player has a pending "save with filters" action (from PacketSaveMemoryCardWithFilters),
 * this handler cancels the normal memory card and block interaction to prevent:
 * 1. Normal AE2 block handling (which saves without filters)
 * 2. Memory card item handling (which clears the card when sneaking)
 */
public class MemoryCardServerHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Only run on server
        if (event.getWorld().isRemote) return;

        EntityPlayer player = event.getEntityPlayer();

        // Must be sneaking (save action)
        if (!player.isSneaking()) return;

        // Check if holding memory card
        ItemStack heldItem = player.getHeldItem(event.getHand());
        if (heldItem.isEmpty() || !(heldItem.getItem() instanceof IMemoryCard)) return;

        BlockPos pos = event.getPos();

        // Check if this is one of our interfaces
        TileEntity te = event.getWorld().getTileEntity(pos);
        if (te == null) return;

        boolean isOurInterface = (te instanceof TileImportInterface)
            || (te instanceof TileFluidImportInterface)
            || (te instanceof TileExportInterface)
            || (te instanceof TileFluidExportInterface)
            || (te instanceof IPartHost);  // Parts are handled via IPartHost

        if (!isOurInterface) return;

        // Check if we have a pending "save with filters" action for this position
        if (MemoryCardSaveTracker.hasPendingSave(player, pos)) {
            // Cancel the event to prevent normal AE2 and memory card handling
            // Our PacketSaveMemoryCardWithFilters handler has already performed the save
            event.setUseItem(Event.Result.DENY);
            event.setUseBlock(Event.Result.DENY);
            event.setCanceled(true);

            // Clear the pending flag
            MemoryCardSaveTracker.clearPendingSave(player);
        }
    }
}
