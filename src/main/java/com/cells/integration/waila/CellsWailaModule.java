package com.cells.integration.waila;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.integration.modules.waila.part.PartAccessor;
import appeng.tile.AEBaseTile;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;

import com.cells.util.InterfaceProbeTooltipHelper;
import com.cells.util.LocalizedTooltipText;
import com.cells.util.AbstractProbeTooltipHelper;
import com.cells.client.WailaProbeClient;


public final class CellsWailaModule {

    private CellsWailaModule() {}

    public static void register() {
        FMLInterModComms.sendMessage(
            "waila",
            "register",
            CellsWailaModule.class.getName() + ".register");
    }

    public static void register(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new CellsWailaDataProvider(), AEBaseTile.class);
    }

    private static final class CellsWailaDataProvider extends BaseWailaDataProvider {

        private final PartAccessor partAccessor = new PartAccessor();

        @Override
        @Nonnull
        public List<String> getWailaBody(ItemStack itemStack,
                                         List<String> currentToolTip,
                                         IWailaDataAccessor accessor,
                                         IWailaConfigHandler config) {

            TileEntity tile = accessor.getTileEntity();
            if (tile == null || tile.getWorld() == null) return currentToolTip;

            int partSide = -1;
            if (!AbstractProbeTooltipHelper.supportsTarget(tile)) {
                RayTraceResult mop = accessor.getMOP();
                if (!(tile instanceof IPartHost) || mop == null) return currentToolTip;

                Optional<IPart> part = this.partAccessor.getMaybePart(tile, mop);
                if (!part.isPresent() || !AbstractProbeTooltipHelper.supportsTarget(part.get())) return currentToolTip;

                partSide = getPartSide((IPartHost) tile, part.get());
                if (partSide < 0) return currentToolTip;
            }

            NBTTagCompound data = WailaProbeClient.getData(tile.getWorld(), tile.getPos(), partSide);
            if (data == null) return currentToolTip;

            for (LocalizedTooltipText line : WailaProbeData.readLines(data)) {
                currentToolTip.add(InterfaceProbeTooltipHelper.render(line));
            }
            return currentToolTip;
        }

        private int getPartSide(IPartHost host, IPart part) {
            for (AEPartLocation side : AEPartLocation.values()) {
                if (host.getPart(side) == part) return side.ordinal();
            }

            return -1;
        }
    }
}