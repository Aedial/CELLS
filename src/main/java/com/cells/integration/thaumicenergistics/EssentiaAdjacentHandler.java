package com.cells.integration.thaumicenergistics;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import thaumcraft.api.aspects.IAspectContainer;

import com.cells.blocks.interfacebase.managers.InterfaceAdjacentHandler;
import com.cells.blocks.interfacebase.managers.InterfaceInventoryManager;

import thaumicenergistics.api.EssentiaStack;


/**
 * Essentia-specific adjacent handler that scans for {@link IAspectContainer} directly
 * on adjacent tile entities, since essentia does not use Forge capabilities.
 * <p>
 * Overrides {@link #scanAndCacheFacing} to check for IAspectContainer instead of
 * querying a Forge Capability. The cached "handler" is the TileEntity itself
 * (which implements IAspectContainer), for use by the ResourceOps callbacks.
 */
public class EssentiaAdjacentHandler extends InterfaceAdjacentHandler<EssentiaStack, EssentiaStackKey> {

    /** Our own position, cached to prevent self-caching */
    private final Callbacks callbacks;

    public EssentiaAdjacentHandler(
            ResourceOps<EssentiaStack, EssentiaStackKey> ops,
            Callbacks callbacks,
            InterfaceInventoryManager<EssentiaStack, ?, EssentiaStackKey> inventoryManager
    ) {
        super(ops, callbacks, inventoryManager);
        this.callbacks = callbacks;
    }

    /**
     * Scan for IAspectContainer on an adjacent tile entity.
     * Stores the TileEntity itself as the handler, since the ResourceOps callbacks
     * cast it to IAspectContainer.
     */
    @Override
    protected void scanAndCacheFacing(World world, BlockPos pos, EnumFacing facing) {
        BlockPos adjacentPos = pos.offset(facing);

        if (!world.isBlockLoaded(adjacentPos)) return;

        TileEntity te = world.getTileEntity(adjacentPos);
        if (te == null || te.isInvalid()) return;
        if (!(te instanceof IAspectContainer)) return;

        // Don't cache ourselves (should never happen, but just in case)
        BlockPos selfPos = this.callbacks.getHostPos();
        if (adjacentPos.equals(selfPos)) return;

        // Store the TileEntity as both the TE reference and the "handler"
        putCapabilityCache(facing, te, te);
    }
}
