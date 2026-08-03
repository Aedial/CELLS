package com.cells.integration.theoneprobe;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mcjty.theoneprobe.api.IProbeInfo;

import com.cells.api.IInterfaceProvider;
import com.cells.util.AbstractProbeTooltipHelper;
import com.cells.util.LocalizedTooltipText;


public final class TopInterfaceProbeTooltipHelper extends AbstractProbeTooltipHelper<LocalizedTooltipText> {

    private static final TopInterfaceProbeTooltipHelper INSTANCE = new TopInterfaceProbeTooltipHelper();

    private TopInterfaceProbeTooltipHelper() {}

    public static boolean appendTooltipLines(@Nullable Object target, IProbeInfo probeInfo) {
        if (probeInfo == null) return false;

        return INSTANCE.appendLines(target, createSink(probeInfo));
    }

    private static Consumer<LocalizedTooltipText> createSink(IProbeInfo probeInfo) {
        return line -> {
            if (line.isEmpty()) {
                probeInfo.text("");
                return;
            }

            probeInfo.element(new TopLocalizedTextElement(line));
        };
    }

    @Override
    protected void appendSeparator(Consumer<LocalizedTooltipText> sink) {
        sink.accept(LocalizedTooltipText.literal(""));
    }

    @Nonnull
    @Override
    protected LocalizedTooltipText renderLine(LocalizedTooltipText line) {
        return line;
    }
}