package com.cells.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;

import com.cells.ItemRegistry;
import com.cells.config.CellsConfig;


/**
 * JEI integration plugin for CELLS mod.
 * <p>
 * Registers dynamic recipe plugins for:
 * - Configurable cell assembly (empty cell + component = filled cell)
 */
@JEIPlugin
public class CellsJEIPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        // Register configurable cell assembly recipe plugin
        if (CellsConfig.enableConfigurableCells && ItemRegistry.CONFIGURABLE_CELL != null) {
            registry.addRecipeRegistryPlugin(new ConfigurableCellRegistryPlugin());
        }
    }
}
