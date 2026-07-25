package com.cells.util;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.api.IInterfaceProvider;


@SideOnly(Side.CLIENT)
public final class InterfaceProbeTooltipHelper extends AbstractInterfaceProbeTooltipHelper<String> {

    private static final InterfaceProbeTooltipHelper INSTANCE = new InterfaceProbeTooltipHelper();

    private InterfaceProbeTooltipHelper() {}

    public static boolean appendTooltipLines(@Nullable Object target, Consumer<String> sink) {
        return INSTANCE.appendLines(target, sink);
    }

    public static void appendTooltipLines(@Nullable IInterfaceProvider provider, Consumer<String> sink) {
        INSTANCE.appendLines(provider, sink);
    }

    @Override
    protected void appendSeparator(Consumer<String> sink) {
        sink.accept("");
    }

    @Nonnull
    @Override
    protected String renderLine(LocalizedTooltipText line) {
        return render(line);
    }

    @Nonnull
    private static String render(LocalizedTooltipText line) {
        if (line.isLiteral()) return line.getLiteral();

        Object[] arguments = new Object[line.getArguments().size()];
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] = render(line.getArguments().get(index));
        }

        return I18n.format(line.getTranslationKey(), arguments);
    }
}