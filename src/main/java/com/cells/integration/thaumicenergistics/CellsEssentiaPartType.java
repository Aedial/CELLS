package com.cells.integration.thaumicenergistics;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPart;
import appeng.util.Platform;

import com.cells.Tags;
import com.cells.parts.PartModelsHelper;


/**
 * Enum defining all CELLS essentia part types.
 * Isolated from main CellsPartType to allow conditional loading only when
 * ThaumicEnergistics is present.
 */
public enum CellsEssentiaPartType {
    ESSENTIA_IMPORT_INTERFACE("import_essentia_interface", PartEssentiaImportInterface.class, "tooltip.cells.import_interface.essentia.info"),
    ESSENTIA_EXPORT_INTERFACE("export_essentia_interface", PartEssentiaExportInterface.class, "tooltip.cells.export_interface.essentia.part.info");

    private final String id;
    private final Class<? extends IPart> partClass;
    private final int baseDamage;
    private final Set<ResourceLocation> models;
    private Constructor<? extends IPart> constructor;
    private final List<ModelResourceLocation> itemModels;
    private final String tooltipKey;

    CellsEssentiaPartType(String id, Class<? extends IPart> partClass, String tooltipKey) {
        this.id = id;
        this.partClass = partClass;
        this.baseDamage = this.ordinal();
        this.tooltipKey = tooltipKey;

        // Load models
        if (Platform.isClientInstall()) {
            this.itemModels = createItemModels(id);
        } else {
            this.itemModels = Collections.emptyList();
        }

        if (partClass != null) {
            this.models = new HashSet<>(PartModelsHelper.createModels(partClass));
        } else {
            this.models = Collections.emptySet();
        }
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation modelFromBaseName(String baseName) {
        return new ModelResourceLocation(new ResourceLocation(Tags.MODID, "part/" + baseName), "inventory");
    }

    @SideOnly(Side.CLIENT)
    private List<ModelResourceLocation> createItemModels(String baseName) {
        return ImmutableList.of(modelFromBaseName(baseName));
    }

    public int getBaseDamage() {
        return this.baseDamage;
    }

    public Class<? extends IPart> getPartClass() {
        return this.partClass;
    }

    public String getUnlocalizedName() {
        return "item." + Tags.MODID + ".part." + this.id;
    }

    public Constructor<? extends IPart> getConstructor() {
        return this.constructor;
    }

    public void setConstructor(Constructor<? extends IPart> constructor) {
        this.constructor = constructor;
    }

    @SideOnly(Side.CLIENT)
    public List<ModelResourceLocation> getItemModels() {
        return this.itemModels;
    }

    public Set<ResourceLocation> getModels() {
        return this.models;
    }

    /**
     * Get the tooltip key for this part type.
     */
    public String getTooltipKey() {
        return this.tooltipKey;
    }

    /**
     * Get a part type by its damage value.
     */
    public static CellsEssentiaPartType getById(int id) {
        if (id < 0 || id >= values().length) return null;
        return values()[id];
    }
}
