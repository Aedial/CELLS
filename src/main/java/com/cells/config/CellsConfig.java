package com.cells.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cells.Tags;


/**
 * Server configuration for the Cells mod.
 * <p>
 * Provides configurable values for:
 * <ul>
 *   <li>Idle drain rates for each cell type</li>
 *   <li>Maximum types per cell type</li>
 *   <li>Enabling/disabling individual cell types</li>
 * </ul>
 * </p>
 * <p>
 * Supports in-game modification via the Forge config GUI.
 * </p>
 */
public class CellsConfig {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_CELLS = "cells";
    public static final String CATEGORY_IDLE_DRAIN = "idle_drain";
    public static final String CATEGORY_ENABLED = "enabled_cells";

    private static Configuration config;

    /** Maximum item types for hyper-density item cells */
    public static int hdItemMaxTypes = 63;

    /** Maximum item types for hyper-density fluid cells */
    public static int hdFluidMaxTypes = 63;

    /** Idle drain for compacting cells */
    public static double compactingIdleDrain = 6.0;

    /** Idle drain for hyper-density cells */
    public static double hdIdleDrain = 10.0;

    /** Idle drain for hyper-density compacting cells */
    public static double hdCompactingIdleDrain = 20.0;

    /** Idle drain for fluid hyper-density cells */
    public static double fluidHdIdleDrain = 10.0;

    /** Enable compacting cells */
    public static boolean enableCompactingCells = true;

    /** Enable hyper-density cells */
    public static boolean enableHDCells = true;

    /** Enable hyper-density compacting cells */
    public static boolean enableHDCompactingCells = true;

    /** Enable fluid hyper-density cells */
    public static boolean enableFluidHDCells = true;

    /** Enable configurable cells */
    public static boolean enableConfigurableCells = true;

    /** Idle drain for configurable cells */
    public static double configurableCellIdleDrain = 3.0;

    /** Maximum types for configurable cells */
    public static int configurableCellMaxTypes = 63;

    /**
     * Initializes the configuration from the given file.
     *
     * @param configFile The configuration file
     */
    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    /**
     * Gets the configuration instance for the GUI.
     *
     * @return The configuration instance
     */
    public static Configuration getConfig() {
        return config;
    }

    /**
     * Loads all configuration values from file.
     */
    public static void loadConfig() {
        // Category language keys
        config.getCategory(CATEGORY_GENERAL).setLanguageKey(Tags.MODID + ".config.category.general");
        config.getCategory(CATEGORY_IDLE_DRAIN).setLanguageKey(Tags.MODID + ".config.category.idle_drain");
        config.getCategory(CATEGORY_ENABLED).setLanguageKey(Tags.MODID + ".config.category.enabled_cells");

        // General category
        config.addCustomCategoryComment(CATEGORY_GENERAL, "General settings for cell behavior");

        Property p = config.get(CATEGORY_GENERAL,
            "hdItemMaxTypes", 63,
            "Maximum item types for hyper-density item storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.hdItemMaxTypes");
        hdItemMaxTypes = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "hdFluidMaxTypes", 63,
            "Maximum item types for hyper-density fluid storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.hdFluidMaxTypes");
        hdFluidMaxTypes = p.getInt();

        // Idle drain category
        config.addCustomCategoryComment(CATEGORY_IDLE_DRAIN,
            "Idle power drain settings (AE power per tick). Higher values = more power consumption.");

        p = config.get(CATEGORY_IDLE_DRAIN,
            "compactingIdleDrain", 6.0D,
            "Idle drain for compacting cells", 0.0D, 100.0D
        );
        p.setLanguageKey(Tags.MODID + ".config.compactingIdleDrain");
        compactingIdleDrain = p.getDouble();

        p = config.get(CATEGORY_IDLE_DRAIN,
            "hdIdleDrain", 10.0D,
            "Idle drain for hyper-density cells", 0.0D, 100.0D
        );
        p.setLanguageKey(Tags.MODID + ".config.hdIdleDrain");
        hdIdleDrain = p.getDouble();

        p = config.get(CATEGORY_IDLE_DRAIN,
            "hdCompactingIdleDrain", 20.0D,
            "Idle drain for hyper-density compacting cells", 0.0D, 100.0D
        );
        p.setLanguageKey(Tags.MODID + ".config.hdCompactingIdleDrain");
        hdCompactingIdleDrain = p.getDouble();

        p = config.get(CATEGORY_IDLE_DRAIN,
            "fluidHdIdleDrain", 10.0D,
            "Idle drain for fluid hyper-density cells", 0.0D, 100.0D
        );
        p.setLanguageKey(Tags.MODID + ".config.fluidHdIdleDrain");
        fluidHdIdleDrain = p.getDouble();

        p = config.get(CATEGORY_IDLE_DRAIN,
            "configurableCellIdleDrain", 3.0D,
            "Idle drain for configurable cells", 0.0D, 100.0D
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellIdleDrain");
        configurableCellIdleDrain = p.getDouble();

        // Enabled cells category
        config.addCustomCategoryComment(CATEGORY_ENABLED,
            "Enable or disable specific cell types. Disabled cells will not be registered.");

        p = config.get(CATEGORY_ENABLED,
            "enableCompactingCells", true,
            "Enable compacting storage cells"
        );
        p.setLanguageKey(Tags.MODID + ".config.enableCompactingCells");
        enableCompactingCells = p.getBoolean();

        p = config.get(CATEGORY_ENABLED,
            "enableHDCells", true,
            "Enable hyper-density storage cells"
        );
        p.setLanguageKey(Tags.MODID + ".config.enableHDCells");
        enableHDCells = p.getBoolean();

        p = config.get(CATEGORY_ENABLED,
            "enableHDCompactingCells", true,
            "Enable hyper-density compacting storage cells"
        );
        p.setLanguageKey(Tags.MODID + ".config.enableHDCompactingCells");
        enableHDCompactingCells = p.getBoolean();

        p = config.get(CATEGORY_ENABLED,
            "enableFluidHDCells", true,
            "Enable fluid hyper-density storage cells"
        );
        p.setLanguageKey(Tags.MODID + ".config.enableFluidHDCells");
        enableFluidHDCells = p.getBoolean();

        p = config.get(CATEGORY_ENABLED,
            "enableConfigurableCells", true,
            "Enable configurable storage cells"
        );
        p.setLanguageKey(Tags.MODID + ".config.enableConfigurableCells");
        enableConfigurableCells = p.getBoolean();

        // General: configurable cell max types
        p = config.get(CATEGORY_GENERAL,
            "configurableCellMaxTypes", 63,
            "Maximum item types for configurable storage cells (1-63)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellMaxTypes");
        configurableCellMaxTypes = p.getInt();

        // Save if config was created or changed
        if (config.hasChanged()) config.save();
    }

    /**
     * Event handler for config changes from the GUI.
     *
     * @param event The config changed event
     */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(Tags.MODID)) loadConfig();
    }
}
