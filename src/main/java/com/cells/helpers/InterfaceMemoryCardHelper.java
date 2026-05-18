package com.cells.helpers;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;


/**
 * Adapts CELLS memory card payloads across compatible interface variants.
 * <p>
 * Standard AE2 memory cards key compatibility off the block or part translation key,
 * which prevents settings transfer between CELLS interfaces that represent the same
 * logical endpoint but use different IDs.
 * <p>
 * The NBT payload is already mostly compatible between standalone and combined
 * interfaces because both use the same type-prefixed keys. I/O interfaces are the
 * exception: they namespace every key by direction, so compatible cards need a small
 * key remap when crossing the I/O boundary.
 */
public final class InterfaceMemoryCardHelper {

    private static final String CELLS_TILE_PREFIX = "tile.cells.";
    private static final String IMPORT_INTERFACE_PREFIX = CELLS_TILE_PREFIX + "import_interface";
    private static final String EXPORT_INTERFACE_PREFIX = CELLS_TILE_PREFIX + "export_interface";
    private static final String IO_INTERFACE_PREFIX = CELLS_TILE_PREFIX + "io_interface";

    private InterfaceMemoryCardHelper() {
    }

    public static TargetProfile simple(boolean export, String typeName) {
        return new TargetProfile(InterfaceKind.SIMPLE, export ? Direction.EXPORT : Direction.IMPORT,
            normalizeType(typeName));
    }

    public static TargetProfile combined(boolean export) {
        return new TargetProfile(InterfaceKind.COMBINED, export ? Direction.EXPORT : Direction.IMPORT, null);
    }

    public static TargetProfile io(String typeName) {
        return new TargetProfile(InterfaceKind.IO, null, normalizeType(typeName));
    }

    /**
     * Prepare a memory card payload for the requested CELLS interface target.
     * Returns null when the stored card data is not compatible with that target.
     */
    @Nullable
    public static NBTTagCompound prepareUploadData(String storedName, NBTTagCompound sourceData, TargetProfile target) {
        CardProfile source = parseStoredName(storedName);
        if (source == null) return null;
        if (!isCompatible(source, target)) return null;

        if (source.kind != InterfaceKind.IO && target.kind != InterfaceKind.IO) {
            return sourceData;
        }

        return remapIoKeys(source, sourceData, target);
    }

    @Nullable
    private static CardProfile parseStoredName(String storedName) {
        CardProfile importProfile = parseDirectional(storedName, IMPORT_INTERFACE_PREFIX, Direction.IMPORT);
        if (importProfile != null) return importProfile;

        CardProfile exportProfile = parseDirectional(storedName, EXPORT_INTERFACE_PREFIX, Direction.EXPORT);
        if (exportProfile != null) return exportProfile;

        return parseIo(storedName);
    }

    @Nullable
    private static CardProfile parseDirectional(String storedName, String prefix, Direction direction) {
        if (!storedName.startsWith(prefix)) return null;

        String suffix = storedName.substring(prefix.length());
        if (suffix.isEmpty()) return new CardProfile(InterfaceKind.SIMPLE, direction, "item");
        if (!suffix.startsWith(".")) return null;

        String typeName = normalizeType(suffix.substring(1));
        if (typeName == null) return null;

        if ("combined".equals(typeName)) {
            return new CardProfile(InterfaceKind.COMBINED, direction, null);
        }

        if (!isSupportedType(typeName)) return null;

        return new CardProfile(InterfaceKind.SIMPLE, direction, typeName);
    }

    @Nullable
    private static CardProfile parseIo(String storedName) {
        if (!storedName.startsWith(IO_INTERFACE_PREFIX)) return null;

        String suffix = storedName.substring(IO_INTERFACE_PREFIX.length());
        if (suffix.isEmpty()) return new CardProfile(InterfaceKind.IO, null, "item");
        if (!suffix.startsWith(".")) return null;

        String typeName = normalizeType(suffix.substring(1));
        if (!isSupportedType(typeName)) return null;

        return new CardProfile(InterfaceKind.IO, null, typeName);
    }

    private static boolean isCompatible(CardProfile source, TargetProfile target) {
        if (target.direction != null && source.direction != null && source.direction != target.direction) {
            return false;
        }

        return target.typeName == null || source.typeName == null || target.typeName.equals(source.typeName);
    }

    /**
     * Remap between standalone/combined keys and I/O keys.
     * <p>
     * Standalone and combined interfaces use keys like itemMaxSlotSize and itemFilters.
     * I/O interfaces use import_itemMaxSlotSize or export_itemFilters. The payload is
     * copied and only the needed aliases are added so existing cards keep working.
     */
    private static NBTTagCompound remapIoKeys(CardProfile source, NBTTagCompound sourceData, TargetProfile target) {
        NBTTagCompound remapped = sourceData.copy();
        String typeName = resolveTypeName(source, target);
        if (typeName == null) return remapped;

        if (target.kind == InterfaceKind.IO) {
            Direction sourceDirection = source.direction;
            if (sourceDirection == null) return remapped;

            String directionPrefix = sourceDirection.getNbtPrefix();

            copyFirstPresent(sourceData, remapped, directionPrefix + typeName + "MaxSlotSize",
                typeName + "MaxSlotSize", "maxSlotSize");
            copyFirstPresent(sourceData, remapped, directionPrefix + typeName + "MaxSlotSizeOverrides",
                typeName + "MaxSlotSizeOverrides", "maxSlotSizeOverrides");
            copyFirstPresent(sourceData, remapped, directionPrefix + typeName + "Filters",
                typeName + "Filters");

            // I/O interfaces expose one shared polling rate in the GUI, so mirror the
            // imported single-direction value into both logic namespaces.
            copyFirstPresent(sourceData, remapped, Direction.IMPORT.getNbtPrefix() + typeName + "PollingRate",
                typeName + "PollingRate", "pollingRate");
            copyFirstPresent(sourceData, remapped, Direction.EXPORT.getNbtPrefix() + typeName + "PollingRate",
                typeName + "PollingRate", "pollingRate");

            return remapped;
        }

        Direction targetDirection = target.direction;
        if (targetDirection == null) return remapped;

        String directionPrefix = targetDirection.getNbtPrefix();

        copyFirstPresent(sourceData, remapped, typeName + "MaxSlotSize",
            directionPrefix + typeName + "MaxSlotSize");
        copyFirstPresent(sourceData, remapped, typeName + "MaxSlotSizeOverrides",
            directionPrefix + typeName + "MaxSlotSizeOverrides");
        copyFirstPresent(sourceData, remapped, typeName + "Filters",
            directionPrefix + typeName + "Filters");
        copyFirstPresent(sourceData, remapped, typeName + "PollingRate",
            directionPrefix + typeName + "PollingRate");

        return remapped;
    }

    @Nullable
    private static String resolveTypeName(CardProfile source, TargetProfile target) {
        if (target.typeName != null) return target.typeName;
        return source.typeName;
    }

    private static void copyFirstPresent(NBTTagCompound source, NBTTagCompound target, String targetKey,
                                         String... sourceKeys) {
        for (String sourceKey : sourceKeys) {
            if (copyIfPresent(source, target, sourceKey, targetKey)) return;
        }
    }

    private static boolean copyIfPresent(NBTTagCompound source, NBTTagCompound target, String sourceKey,
                                         String targetKey) {
        if (!source.hasKey(sourceKey)) return false;

        NBTBase tag = source.getTag(sourceKey);
        if (tag == null) return false;

        target.setTag(targetKey, tag.copy());
        return true;
    }

    @Nullable
    private static String normalizeType(@Nullable String typeName) {
        if (typeName == null || typeName.isEmpty()) return null;
        return typeName.toLowerCase(Locale.ROOT);
    }

    private static boolean isSupportedType(@Nullable String typeName) {
        return "item".equals(typeName)
            || "fluid".equals(typeName)
            || "gas".equals(typeName)
            || "essentia".equals(typeName);
    }

    private enum InterfaceKind {
        SIMPLE,
        COMBINED,
        IO
    }

    private enum Direction {
        IMPORT("import_"),
        EXPORT("export_");

        private final String nbtPrefix;

        Direction(String nbtPrefix) {
            this.nbtPrefix = nbtPrefix;
        }

        public String getNbtPrefix() {
            return this.nbtPrefix;
        }
    }

    private static final class CardProfile {

        private final InterfaceKind kind;

        @Nullable
        private final Direction direction;

        @Nullable
        private final String typeName;

        private CardProfile(InterfaceKind kind, @Nullable Direction direction, @Nullable String typeName) {
            this.kind = kind;
            this.direction = direction;
            this.typeName = typeName;
        }
    }

    public static final class TargetProfile {

        private final InterfaceKind kind;

        @Nullable
        private final Direction direction;

        @Nullable
        private final String typeName;

        private TargetProfile(InterfaceKind kind, @Nullable Direction direction, @Nullable String typeName) {
            this.kind = kind;
            this.direction = direction;
            this.typeName = typeName;
        }
    }
}