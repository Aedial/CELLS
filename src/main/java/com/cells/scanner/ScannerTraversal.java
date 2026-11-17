package com.cells.scanner;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.capabilities.Capabilities;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.parts.misc.PartStorageBus;

import com.cells.api.ISubnetProxy;


/**
 * Cross-grid traversal for the interface scanner.
 * Supported traversal :
 * - Normal subnet semantics (storage bus on interface)
 * - Subnet proxies
 */
public final class ScannerTraversal {

    private ScannerTraversal() {}

    public static boolean isSubnetMachine(Class<?> type) {
        return ISubnetProxy.class.isAssignableFrom(type)
            || PartStorageBus.class.isAssignableFrom(type)
            || PartFluidStorageBus.class.isAssignableFrom(type)
            || appeng.helpers.IInterfaceHost.class.isAssignableFrom(type)
            || appeng.fluids.helper.IFluidInterfaceHost.class.isAssignableFrom(type)
            || type.getName().equals("com.mekeng.github.common.part.PartGasStorageBus")
            || type.getName().equals("com.mekeng.github.common.tile.TileGasInterface")
            || type.getName().equals("com.mekeng.github.common.part.PartGasInterface")
            || type.getName().equals("thaumicenergistics.part.PartEssentiaStorageBus")
            || type.getName().equals("thaumicenergistics.tile.TileEssentiaInterface");
    }

    @Nullable
    public static IGridNode getNode(World world, BlockPos pos, AEPartLocation side) {
        if (!world.isBlockLoaded(pos)) return null;

        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof IPartHost) {
            IPart part = ((IPartHost) tile).getPart(side);
            return part != null ? part.getGridNode() : null;
        }

        return tile instanceof IGridHost ? ((IGridHost) tile).getGridNode(side) : null;
    }

    public static void collectSubnetNodes(IGridNode node, Consumer<IGridNode> target) {
        Object machine = node.getMachine();
        IGrid localGrid = node.getGrid();
        if (machine instanceof ISubnetProxy) {
            Object remote = ((ISubnetProxy) machine).getTargetGrid();
            if (remote instanceof IGrid && remote != localGrid) target.accept(((IGrid) remote).getPivot());
        }

        TileEntity tile = getHostTile(node);
        if (tile == null || tile.getWorld() == null) return;

        EnumFacing partFacing = machine instanceof IPart ? getPartFacing((IPart) machine, tile) : null;
        if (machine instanceof IPart && partFacing == null) return;

        if (isStorageBus(machine) && machine instanceof IPart) {
            IGridNode remote = getFacingNode(tile, partFacing);
            if (remote != null && remote.getGrid() != localGrid) target.accept(remote);
        }

        // Follow storage buses facing an interface on the scanned grid
        for (EnumFacing facing : EnumFacing.values()) {
            if (machine instanceof IPart && partFacing != facing) continue;
            if (!tile.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, facing)) continue;

            TileEntity adjacent = getAdjacentTile(tile, facing);
            if (!(adjacent instanceof IPartHost)) continue;

            IPart part = ((IPartHost) adjacent).getPart(AEPartLocation.fromFacing(facing.getOpposite()));
            if (!isStorageBus(part)) continue;

            IGridNode remote = part.getGridNode();
            if (remote != null && remote.getGrid() != localGrid) target.accept(remote);
        }
    }

    @Nullable
    private static IGridNode getFacingNode(TileEntity tile, @Nullable EnumFacing facing) {
        if (facing == null) return null;

        TileEntity adjacent = getAdjacentTile(tile, facing);
        EnumFacing targetSide = facing.getOpposite();
        if (adjacent == null || !adjacent.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) {
            return null;
        }

        // Resolve the part exposed on the connected face of a cable host
        return getNode(adjacent.getWorld(), adjacent.getPos(), AEPartLocation.fromFacing(targetSide));
    }

    @Nullable
    private static TileEntity getAdjacentTile(TileEntity tile, EnumFacing facing) {
        World world = tile.getWorld();
        BlockPos pos = tile.getPos().offset(facing);
        return world.isBlockLoaded(pos) ? world.getTileEntity(pos) : null;
    }

    @Nullable
    private static TileEntity getHostTile(IGridNode node) {
        Object machine = node.getMachine();
        if (machine instanceof TileEntity) return (TileEntity) machine;
        if (!(machine instanceof IPart)) return null;

        DimensionalCoord location = node.getGridBlock().getLocation();
        if (location == null || location.getWorld() == null) return null;

        World world = location.getWorld();
        BlockPos pos = location.getPos();
        return world.isBlockLoaded(pos) ? world.getTileEntity(pos) : null;
    }

    @Nullable
    private static EnumFacing getPartFacing(IPart part, TileEntity tile) {
        if (!(tile instanceof IPartHost)) return null;

        IPartHost host = (IPartHost) tile;
        for (EnumFacing facing : EnumFacing.values()) {
            if (host.getPart(facing) == part) return facing;
        }

        return null;
    }

    private static boolean isStorageBus(@Nullable Object machine) {
        if (machine instanceof PartStorageBus || machine instanceof PartFluidStorageBus) return true;
        if (machine == null) return false;

        String className = machine.getClass().getName();
        return className.equals("com.mekeng.github.common.part.PartGasStorageBus")
            || className.equals("thaumicenergistics.part.PartEssentiaStorageBus");
    }
}
