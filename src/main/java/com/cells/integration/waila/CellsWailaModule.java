package com.cells.integration.waila;

import java.util.List;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.api.parts.IPart;
import appeng.integration.modules.waila.BaseWailaDataProvider;
import appeng.integration.modules.waila.part.PartAccessor;
import appeng.tile.AEBaseTile;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;

import com.cells.util.InterfaceProbeTooltipHelper;


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
        public List<String> getWailaBody(ItemStack itemStack,
                                         List<String> currentToolTip,
                                         IWailaDataAccessor accessor,
                                         IWailaConfigHandler config) {

            if (InterfaceProbeTooltipHelper.appendTooltipLines(accessor.getTileEntity(), currentToolTip::add)) {
                return currentToolTip;
            }

            TileEntity tile = accessor.getTileEntity();
            RayTraceResult mop = accessor.getMOP();
            if (tile == null || mop == null) return currentToolTip;

            Optional<IPart> maybePart = this.partAccessor.getMaybePart(tile, mop);
            if (!maybePart.isPresent()) return currentToolTip;

            InterfaceProbeTooltipHelper.appendTooltipLines(maybePart.get(), currentToolTip::add);
            return currentToolTip;
        }
    }
}