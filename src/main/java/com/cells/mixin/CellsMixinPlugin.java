package com.cells.mixin;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import zone.rong.mixinbooter.ILateMixinLoader;


/**
 * Mixin plugin for CELLS mod.
 * <p>
 * This class registers the CELLS mixin configuration with MixinBooter.
 * It implements {@link ILateMixinLoader} because our mixins target mod classes
 * (AE2's TileChest and TileCellWorkbench), not vanilla/Forge classes.
 * </p>
 * <p>
 * The plugin is discovered automatically by MixinBooter at runtime.
 * It must be annotated with {@link Optional.Interface} to handle cases
 * where MixinBooter is not installed.
 * </p>
 */
@Optional.Interface(iface = "zone.rong.mixinbooter.ILateMixinLoader", modid = "mixinbooter")
public class CellsMixinPlugin implements ILateMixinLoader {

    @Override
    @Optional.Method(modid = "mixinbooter")
    public List<String> getMixinConfigs() {
        MixinState.markMixinsEnabled();

        List<String> configs = new ArrayList<>();
        configs.add("mixins.cells.json");

        // Load JEI mixin only if JEI is present
        if (Loader.isModLoaded("jei")) configs.add("mixins.cells.jei.json");

        return configs;
    }
}
