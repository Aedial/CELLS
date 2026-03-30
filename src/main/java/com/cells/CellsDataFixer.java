package com.cells;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.IFixableData;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.common.util.ModFixs;
import net.minecraftforge.fml.common.FMLCommonHandler;


/**
 * Handles tile entity ID migration for worlds saved with older CELLS versions.
 *
 * When a tile entity's NBT is loaded from disk, the DataFixer system runs before
 * TileEntity.create() looks up the ID in the registry. This lets us rewrite stale
 * IDs so the tile entity is found under its current registration name instead of
 * being silently dropped ("Skipping BlockEntity with id ...").
 */
public class CellsDataFixer implements IFixableData {

    // Bump this every time a new rename entry is added.
    // The fixer only runs on chunks whose saved data version is lower than this.
    private static final int DATA_VERSION = 1;

    private final Map<String, String> renames = new HashMap<>();

    public CellsDataFixer() {
        // 0.5.9-rc and earlier used "fluid_export_interface";
        // 0.5.10+ uses "export_fluid_interface" (consistent with other interface names).
        addRename("fluid_export_interface", "export_fluid_interface");
    }

    /**
     * Register all data fixers for this mod.
     * Must be called during preInit, before any world is loaded.
     */
    public static void register() {
        CompoundDataFixer fixer = FMLCommonHandler.instance().getDataFixer();
        ModFixs fixes = fixer.init(Tags.MODID, DATA_VERSION);
        fixes.registerFix(FixTypes.BLOCK_ENTITY, new CellsDataFixer());
    }

    @Override
    public int getFixVersion() {
        return DATA_VERSION;
    }

    @Override
    public NBTTagCompound fixTagCompound(NBTTagCompound compound) {
        String id = compound.getString("id");

        if (renames.containsKey(id)) compound.setString("id", renames.get(id));

        return compound;
    }

    /**
     * Maps the fully-qualified name ({@code cells:<old>}) to the new
     * fully-qualified name ({@code cells:<new>}).
     */
    private void addRename(String oldName, String newName) {
        renames.put(Tags.MODID + ":" + oldName, Tags.MODID + ":" + newName);
    }
}
