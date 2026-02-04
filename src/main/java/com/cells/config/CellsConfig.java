package com.cells.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.cells.Tags;


/**
 * Server configuration for the Cells mod.
 * <p>
 * Provides configurable values for:
 * <ul>
 *   <li>Idle drain rates for each cell type</li>
 *   <li>Maximum types for standard cells</li>
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

    /** Maximum item types for standard (non-compacting) cells */
    public static int maxTypes = 63;

    /** Maximum item types for hyper-density cells */
    public static int hdMaxTypes = 63;

    /** Idle drain for compacting cells */
    public static double compactingIdleDrain = 6.0;

    /** Idle drain for hyper-density cells */
    public static double hdIdleDrain = 10.0;

    /** Idle drain for hyper-density compacting cells */
    public static double hdCompactingIdleDrain = 20.0;

    /** Idle drain for normal (extended) cells */
    public static double normalIdleDrain = 3.0;

    /** Idle drain for fluid hyper-density cells */
    public static double fluidHdIdleDrain = 10.0;

    /** Idle drain for fluid normal (extended) cells */
    public static double fluidNormalIdleDrain = 3.0;

    /** Enable compacting cells */
    public static boolean enableCompactingCells = true;

    /** Enable hyper-density cells */
    public static boolean enableHDCells = true;

    /** Enable hyper-density compacting cells */
    public static boolean enableHDCompactingCells = true;

    /** Enable normal (64M-2G) cells */
    public static boolean enableNormalCells = true;

    /** Enable fluid hyper-density cells */
    public static boolean enableFluidHDCells = true;

    /** Enable fluid normal (64M-2G) cells */
    public static boolean enableFluidNormalCells = true;

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
        // General category
        config.addCustomCategoryComment(CATEGORY_GENERAL, "General settings for cell behavior");

        maxTypes = config.getInt(
            "maxTypes", CATEGORY_GENERAL, 63, 1, 63,
            "Maximum item types for standard storage cells (1-63)"
        );

        hdMaxTypes = config.getInt(
            "hdMaxTypes", CATEGORY_GENERAL, 63, 1, 63,
            "Maximum item types for hyper-density storage cells (1-63)"
        );

        // Idle drain category
        config.addCustomCategoryComment(CATEGORY_IDLE_DRAIN,
            "Idle power drain settings (AE power per tick). Higher values = more power consumption.");

        compactingIdleDrain = config.getFloat(
            "compactingIdleDrain", CATEGORY_IDLE_DRAIN, 0.5f, 0.0f, 100.0f,
            "Idle drain for compacting cells"
        );

        hdIdleDrain = config.getFloat(
            "hdIdleDrain", CATEGORY_IDLE_DRAIN, 1.0f, 0.0f, 100.0f,
            "Idle drain for hyper-density cells"
        );

        hdCompactingIdleDrain = config.getFloat(
            "hdCompactingIdleDrain", CATEGORY_IDLE_DRAIN, 1.0f, 0.0f, 100.0f,
            "Idle drain for hyper-density compacting cells"
        );

        normalIdleDrain = config.getFloat(
            "normalIdleDrain", CATEGORY_IDLE_DRAIN, 3.0f, 0.0f, 100.0f,
            "Idle drain for normal (extended capacity) cells"
        );

        fluidHdIdleDrain = config.getFloat(
            "fluidHdIdleDrain", CATEGORY_IDLE_DRAIN, 1.0f, 0.0f, 100.0f,
            "Idle drain for fluid hyper-density cells"
        );

        fluidNormalIdleDrain = config.getFloat(
            "fluidNormalIdleDrain", CATEGORY_IDLE_DRAIN, 3.0f, 0.0f, 100.0f,
            "Idle drain for fluid normal (extended capacity) cells"
        );

        // Enabled cells category
        config.addCustomCategoryComment(CATEGORY_ENABLED,
            "Enable or disable specific cell types. Disabled cells will not be registered.");

        enableCompactingCells = config.getBoolean(
            "enableCompactingCells", CATEGORY_ENABLED, true,
            "Enable compacting storage cells"
        );

        enableHDCells = config.getBoolean(
            "enableHDCells", CATEGORY_ENABLED, true,
            "Enable hyper-density storage cells"
        );

        enableHDCompactingCells = config.getBoolean(
            "enableHDCompactingCells", CATEGORY_ENABLED, true,
            "Enable hyper-density compacting storage cells"
        );

        enableNormalCells = config.getBoolean(
            "enableNormalCells", CATEGORY_ENABLED, true,
            "Enable normal (64M-2G) storage cells"
        );

        enableFluidHDCells = config.getBoolean(
            "enableFluidHDCells", CATEGORY_ENABLED, true,
            "Enable fluid hyper-density storage cells"
        );

        enableFluidNormalCells = config.getBoolean(
            "enableFluidNormalCells", CATEGORY_ENABLED, true,
            "Enable fluid normal (64M-2G) storage cells"
        );

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
