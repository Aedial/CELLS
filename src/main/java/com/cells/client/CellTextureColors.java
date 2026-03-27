package com.cells.client;


/**
 * Centralized color data for the modular cell texture system.
 * <p>
 * Cell textures are composed of 5 layers (tintindex 0-4):
 * <ul>
 *   <li>0: Frame (untinted)</li>
 *   <li>1: Outer highlights (tinted by tier)</li>
 *   <li>2: Inner highlights (tinted by tier)</li>
 *   <li>3: Inner shape (tinted by cell type, lower brightness)</li>
 *   <li>4: Outer shape (tinted by cell type, higher brightness)</li>
 * </ul>
 * <p>
 * Highlights use the tier's base color with specific brightness values.
 * Shapes use the cell type's base color with 2 brightness values (inner/outer).
 * <p>
 * All colors are pre-computed at class load time for performance.
 * Tier indices: 0=1k, 1=4k, 2=16k, 3=64k, 4=256k, 5=1m, 6=4m, 7=16m, 8=64m, 9=256m, 10=1g, 11=2g
 */
public final class CellTextureColors {

    private CellTextureColors() {}

    /**
     * Number of tiers supported (1k through 2g).
     */
    public static final int TIER_COUNT = 12;

    /**
     * Tier names indexed by tier index.
     * Used for reverse lookup from tier name to index.
     */
    private static final String[] TIER_NAMES = {
        "1k", "4k", "16k", "64k", "256k", "1m",
        "4m", "16m", "64m", "256m", "1g", "2g"
    };

    /**
     * Convert a tier name to its tier index.
     *
     * @param tierName The tier name (e.g., "1k", "64k", "1m")
     * @return The tier index (0-11), or -1 if not recognized (custom tier)
     */
    public static int getTierIndex(String tierName) {
        if (tierName == null) return -1;

        for (int i = 0; i < TIER_NAMES.length; i++) {
            if (TIER_NAMES[i].equalsIgnoreCase(tierName)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Check if a tier name is a known built-in tier.
     *
     * @param tierName The tier name to check
     * @return true if the tier is known (1k-2g), false if custom
     */
    public static boolean isKnownTier(String tierName) {
        return getTierIndex(tierName) >= 0;
    }

    // =====================
    // Tier Highlight Colors (pre-computed)
    // =====================

    /**
     * Pre-computed inner highlight colors for each tier (indexed 0-11).
     */
    private static final int[] INNER_HIGHLIGHT_COLORS = new int[TIER_COUNT];

    /**
     * Pre-computed outer highlight colors for each tier (indexed 0-11).
     */
    private static final int[] OUTER_HIGHLIGHT_COLORS = new int[TIER_COUNT];

    static {
        // Format: tier index -> base color, inner brightness %, outer brightness %
        // Tier 0 (1k) = #FF0000 -> 39% + 48%
        // Tier 1 (4k) = #FF6F00 -> 38% + 54%
        // Tier 2 (16k) = #FFF600 -> 48% + 62%
        // Tier 3 (64k) = #15FF00 -> 47% + 65%
        // Tier 4 (256k) = #005EFF -> 46% + 72%
        // Tier 5 (1m) = #B700FF -> 46% + 71%
        // Tier 6 (4m) = #FF0000 -> 54% + 77%
        // Tier 7 (16m) = #FF6F00 -> 68% + 86%
        // Tier 8 (64m) = #FFF600 -> 63% + 86%
        // Tier 9 (256m) = #15FF00 -> 73% + 95%
        // Tier 10 (1g) = #FF00BB -> 57% + 81%
        // Tier 11 (2g) = #005EFF -> 73% + 95%

        initHighlightColor(0,  0xFF0000, 0.39f, 0.48f);
        initHighlightColor(1,  0xFF6F00, 0.38f, 0.54f);
        initHighlightColor(2,  0xFFF600, 0.48f, 0.62f);
        initHighlightColor(3,  0x15FF00, 0.47f, 0.65f);
        initHighlightColor(4,  0x005EFF, 0.46f, 0.72f);
        initHighlightColor(5,  0xB700FF, 0.46f, 0.71f);
        initHighlightColor(6,  0xFF0000, 0.54f, 0.77f);
        initHighlightColor(7,  0xFF6F00, 0.68f, 0.86f);
        initHighlightColor(8,  0xFFF600, 0.63f, 0.86f);
        initHighlightColor(9,  0x15FF00, 0.73f, 0.95f);
        initHighlightColor(10, 0xFF00BB, 0.57f, 0.81f);
        initHighlightColor(11, 0x005EFF, 0.73f, 0.95f);
    }

    private static void initHighlightColor(int tier, int baseColor, float innerBrightness, float outerBrightness) {
        INNER_HIGHLIGHT_COLORS[tier] = applyBrightness(baseColor, innerBrightness);
        OUTER_HIGHLIGHT_COLORS[tier] = applyBrightness(baseColor, outerBrightness);
    }

    // =====================
    // Cell Type Shape Colors
    // =====================

    /**
     * Enumeration of cell types for shape coloring.
     * Each type has a base color and two brightness levels for inner/outer shapes.
     */
    public enum CellType {
        CONFIGURABLE_ITEM(0x00D0FF),
        CONFIGURABLE_FLUID(0x00FF5A),
        CONFIGURABLE_GAS(0xEBFF0A),
        CONFIGURABLE_ESSENTIA(0xEA00FF),
        COMPACTING(0x5050FF),
        HYPER_DENSITY_ITEM(0x00FFFF),
        HYPER_DENSITY_FLUID(0xFF00FF),
        HYPER_DENSITY_COMPACTING(0x5050FF);

        // Pre-computed colors for each brightness cycle (0=low, 1=medium, 2=high)
        private final int[] outerColors;  // outer shape (darker)
        private final int[] innerColors;  // inner shape (lighter)

        CellType(int baseColor) {
            // Brightness levels for each cycle
            // Cycle 0: outer=44%, inner=72%
            // Cycle 1: outer=72%, inner=100%
            this.outerColors = new int[] {
                applyBrightness(baseColor, 0.44f),  // cycle 0
                applyBrightness(baseColor, 0.72f),  // cycle 1
            };
            this.innerColors = new int[] {
                applyBrightness(baseColor, 0.72f),  // cycle 0
                applyBrightness(baseColor, 1.00f),  // cycle 1
            };
        }

        /**
         * Get the brightness cycle for a tier (0 or 1).
         * Each cycle spans 6 tiers: shapes 0-5.
         * Tiers 0-5 = cycle 0, tiers 6-11 = cycle 1, etc.
         */
        private static int getBrightnessCycle(int tier) {
            return Math.min(tier / 6, 1);
        }

        public int getInnerColor(int tier) {
            return innerColors[getBrightnessCycle(tier)];
        }

        public int getOuterColor(int tier) {
            return outerColors[getBrightnessCycle(tier)];
        }
    }

    // =====================
    // Component Tint Colors (for wave layer)
    // =====================

    /**
     * Component wave base color for Hyper Density Item type.
     * Pure cyan, used to tint the grayscale wave texture.
     */
    public static final int HYPER_DENSITY_ITEM_WAVE_COLOR = 0x00FFFF;

    /**
     * Component wave base color for Hyper Density Fluid type.
     * Pure magenta, used to tint the grayscale wave texture.
     */
    public static final int HYPER_DENSITY_FLUID_WAVE_COLOR = 0xFF00FF;

    /**
     * Component wave base color for Hyper Density Compacting type.
     * Purple-blue, used to tint the grayscale wave texture.
     */
    public static final int HYPER_DENSITY_COMPACTING_WAVE_COLOR = 0x5050FF;

    /**
     * Component wave base color for Compacting type.
     * Purple-blue, used to tint the grayscale wave texture.
     */
    public static final int COMPACTING_WAVE_COLOR = 0x5050FF;

    /**
     * Enumeration of component types for wave layer coloring.
     * Each type has a base color used to tint the grayscale wave texture.
     */
    public enum ComponentType {
        HYPER_DENSITY_ITEM(HYPER_DENSITY_ITEM_WAVE_COLOR),
        HYPER_DENSITY_FLUID(HYPER_DENSITY_FLUID_WAVE_COLOR),
        HYPER_DENSITY_COMPACTING(HYPER_DENSITY_COMPACTING_WAVE_COLOR),
        COMPACTING(COMPACTING_WAVE_COLOR);

        private final int waveColor;

        ComponentType(int waveColor) {
            this.waveColor = waveColor;
        }

        /**
         * Get the wave tint color for this component type.
         * This color is applied to the grayscale wave layer.
         */
        public int getWaveColor() {
            return waveColor | 0xFF000000;  // Ensure full alpha
        }
    }

    /**
     * Get the component wave tint color for a component type.
     *
     * @param componentType The component type
     * @return Tint color for the component wave layer
     */
    public static int getComponentWaveColor(ComponentType componentType) {
        return componentType.getWaveColor();
    }

    // =====================
    // Shape Index
    // =====================

    /**
     * Get the shape texture index for a tier (0-5).
     * Shape textures cycle every 6 tiers, offset by 1 so that:
     * - 1k (tier 0) uses shape 0
     * - 4k (tier 1) uses shape 1
     * - ...
     * - 1m (tier 5) uses shape 5
     * - 4m (tier 6) uses shape 0 (wraps)
     * - ...
     * - 2g (tier 11) uses shape 5
     *
     * @param tier Tier index (0-11)
     * @return Shape index (0-5)
     */
    public static int getShapeIndex(int tier) {
        return tier % 6;
    }

    // =====================
    // Frame Types
    // =====================

    /**
     * Enumeration of frame types.
     */
    public enum FrameType {
        NORMAL("frame_normal"),
        HYPER_DENSITY("frame_hyper_density");

        private final String textureName;

        FrameType(String textureName) {
            this.textureName = textureName;
        }

        public String getTextureName() {
            return textureName;
        }
    }

    // =====================
    // Utility Methods
    // =====================

    /**
     * Apply a brightness multiplier to an RGB color.
     * The result includes full alpha (0xFF in the high byte).
     */
    private static int applyBrightness(int rgb, float brightness) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = Math.min(255, Math.round(r * brightness));
        g = Math.min(255, Math.round(g * brightness));
        b = Math.min(255, Math.round(b * brightness));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // =====================
    // Color Provider for IItemColor
    // =====================

    /**
     * Layer indices for the cell model.
     */
    public static final int LAYER_FRAME = 0;
    public static final int LAYER_OUTER_HIGHLIGHTS = 1;
    public static final int LAYER_INNER_HIGHLIGHTS = 2;
    public static final int LAYER_INNER_SHAPE = 3;
    public static final int LAYER_OUTER_SHAPE = 4;

    /**
     * Default color (white, no tint) - used for the frame layer.
     */
    public static final int NO_TINT = 0xFFFFFFFF;

    /**
     * Get the color for a specific layer of a cell.
     *
     * @param cellType The cell type
     * @param tier The tier index (0-11), or -1 for custom tiers (defaults to tier 0 colors)
     * @param tintIndex The layer index (0-4)
     * @return The color for that layer
     */
    public static int getColorForLayer(CellType cellType, int tier, int tintIndex) {
        // Clamp tier to valid range (custom tiers use tier 0 colors as fallback)
        if (tier < 0 || tier >= TIER_COUNT) tier = 0;

        switch (tintIndex) {
            case LAYER_FRAME:
                return NO_TINT;

            case LAYER_OUTER_HIGHLIGHTS:
                return OUTER_HIGHLIGHT_COLORS[tier];

            case LAYER_INNER_HIGHLIGHTS:
                return INNER_HIGHLIGHT_COLORS[tier];

            case LAYER_INNER_SHAPE:
                return cellType.getInnerColor(tier);

            case LAYER_OUTER_SHAPE:
                return cellType.getOuterColor(tier);

            default:
                return NO_TINT;
        }
    }
}
