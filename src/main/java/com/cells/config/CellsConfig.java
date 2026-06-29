package com.cells.config;

import java.io.File;
import java.util.Arrays;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
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
@Mod.EventBusSubscriber(modid = Tags.MODID)
@Config(modid = Tags.MODID, name = Tags.MODID, category = "")
@Config.LangKey(Tags.MODID + ".config.title")
public final class CellsConfig {

    private static final int[] DEFAULT_EMC_CELL_PARTITION_SLOTS = new int[] {1, 9, 54};
    private static final long DEFAULT_EMC_CELL_REPORTED_AMOUNT = Integer.MAX_VALUE;

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_CELLS = "cells";
    private static final String CATEGORY_IDLE_DRAIN = "idle_drain";
    private static final String CATEGORY_ENABLED = "enabled_cells";
    private static final String CATEGORY_INTERFACES = "interfaces";
    private static final String CATEGORY_HIDDEN = "hidden";

    // Mithminite Jar is blacklisted by default because it crashes if overfilled more than 250 (bug in the base mod)
    private static final String[] DEFAULT_ESSENTIA_CONTAINER_BLACKLIST = new String[] {
        "thaumadditions:jar_mithminite"
    };

    @Config.Name(CATEGORY_GENERAL)
    @Config.LangKey(Tags.MODID + ".config.category.general")
    @Config.Comment("General settings for cell behavior")
    public static GeneralCategory general = new GeneralCategory();

    @Config.Name(CATEGORY_CELLS)
    @Config.LangKey(Tags.MODID + ".config.category.cells")
    @Config.Comment("Misc settings for cells.")
    public static CellsCategory cells = new CellsCategory();

    @Config.Name(CATEGORY_IDLE_DRAIN)
    @Config.LangKey(Tags.MODID + ".config.category.idle_drain")
    @Config.Comment("Idle power drain settings (AE power per tick). Higher values = more power consumption.")
    public static IdleDrainCategory idleDrain = new IdleDrainCategory();

    @Config.Name(CATEGORY_ENABLED)
    @Config.LangKey(Tags.MODID + ".config.category.enabled_cells")
    @Config.Comment("Enable or disable specific cell types. Disabled cells will not be registered.")
    public static EnabledCellsCategory enabledCells = new EnabledCellsCategory();

    @Config.Name(CATEGORY_INTERFACES)
    @Config.LangKey(Tags.MODID + ".config.category.interfaces")
    @Config.Comment("Settings for resource interfaces (Fluid, Gas, Essentia, Item import/export interfaces).")
    public static InterfacesCategory interfaces = new InterfacesCategory();

    @Config.Name(CATEGORY_HIDDEN)
    @Config.Comment("Hidden client preferences.")
    public static HiddenCategory hidden = new HiddenCategory();

    // Forge 1.12 does not support long-backed @Config fields, so these two values
    // are normalized once during sync and then served from cached parsed values.
    private static long emcCellReportedAmountValue = DEFAULT_EMC_CELL_REPORTED_AMOUNT;
    private static long interfaceMaxSlotSizeLimitValue = Long.MAX_VALUE;

    private CellsConfig() {
    }

    public static boolean isHiddenCategory(String categoryName) {
        return CATEGORY_HIDDEN.equals(categoryName);
    }

    /**
     * Check whether a tile entity registry ID is blacklisted from essentia
     * interface interaction.
     *
     * @param registryId The tile entity registry ID (e.g. "thaumcraft:thaumatorium")
     * @return true if the tile entity should be ignored by the essentia interface
     */
    public static boolean isEssentiaContainerBlacklisted(String registryId) {
        if (registryId == null) return false;

        for (String entry : interfaces.essentiaContainerBlacklist) {
            if (entry == null) continue;

            if (registryId.equals(entry.trim())) return true;
        }

        return false;
    }

    /**
     * Syncs the annotated config.
     *
     * @param configFile The configuration file
     */
    public static void init(File configFile) {
        syncConfig();
    }

    private static void syncConfig() {
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);

        NormalizedLongConfigValues normalizedLongValues = normalizeConfiguredValues();

        emcCellReportedAmountValue = normalizedLongValues.emcCellReportedAmount;
        interfaceMaxSlotSizeLimitValue = normalizedLongValues.interfaceMaxSlotSizeLimit;

        if (normalizedLongValues.changed) {
            ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
        }
    }

    private static NormalizedLongConfigValues normalizeConfiguredValues() {
        boolean changed = false;

        NormalizedLongValue normalizedReportedAmount = normalizeReportedAmount(general.emcCellReportedAmount);
        if (!normalizedReportedAmount.normalizedValue.equals(general.emcCellReportedAmount)) {
            general.emcCellReportedAmount = normalizedReportedAmount.normalizedValue;
            changed = true;
        }

        NormalizedLongValue normalizedInterfaceLimit = normalizeInterfaceMaxSlotSizeLimit(interfaces.interfaceMaxSlotSizeLimit);
        if (!normalizedInterfaceLimit.normalizedValue.equals(interfaces.interfaceMaxSlotSizeLimit)) {
            interfaces.interfaceMaxSlotSizeLimit = normalizedInterfaceLimit.normalizedValue;
            changed = true;
        }

        int[] sanitizedPartitionSlots = sanitizeEmcCellPartitionSlots(general.emcCellPartitionSlots);
        if (!Arrays.equals(sanitizedPartitionSlots, general.emcCellPartitionSlots)) {
            general.emcCellPartitionSlots = sanitizedPartitionSlots;
            changed = true;
        }

        return new NormalizedLongConfigValues(
            changed,
            normalizedReportedAmount.parsedValue,
            normalizedInterfaceLimit.parsedValue
        );
    }

    public static long getEmcCellReportedAmount() {
        return emcCellReportedAmountValue;
    }

    private static NormalizedLongValue normalizeReportedAmount(String rawValue) {
        String trimmedValue = rawValue == null ? "" : rawValue.trim();

        try {
            long parsedValue = Long.parseLong(trimmedValue);
            if (parsedValue > 0) {
                return new NormalizedLongValue(Long.toString(parsedValue), parsedValue);
            }
        } catch (NumberFormatException ignored) {
        }

        return new NormalizedLongValue(
            String.valueOf(DEFAULT_EMC_CELL_REPORTED_AMOUNT),
            DEFAULT_EMC_CELL_REPORTED_AMOUNT
        );
    }

    public static long getInterfaceMaxSlotSizeLimit() {
        return interfaceMaxSlotSizeLimitValue;
    }

    private static NormalizedLongValue normalizeInterfaceMaxSlotSizeLimit(String rawValue) {
        String trimmedValue = rawValue == null ? "" : rawValue.trim();

        try {
            long parsedValue = Long.parseLong(trimmedValue);
            if (parsedValue < 0) return new NormalizedLongValue("-1", Long.MAX_VALUE);


            long normalizedValue = Math.max(1, parsedValue);
            return new NormalizedLongValue(Long.toString(normalizedValue), normalizedValue);
        } catch (NumberFormatException ignored) {
        }

        return new NormalizedLongValue(String.valueOf(Long.MAX_VALUE), Long.MAX_VALUE);
    }

    /**
     * Persist the controls help panel visibility setting to the hidden config category.
     * Must be called from the client side only.
     *
     * @param value true to show the panel, false to hide it
     */
    public static void setShowControlsHelp(boolean value) {
        hidden.showControlsHelp = value;
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
    }

    /**
     * Whether the given interface direction should receive JEI recipe inputs.
     * Export uses the shared preference directly and Import receives the opposite side.
     */
    public static boolean interfaceReceivesJeiInputs(boolean isExportInterface) {
        return isExportInterface ? hidden.jeiTransferInputsToExport : !hidden.jeiTransferInputsToExport;
    }

    /**
     * Whether Creative Cell filters should receive JEI recipe outputs.
     */
    public static boolean creativeCellReceivesJeiOutputs() {
        return hidden.jeiTransferOutputsToCreativeCell;
    }

    public static int getEmcCellUnlockedSlots(int tier) {
        int[] configuredSlots = sanitizeEmcCellPartitionSlots(general.emcCellPartitionSlots);
        if (configuredSlots.length == 0) return DEFAULT_EMC_CELL_PARTITION_SLOTS[0];

        int clampedTier = Math.max(0, Math.min(tier, configuredSlots.length - 1));
        return Math.max(1, configuredSlots[clampedTier]);
    }

    public static int getEmcCellUpgradeTierCount() {
        return Math.max(0, sanitizeEmcCellPartitionSlots(general.emcCellPartitionSlots).length - 1);
    }

    public static int getEmcCellMaxPartitionSlots() {
        int maxSlots = 1;

        for (int slots : sanitizeEmcCellPartitionSlots(general.emcCellPartitionSlots)) {
            maxSlots = Math.max(maxSlots, slots);
        }

        return maxSlots;
    }

    public static int[] getEmcCellPartitionSlots() {
        return sanitizeEmcCellPartitionSlots(general.emcCellPartitionSlots);
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
        hidden.jeiTransferInputsToExport = value;
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
    }

    /**
     * Persist the Creative Cell JEI transfer preference to the hidden config category.
     * Must be called from the client side only.
     */
    public static void setJeiTransferOutputsToCreativeCell(boolean value) {
        hidden.jeiTransferOutputsToCreativeCell = value;
        ConfigManager.sync(Tags.MODID, Config.Type.INSTANCE);
    }

    /**
     * Event handler for config changes from the GUI.
     *
     * @param event The config changed event
     */
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Tags.MODID.equals(event.getModID())) return;

        syncConfig();
    }

    public static class GeneralCategory {

        @Config.LangKey(Tags.MODID + ".config.hdItemMaxTypes")
        @Config.Comment("Maximum item types for hyper-density item storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int hdItemMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.hdFluidMaxTypes")
        @Config.Comment("Maximum item types for hyper-density fluid storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int hdFluidMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.configurableCellItemMaxTypes")
        @Config.Comment("Maximum item types for configurable item storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int configurableCellItemMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.configurableCellFluidMaxTypes")
        @Config.Comment("Maximum fluid types for configurable fluid storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int configurableCellFluidMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.configurableCellEssentiaMaxTypes")
        @Config.Comment("Maximum essentia types for configurable essentia storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int configurableCellEssentiaMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.configurableCellGasMaxTypes")
        @Config.Comment("Maximum gas types for configurable gas storage cells (1-16384)")
        @Config.RangeInt(min = 1, max = 16384)
        public int configurableCellGasMaxTypes = 63;

        @Config.LangKey(Tags.MODID + ".config.compactingCellUpgradeSlots")
        @Config.Comment("Number of upgrade slots for compacting cells (1-16)")
        @Config.RangeInt(min = 1, max = 16)
        public int compactingCellUpgradeSlots = 4;

        @Config.LangKey(Tags.MODID + ".config.hdItemCellUpgradeSlots")
        @Config.Comment("Number of upgrade slots for hyper-density item cells (1-16)")
        @Config.RangeInt(min = 1, max = 16)
        public int hdItemCellUpgradeSlots = 4;

        @Config.LangKey(Tags.MODID + ".config.hdCompactingCellUpgradeSlots")
        @Config.Comment("Number of upgrade slots for hyper-density compacting cells (1-16)")
        @Config.RangeInt(min = 1, max = 16)
        public int hdCompactingCellUpgradeSlots = 4;

        @Config.LangKey(Tags.MODID + ".config.hdFluidCellUpgradeSlots")
        @Config.Comment("Number of upgrade slots for hyper-density fluid cells (1-16)")
        @Config.RangeInt(min = 1, max = 16)
        public int hdFluidCellUpgradeSlots = 4;

        @Config.LangKey(Tags.MODID + ".config.configurableCellUpgradeSlots")
        @Config.Comment("Number of upgrade slots for configurable cells (1-16)")
        @Config.RangeInt(min = 1, max = 16)
        public int configurableCellUpgradeSlots = 4;

        @Config.LangKey(Tags.MODID + ".config.emcCellSyncIntervalTicks")
        @Config.Comment("Server tick interval between EMC cell buffer flushes to player EMC (1-72000)")
        @Config.RangeInt(min = 1, max = 72000)
        public int emcCellSyncIntervalTicks = 20;

        // Forge's 1.12 annotation config has no long adapter, so this stays string-backed
        // and is normalized during config sync before being cached as a parsed long.
        @Config.LangKey(Tags.MODID + ".config.emcCellReportedAmount")
        @Config.Comment("Reported stack size for learned EMC cell filters. Use a positive non-zero number up to Long.MAX_VALUE. Invalid values fall back to the default.")
        public String emcCellReportedAmount = String.valueOf(DEFAULT_EMC_CELL_REPORTED_AMOUNT);

        @Config.LangKey(Tags.MODID + ".config.emcCellPartitionSlots")
        @Config.Comment("Unlocked partition slots for EMC cell tiers. Index 0 is the base cell with no upgrade installed. Each following entry unlocks slots for emc_upgrade_1, emc_upgrade_2, and so on.")
        public int[] emcCellPartitionSlots = DEFAULT_EMC_CELL_PARTITION_SLOTS.clone();

        @Config.LangKey(Tags.MODID + ".config.nbtSizeWarningThresholdKB")
        @Config.Comment("NBT size warning threshold in KB. Tooltip shows warning when cell NBT exceeds this.")
        @Config.RangeInt(min = 1, max = 10000)
        public int nbtSizeWarningThresholdKB = 100;

        @Config.LangKey(Tags.MODID + ".config.enableNbtSizeTooltip")
        @Config.Comment("Enable NBT size computation and display in cell tooltips. Disable for performance.")
        public boolean enableNbtSizeTooltip = true;

        @Config.LangKey(Tags.MODID + ".config.subnetProxyUpgradeSlots")
        @Config.Comment("Number of upgrade slots for the Subnet Proxy (1-24)")
        @Config.RangeInt(min = 1, max = 24)
        public int subnetProxyUpgradeSlots = 5;

        @Config.LangKey(Tags.MODID + ".config.subnetProxyMinTickRate")
        @Config.Comment("Minimum tick rate for the Subnet Proxy in ticks (lower = more responsive, higher = less CPU). This is the fastest the proxy will poll for changes.")
        @Config.RangeInt(min = 1, max = 200)
        public int subnetProxyMinTickRate = 5;

        @Config.LangKey(Tags.MODID + ".config.subnetProxyMaxTickRate")
        @Config.Comment("Maximum tick rate for the Subnet Proxy in ticks (idle interval). This is the slowest the proxy will poll when no changes are detected.")
        @Config.RangeInt(min = 1, max = 1200)
        public int subnetProxyMaxTickRate = 60;

        @Config.LangKey(Tags.MODID + ".config.subnetProxyReportExtractionFaults")
        @Config.Comment("Enable Subnet Proxy extraction-fault reporting and warning logs. A reported fault can originate from the proxy, a connected inventory, or the network itself.")
        public boolean subnetProxyReportExtractionFaults = false;
    }

    public static class CellsCategory {

        @Config.LangKey(Tags.MODID + ".config.enableEssentiaCreativeCellFix")
        @Config.Comment("Enable the fix for the Essentia Creative Cell that makes it report only Max Int instead of Max Long / 2. This prevents deltas from overflowing and not reporting the right amounts. Disable this config if Thaumic Energistics support long in your version.")
        public boolean enableEssentiaCreativeCellFix = true;
    }

    public static class IdleDrainCategory {

        @Config.LangKey(Tags.MODID + ".config.compactingIdleDrain")
        @Config.Comment("Idle drain for compacting cells")
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double compactingIdleDrain = 6.0D;

        @Config.LangKey(Tags.MODID + ".config.hdIdleDrain")
        @Config.Comment("Idle drain for hyper-density cells")
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double hdIdleDrain = 10.0D;

        @Config.LangKey(Tags.MODID + ".config.hdCompactingIdleDrain")
        @Config.Comment("Idle drain for hyper-density compacting cells")
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double hdCompactingIdleDrain = 20.0D;

        @Config.LangKey(Tags.MODID + ".config.fluidHdIdleDrain")
        @Config.Comment("Idle drain for fluid hyper-density cells")
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double fluidHdIdleDrain = 10.0D;

        @Config.LangKey(Tags.MODID + ".config.configurableCellIdleDrain")
        @Config.Comment("Idle drain for configurable cells")
        @Config.RangeDouble(min = 0.0D, max = 100.0D)
        public double configurableCellIdleDrain = 3.0D;
    }

    public static class EnabledCellsCategory {

        @Config.LangKey(Tags.MODID + ".config.enableCompactingCells")
        @Config.Comment("Enable compacting storage cells")
        public boolean enableCompactingCells = true;

        @Config.LangKey(Tags.MODID + ".config.enableHDCells")
        @Config.Comment("Enable hyper-density storage cells")
        public boolean enableHDCells = true;

        @Config.LangKey(Tags.MODID + ".config.enableHDCompactingCells")
        @Config.Comment("Enable hyper-density compacting storage cells")
        public boolean enableHDCompactingCells = true;

        @Config.LangKey(Tags.MODID + ".config.enableFluidHDCells")
        @Config.Comment("Enable fluid hyper-density storage cells")
        public boolean enableFluidHDCells = true;

        @Config.LangKey(Tags.MODID + ".config.enableConfigurableCells")
        @Config.Comment("Enable configurable storage cells")
        public boolean enableConfigurableCells = true;
    }

    public static class InterfacesCategory {

        // Forge's 1.12 annotation config has no long adapter, so this stays string-backed
        // and is normalized during config sync before being cached as a parsed long.
        @Config.LangKey(Tags.MODID + ".config.interfaceMaxSlotSizeLimit")
        @Config.Comment("Maximum slot size limit for interfaces. Caps the user-configurable max slot size per slot. Use -1 for unlimited (Long.MAX_VALUE).")
        public String interfaceMaxSlotSizeLimit = String.valueOf(Long.MAX_VALUE);

        @Config.LangKey(Tags.MODID + ".config.interfaceMinPollingRate")
        @Config.Comment("Minimum polling rate for interfaces in ticks. 0 allows adaptive (AE2-managed tick rates). Higher values force interfaces to poll at least this often, reducing responsiveness but saving performance.")
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int interfaceMinPollingRate = 0;

        @Config.LangKey(Tags.MODID + ".config.useFixedInterfaceTextures")
        @Config.Comment("Use fixed (non-animated) textures for interface blocks and parts. Requires a game restart to take effect.")
        @Config.RequiresMcRestart
        public boolean useFixedInterfaceTextures = true;

        @Config.LangKey(Tags.MODID + ".config.essentiaContainerBlacklist")
        @Config.Comment("List of tile entity registry IDs that the Essentia Interface should ignore. Blacklisted tile entities will not be detected as adjacent essentia containers, preventing both push and pull operations. Use F3 or a mod like WAILA/TOP to find the tile entity ID (e.g. \"thaumcraft:thaumatorium\").")
        public String[] essentiaContainerBlacklist = DEFAULT_ESSENTIA_CONTAINER_BLACKLIST.clone();
    }

    public static class HiddenCategory {

        public boolean showControlsHelp = false;
        public boolean jeiTransferInputsToExport = true;
        public boolean jeiTransferOutputsToCreativeCell = true;
    }

    private static class NormalizedLongValue {

        private final String normalizedValue;
        private final long parsedValue;

        private NormalizedLongValue(String normalizedValue, long parsedValue) {
            this.normalizedValue = normalizedValue;
            this.parsedValue = parsedValue;
        }
    }

    private static class NormalizedLongConfigValues {

        private final boolean changed;
        private final long emcCellReportedAmount;
        private final long interfaceMaxSlotSizeLimit;

        private NormalizedLongConfigValues(boolean changed, long emcCellReportedAmount, long interfaceMaxSlotSizeLimit) {
            this.changed = changed;
            this.emcCellReportedAmount = emcCellReportedAmount;
            this.interfaceMaxSlotSizeLimit = interfaceMaxSlotSizeLimit;
        }
    }
}