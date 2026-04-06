package com.cells.integration.mekanismenergistics;

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
 * Enum defining all CELLS gas part types.
 * Isolated from main CellsPartType to allow conditional loading only when
 * MekanismEnergistics is present.
 */
public enum CellsGasPartType {
    GAS_IMPORT_INTERFACE("import_gas_interface", PartGasImportInterface.class),
    GAS_EXPORT_INTERFACE("export_gas_interface", PartGasExportInterface.class);

    private final String id;
    private final Class<? extends IPart> partClass;
    private final int baseDamage;
    private final Set<ResourceLocation> models;
    private Constructor<? extends IPart> constructor;
    private final List<ModelResourceLocation> itemModels;

    CellsGasPartType(String id, Class<? extends IPart> partClass) {
        this.id = id;
        this.partClass = partClass;
        this.baseDamage = this.ordinal();

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

    public static CellsGasPartType getById(int damage) {
        for (CellsGasPartType type : values()) {
            if (type.getBaseDamage() == damage) return type;
        }
        return null;
    }
}
