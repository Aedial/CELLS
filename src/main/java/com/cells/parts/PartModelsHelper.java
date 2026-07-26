package com.cells.parts;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;

import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Helper to extract @PartModels annotated fields from part classes.
 */
public final class PartModelsHelper {

    private PartModelsHelper() {
    }

    public static List<ResourceLocation> createModels(Class<?> clazz) {
        List<ResourceLocation> models = new ArrayList<>();

        for (Field field : clazz.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!field.isAnnotationPresent(PartModels.class)) continue;

            try {
                Object value = field.get(null);
                if (value instanceof IPartModel) {
                    IPartModel partModel = (IPartModel) value;
                    models.addAll(partModel.getModels());
                } else if (value instanceof ResourceLocation) {
                    models.add((ResourceLocation) value);
                }
            } catch (IllegalAccessException e) {
                // Ignore
            }
        }

        return models;
    }

    /**
     * Create part models for an interface part, choosing between normal and fixed
     * textures based on the config.
     * Returns {MODEL_BASE, MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL}.
     *
     * @param prefix The model path prefix (e.g., "part/import_interface/item/")
     * @return Array of [MODEL_BASE, MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL]
     */
    public static Object[] createInterfaceModels(String prefix) {
        String baseName = CellsConfig.interfaces.useFixedInterfaceTextures ? "base_fixed" : "base";
        String hasChannelName = CellsConfig.interfaces.useFixedInterfaceTextures ? "has_channel_fixed" : "has_channel";

        ResourceLocation modelBase = new ResourceLocation(Tags.MODID, prefix + baseName);

        // on/off models always use front_still, no need for fixed variants
        PartModel modelsOff = new PartModel(modelBase, new ResourceLocation(Tags.MODID, prefix + "off"));
        PartModel modelsOn = new PartModel(modelBase, new ResourceLocation(Tags.MODID, prefix + "on"));
        PartModel modelsHasChannel = new PartModel(modelBase, new ResourceLocation(Tags.MODID, prefix + hasChannelName));

        return new Object[] { modelBase, modelsOff, modelsOn, modelsHasChannel };
    }
}
