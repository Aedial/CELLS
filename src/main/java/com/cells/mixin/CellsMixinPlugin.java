package com.cells.mixin;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

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
        return Collections.singletonList("mixins.cells.json");
    }
}
