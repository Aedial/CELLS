package com.cells.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import com.cells.Tags;


/**
 * GUI Factory for the Cells mod configuration.
 * <p>
 * Enables in-game modification of configuration values through the
 * Mod Options menu.
 * </p>
 */
public class CellsGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // No initialization needed
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new CellsConfigGui(parentScreen);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    /**
     * The configuration GUI screen for the Cells mod.
     */
    public static class CellsConfigGui extends GuiConfig {

        public CellsConfigGui(GuiScreen parentScreen) {
            super(
                parentScreen,
                getConfigElements(),
                Tags.MODID,
                false,
                false,
                Tags.MODNAME + " Configuration"
            );
        }

        /**
         * Gets all configuration elements for display in the GUI.
         *
         * @return List of config elements
         */
        private static List<IConfigElement> getConfigElements() {
            List<IConfigElement> elements = new ArrayList<>();

            // Add each category
            elements.addAll(new ConfigElement(
                CellsConfig.getConfig().getCategory(CellsConfig.CATEGORY_GENERAL)
            ).getChildElements());

            elements.addAll(new ConfigElement(
                CellsConfig.getConfig().getCategory(CellsConfig.CATEGORY_IDLE_DRAIN)
            ).getChildElements());

            elements.addAll(new ConfigElement(
                CellsConfig.getConfig().getCategory(CellsConfig.CATEGORY_ENABLED)
            ).getChildElements());

            return elements;
        }
    }
}
