package com.cells.client;

import java.lang.ref.WeakReference;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.integration.waila.WailaProbeData;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketRequestProbeData;


@SideOnly(Side.CLIENT)
public final class WailaProbeClient {

    private static final long REQUEST_INTERVAL = 1_000_000_000L;

    private static WeakReference<World> world = new WeakReference<>(null);
    private static BlockPos pos;
    private static int partSide;
    private static long requestId;
    private static long lastResponseId;
    private static long lastRequest;
    private static NBTTagCompound data;

    private WailaProbeClient() {}

    @Nullable
    public static NBTTagCompound getData(World currentWorld, BlockPos currentPos, int currentPartSide) {
        if (world.get() != currentWorld || !currentPos.equals(pos) || partSide != currentPartSide) {
            world = new WeakReference<>(currentWorld);
            pos = new BlockPos(currentPos.getX(), currentPos.getY(), currentPos.getZ());
            partSide = currentPartSide;
            requestId++;
            lastResponseId = requestId;
            data = null;
        }

        long now = System.nanoTime();
        if (lastRequest == 0 || now - lastRequest >= REQUEST_INTERVAL) {
            lastRequest = now;
            requestId++;
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketRequestProbeData(
                    currentWorld.provider.getDimension(), pos, partSide, requestId));
        }

        return WailaProbeData.getData(data);
    }

    public static void receive(int dimension, BlockPos responsePos, int responsePartSide,
                               long responseId, @Nullable NBTTagCompound tag) {
        World currentWorld = Minecraft.getMinecraft().world;
        if (currentWorld == null || world.get() != currentWorld
                || currentWorld.provider.getDimension() != dimension || !responsePos.equals(pos)
                || responsePartSide != partSide || responseId <= lastResponseId || responseId > requestId) return;

        lastResponseId = responseId;
        data = tag;
    }
}
