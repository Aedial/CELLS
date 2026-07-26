package com.cells.integration.theoneprobe;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mcjty.theoneprobe.apiimpl.client.ElementTextRender;

import com.cells.util.LocalizedTooltipText;


@SideOnly(Side.CLIENT)
public final class TopLocalizedTextRenderer {

    private TopLocalizedTextRenderer() {}

    public static void render(LocalizedTooltipText text, int x, int y) {
        ElementTextRender.render(resolve(text), x, y);
    }

    public static int getWidth(LocalizedTooltipText text) {
        return ElementTextRender.getWidth(resolve(text));
    }

    @Nonnull
    private static String resolve(LocalizedTooltipText text) {
        if (text.isLiteral()) return text.getLiteral();

        Object[] arguments = new Object[text.getArguments().size()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = resolve(text.getArguments().get(index));
        }

        return I18n.format(text.getTranslationKey(), arguments);
    }
}