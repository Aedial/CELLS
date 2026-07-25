package com.cells.integration.theoneprobe;

import java.util.Optional;
import java.util.function.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.event.FMLInterModComms;

import appeng.api.parts.IPart;
import appeng.integration.modules.theoneprobe.part.PartAccessor;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

import com.cells.Tags;
public class CellsTheOneProbePlugin implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

    private final PartAccessor partAccessor = new PartAccessor();

    public static void register() {
        FMLInterModComms.sendFunctionMessage(
            "theoneprobe",
            "getTheOneProbe",
            CellsTheOneProbePlugin.class.getName());
    }

    @Override
    public Void apply(ITheOneProbe input) {
        TopLocalizedTextElement.register(input);
        input.registerProvider(this);
        return null;
    }

    @Override
    public String getID() {
        return Tags.MODID + ":interface_intervals";
    }

    @Override
    public void addProbeInfo(ProbeMode mode,
                             IProbeInfo probeInfo,
                             EntityPlayer player,
                             World world,
                             IBlockState blockState,
                             IProbeHitData data) {
        TileEntity tile = world.getTileEntity(data.getPos());
        if (TopInterfaceProbeTooltipHelper.appendTooltipLines(tile, probeInfo)) return;

        Optional<IPart> maybePart = this.partAccessor.getMaybePart(tile, data);
        if (!maybePart.isPresent()) return;

        TopInterfaceProbeTooltipHelper.appendTooltipLines(maybePart.get(), probeInfo);
    }
}