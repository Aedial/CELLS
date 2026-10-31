package com.cells.util;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


@SideOnly(Side.CLIENT)
public final class InterfaceProbeTooltipHelper {

    private InterfaceProbeTooltipHelper() {}

    @Nonnull
    public static String render(LocalizedTooltipText line) {
        if (line.isLiteral()) return line.getLiteral();

        Object[] arguments = new Object[line.getArguments().size()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = render(line.getArguments().get(index));
        }

        return I18n.format(line.getTranslationKey(), arguments);
    }
}