package com.cells.parts;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;


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
}
