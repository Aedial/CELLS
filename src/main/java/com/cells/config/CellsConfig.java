package com.cells.config;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private static final int[] DEFAULT_EMC_CELL_PARTITION_SLOTS = new int[] {1, 9, 54};
    public static final long DEFAULT_EMC_CELL_REPORTED_AMOUNT = Integer.MAX_VALUE;

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_CELLS = "cells";
    public static final String CATEGORY_IDLE_DRAIN = "idle_drain";
    public static final String CATEGORY_ENABLED = "enabled_cells";
    public static final String CATEGORY_INTERFACES = "interfaces";

    public static final String CATEGORY_HIDDEN = "hidden";

    static public final List<String> hiddenCategories = Arrays.asList(
        CATEGORY_HIDDEN
    );

    // Default blacklist entries for essentia containers that should be ignored by the Essentia Interface
    // Mithminite Jar is blacklisted by default because it crashes if overfilled more than 250 (bug with the jar)
    static private final String[] defaultEssentiaContainerBlacklist = new String[] {
        "thaumadditions:jar_mithminite"
    };

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

    /** Maximum types for configurable item cells */
    public static int configurableCellItemMaxTypes = 63;

    /** Maximum types for configurable fluid cells */
    public static int configurableCellFluidMaxTypes = 63;

    /** Maximum types for configurable essentia cells */
    public static int configurableCellEssentiaMaxTypes = 63;

    /** Maximum types for configurable gas cells */
    public static int configurableCellGasMaxTypes = 63;

    /** Upgrade slots for compacting cells */
    public static int compactingCellUpgradeSlots = 4;

    /** Upgrade slots for hyper-density item cells */
    public static int hdItemCellUpgradeSlots = 4;

    /** Upgrade slots for hyper-density compacting cells */
    public static int hdCompactingCellUpgradeSlots = 4;

    /** Upgrade slots for hyper-density fluid cells */
    public static int hdFluidCellUpgradeSlots = 4;

    /** Upgrade slots for configurable cells */
    public static int configurableCellUpgradeSlots = 4;

    /** Server tick interval between EMC cell buffer flushes */
    public static int emcCellSyncIntervalTicks = 20;

    /** Reported stack size for learned EMC cell filters */
    public static long emcCellReportedAmount = DEFAULT_EMC_CELL_REPORTED_AMOUNT;

    /** Base tier plus upgrade-defined unlocked partition slots for the EMC cell */
    public static int[] emcCellPartitionSlots = DEFAULT_EMC_CELL_PARTITION_SLOTS.clone();

    /** NBT size warning threshold in KB (tooltip shows warning when exceeded) */
    public static int nbtSizeWarningThresholdKB = 100;

    /** Enable NBT size computation and display in cell tooltips */
    public static boolean enableNbtSizeTooltip = true;

    /** Maximum slot size limit for interfaces (caps user-configurable max slot size) */
    public static long interfaceMaxSlotSizeLimit = Long.MAX_VALUE;

    /** Minimum polling rate for interfaces (0 = allow adaptive) */
    public static int interfaceMinPollingRate = 0;

    /** Use fixed (non-animated) textures for interface blocks and parts. Requires restart. */
    public static boolean useFixedInterfaceTextures = true;

    /** Whether the controls help panel is visible in Interface GUIs. Persisted as a hidden config. */
    public static boolean showControlsHelp = false;

    /** Whether JEI recipe transfer sends recipe inputs to Export interfaces and outputs to Import interfaces, or the opposite */
    public static boolean jeiTransferInputsToExport = true;

    /** Whether JEI recipe transfer adds recipe outputs to Creative Cell filters instead of recipe inputs */
    public static boolean jeiTransferOutputsToCreativeCell = true;

    /** Number of upgrade slots for the Subnet Proxy (1-24) */
    public static int subnetProxyUpgradeSlots = 5;

    /** Minimum tick rate for the Subnet Proxy (ticks between updates) */
    public static int subnetProxyMinTickRate = 5;

    /** Maximum tick rate for the Subnet Proxy (ticks between updates when idle) */
    public static int subnetProxyMaxTickRate = 60;

    /** Essentia Creative Cell fix */
    public static boolean enableEssentiaCreativeCellFix = true;

    /**
     * Set of tile entity registry IDs (e.g. "thaumcraft:thaumatorium") that the
     * Essentia Interface should treat as non-interactable. Blacklisted TEs will
     * not be detected as adjacent essentia containers, preventing both push and
     * pull operations.
     */
    private static Set<String> essentiaContainerBlacklist = Collections.emptySet();

    /**
     * Check whether a tile entity registry ID is blacklisted from essentia
     * interface interaction.
     *
     * @param registryId The tile entity registry ID (e.g. "thaumcraft:thaumatorium")
     * @return true if the tile entity should be ignored by the essentia interface
     */
    public static boolean isEssentiaContainerBlacklisted(String registryId) {
        return essentiaContainerBlacklist.contains(registryId);
    }

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
        config.getCategory(CATEGORY_CELLS).setLanguageKey(Tags.MODID + ".config.category.cells");
        config.getCategory(CATEGORY_IDLE_DRAIN).setLanguageKey(Tags.MODID + ".config.category.idle_drain");
        config.getCategory(CATEGORY_ENABLED).setLanguageKey(Tags.MODID + ".config.category.enabled_cells");
        config.getCategory(CATEGORY_INTERFACES).setLanguageKey(Tags.MODID + ".config.category.interfaces");

        config.addCustomCategoryComment(CATEGORY_GENERAL, "General settings for cell behavior");
        config.addCustomCategoryComment(CATEGORY_CELLS, "Misc settings for cells.");
        config.addCustomCategoryComment(CATEGORY_IDLE_DRAIN,
            "Idle power drain settings (AE power per tick). Higher values = more power consumption.");
        config.addCustomCategoryComment(CATEGORY_ENABLED,
            "Enable or disable specific cell types. Disabled cells will not be registered.");
        config.addCustomCategoryComment(CATEGORY_INTERFACES,
            "Settings for resource interfaces (Fluid, Gas, Essentia, Item import/export interfaces).");


        // General category
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

        // General: configurable cell max types per channel
        p = config.get(CATEGORY_GENERAL,
            "configurableCellItemMaxTypes", 63,
            "Maximum item types for configurable item storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellItemMaxTypes");
        configurableCellItemMaxTypes = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "configurableCellFluidMaxTypes", 63,
            "Maximum fluid types for configurable fluid storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellFluidMaxTypes");
        configurableCellFluidMaxTypes = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "configurableCellEssentiaMaxTypes", 63,
            "Maximum essentia types for configurable essentia storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellEssentiaMaxTypes");
        configurableCellEssentiaMaxTypes = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "configurableCellGasMaxTypes", 63,
            "Maximum gas types for configurable gas storage cells (1-16384)", 1, 16384
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellGasMaxTypes");
        configurableCellGasMaxTypes = p.getInt();

        // Was for Cell Terminal, but seems Cell Workbench automatically adapts (lol)
        // 4 is the max we can show on 1 Terminal row safely, but can always accommodate more if needed

        // General: Upgrade slots per cell type
        p = config.get(CATEGORY_GENERAL,
            "compactingCellUpgradeSlots", 4,
            "Number of upgrade slots for compacting cells (1-16)", 1, 16
        );
        p.setLanguageKey(Tags.MODID + ".config.compactingCellUpgradeSlots");
        compactingCellUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "hdItemCellUpgradeSlots", 4,
            "Number of upgrade slots for hyper-density item cells (1-16)", 1, 16
        );
        p.setLanguageKey(Tags.MODID + ".config.hdItemCellUpgradeSlots");
        hdItemCellUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "hdCompactingCellUpgradeSlots", 4,
            "Number of upgrade slots for hyper-density compacting cells (1-16)", 1, 16
        );
        p.setLanguageKey(Tags.MODID + ".config.hdCompactingCellUpgradeSlots");
        hdCompactingCellUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "hdFluidCellUpgradeSlots", 4,
            "Number of upgrade slots for hyper-density fluid cells (1-16)", 1, 16
        );
        p.setLanguageKey(Tags.MODID + ".config.hdFluidCellUpgradeSlots");
        hdFluidCellUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "configurableCellUpgradeSlots", 4,
            "Number of upgrade slots for configurable cells (1-16)", 1, 16
        );
        p.setLanguageKey(Tags.MODID + ".config.configurableCellUpgradeSlots");
        configurableCellUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "emcCellSyncIntervalTicks", 20,
            "Server tick interval between EMC cell buffer flushes to player EMC (1-72000)", 1, 72000
        );
        p.setLanguageKey(Tags.MODID + ".config.emcCellSyncIntervalTicks");
        emcCellSyncIntervalTicks = p.getInt();

        // Use String to preserve values above Integer.MAX_VALUE in the Forge config GUI.
        p = config.get(CATEGORY_GENERAL,
            "emcCellReportedAmount", String.valueOf(DEFAULT_EMC_CELL_REPORTED_AMOUNT),
            "Reported stack size for learned EMC cell filters. Use a positive non-zero number up to Long.MAX_VALUE. Invalid values fall back to the default."
        );
        p.setLanguageKey(Tags.MODID + ".config.emcCellReportedAmount");
        String reportedAmountStr = p.getString();
        try {
            long parsed = Long.parseLong(reportedAmountStr);
            emcCellReportedAmount = parsed > 0 ? parsed : DEFAULT_EMC_CELL_REPORTED_AMOUNT;
        } catch (NumberFormatException e) {
            emcCellReportedAmount = DEFAULT_EMC_CELL_REPORTED_AMOUNT;
        }

        p = config.get(CATEGORY_GENERAL,
            "emcCellPartitionSlots", DEFAULT_EMC_CELL_PARTITION_SLOTS,
            "Unlocked partition slots for EMC cell tiers. Index 0 is the base cell with no upgrade installed. Each following entry unlocks slots for emc_upgrade_1, emc_upgrade_2, and so on."
        );
        p.setLanguageKey(Tags.MODID + ".config.emcCellPartitionSlots");
        emcCellPartitionSlots = sanitizeEmcCellPartitionSlots(p.getIntList());

        // General: NBT size warning threshold
        p = config.get(CATEGORY_GENERAL,
            "nbtSizeWarningThresholdKB", 100,
            "NBT size warning threshold in KB. Tooltip shows warning when cell NBT exceeds this.", 1, 10000
        );
        p.setLanguageKey(Tags.MODID + ".config.nbtSizeWarningThresholdKB");
        nbtSizeWarningThresholdKB = p.getInt();

        // General: Enable NBT size tooltip
        p = config.get(CATEGORY_GENERAL,
            "enableNbtSizeTooltip", true,
            "Enable NBT size computation and display in cell tooltips. Disable for performance."
        );
        p.setLanguageKey(Tags.MODID + ".config.enableNbtSizeTooltip");
        enableNbtSizeTooltip = p.getBoolean();

        // Cells category
        p = config.get(CATEGORY_CELLS,
            "enableEssentiaCreativeCellFix", true,
            "Enable the fix for the Essentia Creative Cell that makes it report only Max Int instead of Max Long / 2. This prevents deltas from overflowing and not reporting the right amounts. Disable this config if Thaumic Energistics support long in your version."
        );
        p.setLanguageKey(Tags.MODID + ".config.enableEssentiaCreativeCellFix");
        enableEssentiaCreativeCellFix = p.getBoolean();

        // Idle drain category
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

        // Interfaces category

        // Use String to handle Long.MAX_VALUE precisely (double loses precision above 2^53)
        p = config.get(CATEGORY_INTERFACES,
            "interfaceMaxSlotSizeLimit", String.valueOf(Long.MAX_VALUE),
            "Maximum slot size limit for interfaces. Caps the user-configurable max slot size per slot. Use -1 for unlimited (Long.MAX_VALUE)."
        );
        p.setLanguageKey(Tags.MODID + ".config.interfaceMaxSlotSizeLimit");
        String maxSlotStr = p.getString();
        try {
            long parsed = Long.parseLong(maxSlotStr);
            interfaceMaxSlotSizeLimit = parsed < 0 ? Long.MAX_VALUE : Math.max(1, parsed);
        } catch (NumberFormatException e) {
            interfaceMaxSlotSizeLimit = Long.MAX_VALUE;
        }

        p = config.get(CATEGORY_INTERFACES,
            "interfaceMinPollingRate", 0,
            "Minimum polling rate for interfaces in ticks. 0 allows adaptive (AE2-managed tick rates). " +
            "Higher values force interfaces to poll at least this often, reducing responsiveness but saving performance.", 0, Integer.MAX_VALUE
        );
        p.setLanguageKey(Tags.MODID + ".config.interfaceMinPollingRate");
        interfaceMinPollingRate = p.getInt();

        p = config.get(CATEGORY_INTERFACES,
            "useFixedInterfaceTextures", true,
            "Use fixed (non-animated) textures for interface blocks and parts. " +
            "Requires a game restart to take effect."
        );
        p.setLanguageKey(Tags.MODID + ".config.useFixedInterfaceTextures");
        useFixedInterfaceTextures = p.getBoolean();

        String[] blacklistEntries = config.getStringList(
            "essentiaContainerBlacklist", CATEGORY_INTERFACES,
            defaultEssentiaContainerBlacklist,
            "List of tile entity registry IDs that the Essentia Interface should ignore. " +
            "Blacklisted tile entities will not be detected as adjacent essentia containers, " +
            "preventing both push and pull operations. " +
            "Use F3 or a mod like WAILA/TOP to find the tile entity ID (e.g. \"thaumcraft:thaumatorium\")."
        );
        config.getCategory(CATEGORY_INTERFACES)
            .get("essentiaContainerBlacklist")
            .setLanguageKey(Tags.MODID + ".config.essentiaContainerBlacklist");
        Set<String> parsed = new HashSet<>();
        for (String entry : blacklistEntries) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) parsed.add(trimmed);
        }
        essentiaContainerBlacklist = parsed;

        // Subnet Proxy settings
        p = config.get(CATEGORY_GENERAL,
            "subnetProxyUpgradeSlots", 5,
            "Number of upgrade slots for the Subnet Proxy (1-24)", 1, 24
        );
        p.setLanguageKey(Tags.MODID + ".config.subnetProxyUpgradeSlots");
        subnetProxyUpgradeSlots = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "subnetProxyMinTickRate", 5,
            "Minimum tick rate for the Subnet Proxy in ticks (lower = more responsive, higher = less CPU). " +
            "This is the fastest the proxy will poll for changes.", 1, 200
        );
        p.setLanguageKey(Tags.MODID + ".config.subnetProxyMinTickRate");
        subnetProxyMinTickRate = p.getInt();

        p = config.get(CATEGORY_GENERAL,
            "subnetProxyMaxTickRate", 60,
            "Maximum tick rate for the Subnet Proxy in ticks (idle interval). " +
            "This is the slowest the proxy will poll when no changes are detected.", 1, 1200
        );
        p.setLanguageKey(Tags.MODID + ".config.subnetProxyMaxTickRate");
        subnetProxyMaxTickRate = p.getInt();

        // Hidden category: GUI preferences (not shown in config GUI)
        p = config.get(CATEGORY_HIDDEN, "showControlsHelp", false,
            "Whether the controls help panel is visible in Interface GUIs.");
        showControlsHelp = p.getBoolean();

        p = config.get(CATEGORY_HIDDEN, "jeiTransferInputsToExport", true,
            "Whether JEI recipe transfer sends recipe inputs to Export interfaces and outputs to Import interfaces, or the opposite.");
        jeiTransferInputsToExport = p.getBoolean();

        p = config.get(CATEGORY_HIDDEN, "jeiTransferOutputsToCreativeCell", jeiTransferInputsToExport,
            "Whether JEI recipe transfer adds recipe outputs to Creative Cell filters instead of recipe inputs.");
        jeiTransferOutputsToCreativeCell = p.getBoolean();

        // Save if config was created or changed
        if (config.hasChanged()) config.save();
    }

    /**
     * Persist the controls help panel visibility setting to the hidden config category.
     * Must be called from the client side only.
     *
     * @param value true to show the panel, false to hide it
     */
    public static void setShowControlsHelp(boolean value) {
        showControlsHelp = value;
        config.get(CATEGORY_HIDDEN, "showControlsHelp", false).set(value);
        config.save();
    }

    /**
     * Whether the given interface direction should receive JEI recipe inputs.
     * Export uses the shared preference directly and Import receives the opposite side.
     */
    public static boolean interfaceReceivesJeiInputs(boolean isExportInterface) {
        return isExportInterface ? jeiTransferInputsToExport : !jeiTransferInputsToExport;
    }

    /**
     * Whether Creative Cell filters should receive JEI recipe outputs.
     */
    public static boolean creativeCellReceivesJeiOutputs() {
        return jeiTransferOutputsToCreativeCell;
    }

    public static int getEmcCellUnlockedSlots(int tier) {
        if (emcCellPartitionSlots.length == 0) return DEFAULT_EMC_CELL_PARTITION_SLOTS[0];

        int clampedTier = Math.max(0, Math.min(tier, emcCellPartitionSlots.length - 1));
        return Math.max(1, emcCellPartitionSlots[clampedTier]);
    }

    public static int getEmcCellUpgradeTierCount() {
        return Math.max(0, emcCellPartitionSlots.length - 1);
    }

    public static int getEmcCellMaxPartitionSlots() {
        int maxSlots = 1;

        for (int slots : emcCellPartitionSlots) maxSlots = Math.max(maxSlots, slots);

        return maxSlots;
    }

    public static int[] getEmcCellPartitionSlots() {
        return emcCellPartitionSlots.clone();
    }

    private static int[] sanitizeEmcCellPartitionSlots(int[] configuredSlots) {
        if (configuredSlots == null || configuredSlots.length == 0) {
            return DEFAULT_EMC_CELL_PARTITION_SLOTS.clone();
        }

        int[] sanitizedSlots = new int[configuredSlots.length];

        for (int i = 0; i < configuredSlots.length; i++) {
            sanitizedSlots[i] = Math.max(1, configuredSlots[i]);
        }

        return sanitizedSlots;
    }

    /**
     * Persist the interface JEI routing preference to the hidden config category.
     * Must be called from the client side only.
     */
    public static void setJeiTransferInputsToExport(boolean value) {
        jeiTransferInputsToExport = value;
        config.get(CATEGORY_HIDDEN, "jeiTransferInputsToExport", true).set(value);
        config.save();
    }

    /**
     * Persist the Creative Cell JEI transfer preference to the hidden config category.
     * Must be called from the client side only.
     */
    public static void setJeiTransferOutputsToCreativeCell(boolean value) {
        jeiTransferOutputsToCreativeCell = value;
        config.get(CATEGORY_HIDDEN, "jeiTransferOutputsToCreativeCell", true).set(value);
        config.save();
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
