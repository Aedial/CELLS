package com.cells.gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;

import com.cells.Cells;


/**
 * Utility class for GUI ID encoding and decoding.
 * <p>
 * For parts, the GUI ID encodes both the base ID and the side:
 * - Bits 0-2: side ordinal (AEPartLocation, 0-6)
 * - Bits 3+: base GUI ID shifted left by 3
 */
public final class GuiIdUtils {

    private GuiIdUtils() {
        // Utility class
    }

    /**
     * Encode a part GUI ID with side information.
     *
     * @param baseGuiId The base GUI ID (must be a part GUI ID >= 100)
     * @param side The side the part is on
     * @return The encoded GUI ID with side information
     */
    public static int encodePartGuiId(int baseGuiId, AEPartLocation side) {
        return (baseGuiId << 3) | (side.ordinal() & 0x07);
    }

    /**
     * Extract the base GUI ID from an encoded ID.
     */
    public static int getBaseGuiId(int encodedId) {
        return encodedId >> 3;
    }

    /**
     * Extract the side from an encoded ID.
     */
    public static AEPartLocation getSideFromGuiId(int encodedId) {
        return AEPartLocation.fromOrdinal(encodedId & 0x07);
    }

    /**
     * Check if a GUI ID is for a part (encoded with side).
     * Part GUI IDs have base ID >= 100.
     */
    public static boolean isPartGui(int id) {
        int baseId = getBaseGuiId(id);
        return baseId >= 100;
    }

    /**
     * Get the part from a tile entity given the encoded GUI ID.
     */
    public static IPart getPartFromTile(TileEntity tile, int encodedId) {
        if (!(tile instanceof IPartHost)) return null;

        AEPartLocation side = getSideFromGuiId(encodedId);
        return ((IPartHost) tile).getPart(side);
    }

    /**
     * Open a GUI for a part, encoding the side information.
     *
     * @param player The player opening the GUI
     * @param tile The tile entity (usually IPartHost)
     * @param side The side the part is on
     * @param guiId The base GUI ID
     */
    public static void openPartGui(EntityPlayer player, TileEntity tile, AEPartLocation side, int guiId) {
        if (tile == null) return;

        BlockPos pos = tile.getPos();
        int encodedId = encodePartGuiId(guiId, side);

        player.openGui(Cells.instance, encodedId, tile.getWorld(), pos.getX(), pos.getY(), pos.getZ());
    }
}
