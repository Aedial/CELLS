package com.cells.commands;

import java.util.EnumSet;

import javax.annotation.Nonnull;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEPartLocation;

import com.cells.network.sync.ResourceType;
import com.cells.parts.subnetproxy.PartSubnetProxyBack;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


public class InspectSubnetProxyCommand extends CommandBase {

    private static final double REACH_DISTANCE = 5.0D;

    @Override
    @Nonnull
    public String getName() {
        return "inspectSubnetProxy";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "commands.cells.inspect_subnet_proxy.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentTranslation("commands.cells.inspect_slots.not_player"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        RayTraceResult hit = rayTrace(player);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            sender.sendMessage(new TextComponentTranslation("commands.cells.inspect_slots.no_block"));
            return;
        }

        BlockPos hitPos = hit.getBlockPos();
        TileEntity te = player.world.getTileEntity(hitPos);
        if (te == null) {
            sender.sendMessage(new TextComponentTranslation(
                "commands.cells.inspect_slots.no_tile", hitPos.getX(), hitPos.getY(), hitPos.getZ()));
            return;
        }

        TargetedProxy target = resolveTarget(te, hit);
        if (target == null) {
            sender.sendMessage(new TextComponentTranslation("commands.cells.inspect_subnet_proxy.no_proxy"));
            return;
        }

        if (target.targetedBack) {
            sender.sendMessage(new TextComponentTranslation(
                "commands.cells.inspect_subnet_proxy.back_header",
                hitPos.getX(),
                hitPos.getY(),
                hitPos.getZ(),
                player.world.provider.getDimension(),
                player.world.provider.getDimensionType().getName(),
                target.back.getSide().getFacing()));

            if (target.front == null) {
                sender.sendMessage(new TextComponentTranslation("commands.cells.inspect_subnet_proxy.no_front"));
                return;
            }
        }

        PartSubnetProxyFront.ProxyDiagnosticSnapshot snapshot = target.front.getDiagnosticSnapshot();
        BlockPos frontPos = snapshot.pos != null ? snapshot.pos : hitPos;
        EnumFacing frontSide = snapshot.side != null ? snapshot.side : hit.sideHit;

        if (!target.targetedBack) {
            sender.sendMessage(new TextComponentTranslation(
                "commands.cells.inspect_subnet_proxy.front_header",
                frontPos.getX(),
                frontPos.getY(),
                frontPos.getZ(),
                snapshot.dimensionId,
                snapshot.dimensionName,
                frontSide));
        } else {
            sender.sendMessage(new TextComponentTranslation(
                "commands.cells.inspect_subnet_proxy.linked_front",
                frontPos.getX(),
                frontPos.getY(),
                frontPos.getZ(),
                frontSide));
        }

        sender.sendMessage(new TextComponentTranslation(
            "commands.cells.inspect_subnet_proxy.state",
            boolText(snapshot.powered),
            boolText(snapshot.active),
            boolText(snapshot.paired),
            boolText(snapshot.ownOriginVisible),
            formatHash(snapshot.structureHash)));

        sender.sendMessage(new TextComponentTranslation(
            "commands.cells.inspect_subnet_proxy.channels",
            describeEnabledChannels(snapshot.enabledChannels),
            snapshot.priority,
            boolText(snapshot.hasInsertionCard),
            boolText(snapshot.insertionActive),
            snapshot.filterMode.name(),
            boolText(snapshot.fuzzyEnabled),
            boolText(snapshot.inverterEnabled)));

        sender.sendMessage(new TextComponentTranslation(
            "commands.cells.inspect_subnet_proxy.grids",
            formatHash(snapshot.frontGridHash),
            formatHash(snapshot.backGridHash),
            snapshot.exposedOriginCount,
            snapshot.visiblePeerCount,
            snapshot.totalPeerCount,
            snapshot.itemLocalCells,
            snapshot.fluidLocalCells,
            snapshot.gasLocalCells,
            snapshot.essentiaLocalCells));

        if (snapshot.lastFault == null) {
            sender.sendMessage(new TextComponentTranslation("commands.cells.inspect_subnet_proxy.last_fault.none"));
            return;
        }

        sender.sendMessage(new TextComponentTranslation(
            "commands.cells.inspect_subnet_proxy.last_fault",
            snapshot.lastFault.lastObservedTick,
            snapshot.lastFault.occurrenceCount,
            snapshot.lastFault.channelName,
            snapshot.lastFault.requestDescription,
            snapshot.lastFault.requestedAmount,
            snapshot.lastFault.extractedAmount,
            snapshot.lastFault.visibleAmount,
            snapshot.lastFault.actionName,
            formatHash(snapshot.lastFault.structureHash)));
    }

    private static TargetedProxy resolveTarget(TileEntity te, RayTraceResult hit) {
        if (!(te instanceof IPartHost)) return null;

        IPartHost host = (IPartHost) te;
        SelectedPart selected = host.selectPartGlobal(hit.hitVec);
        IPart part = selected.part;

        if (!(part instanceof PartSubnetProxyFront) && !(part instanceof PartSubnetProxyBack) && hit.sideHit != null) {
            part = host.getPart(AEPartLocation.fromFacing(hit.sideHit));
        }

        if (part instanceof PartSubnetProxyFront) {
            PartSubnetProxyFront front = (PartSubnetProxyFront) part;
            return new TargetedProxy(front, front.findBackPart(), false);
        }

        if (part instanceof PartSubnetProxyBack) {
            PartSubnetProxyBack back = (PartSubnetProxyBack) part;
            return new TargetedProxy(back.findFrontPart(), back, true);
        }

        return null;
    }

    private static RayTraceResult rayTrace(EntityPlayerMP player) {
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLookVec();
        Vec3d end = start.add(lookVec.scale(REACH_DISTANCE));

        return player.world.rayTraceBlocks(start, end, false, false, true);
    }

    private static String boolText(boolean value) {
        return value ? "yes" : "no";
    }

    private static String formatHash(int hash) {
        return String.format("%08X", hash);
    }

    private static String describeEnabledChannels(EnumSet<ResourceType> channels) {
        if (channels.isEmpty()) return "none";

        StringBuilder description = new StringBuilder();
        for (ResourceType channel : channels) {
            if (description.length() > 0) description.append(", ");

            description.append(channel.name());
        }

        return description.toString();
    }

    private static class TargetedProxy {

        private final PartSubnetProxyFront front;
        private final PartSubnetProxyBack back;
        private final boolean targetedBack;

        private TargetedProxy(PartSubnetProxyFront front, PartSubnetProxyBack back, boolean targetedBack) {
            this.front = front;
            this.back = back;
            this.targetedBack = targetedBack;
        }
    }
}