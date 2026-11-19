package com.cells.profiling;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;


/**
 * Discovery of loaded adaptive interfaces, including logics that never poll during capture.
 */
public final class ProfileDiscovery {

    private ProfileDiscovery() {}

    public static void collect(ProfileSession session) {
        for (WorldServer world : DimensionManager.getWorlds()) {
            for (TileEntity tile : world.loadedTileEntityList) {
                if (tile.isInvalid()) continue;

                collectHost(session, tile);

                if (!(tile instanceof IPartHost)) continue;

                for (AEPartLocation side : AEPartLocation.values()) {
                    IPart part = ((IPartHost) tile).getPart(side);
                    if (part != null) collectHost(session, part);
                }
            }
        }
    }

    private static void collectHost(ProfileSession session, Object machine) {
        if (!(machine instanceof IInterfaceHost)) return;

        IInterfaceHost host = (IInterfaceHost) machine;
        if (host.getHostWorld() == null || host.getHostWorld().isRemote) return;

        if (host instanceof ICombinedInterfaceHost) {
            for (IInterfaceLogic logic : ((ICombinedInterfaceHost) host).getAllLogics()) {
                session.getAdaptiveEntry(host, logic, host.isExport());
            }
        } else if (host instanceof IIOInterfaceHost) {
            IIOInterfaceHost io = (IIOInterfaceHost) host;
            session.getAdaptiveEntry(host, io.getImportLogic(), false);
            session.getAdaptiveEntry(host, io.getExportLogic(), true);
        } else if (host instanceof IFilterableInterfaceHost) {
            IInterfaceLogic logic = ((IFilterableInterfaceHost<?, ?>) host).getInterfaceLogic();
            session.getAdaptiveEntry(host, logic, host.isExport());
        }
    }
}
