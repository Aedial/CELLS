package com.cells.integration.theoneprobe;

import io.netty.buffer.ByteBuf;

import mcjty.theoneprobe.api.IElement;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.network.NetworkTools;

import com.cells.util.LocalizedTooltipText;


public class TopLocalizedTextElement implements IElement {

    private static int elementId = -1;

    private final LocalizedTooltipText text;

    public TopLocalizedTextElement(LocalizedTooltipText text) {
        this.text = text;
    }

    public TopLocalizedTextElement(ByteBuf buf) {
        this.text = readText(buf);
    }

    public static void register(ITheOneProbe probe) {
        elementId = probe.registerElementFactory(TopLocalizedTextElement::new);
    }

    @Override
    public void render(int x, int y) {
        TopLocalizedTextRenderer.render(this.text, x, y);
    }

    @Override
    public int getWidth() {
        return TopLocalizedTextRenderer.getWidth(this.text);
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        writeText(buf, this.text);
    }

    @Override
    public int getID() {
        return elementId;
    }

    private static void writeText(ByteBuf buf, LocalizedTooltipText text) {
        buf.writeBoolean(text.isLiteral());

        if (text.isLiteral()) {
            NetworkTools.writeStringUTF8(buf, text.getLiteral());
            return;
        }

        NetworkTools.writeStringUTF8(buf, text.getTranslationKey());
        buf.writeInt(text.getArguments().size());
        for (LocalizedTooltipText argument : text.getArguments()) {
            writeText(buf, argument);
        }
    }

    private static LocalizedTooltipText readText(ByteBuf buf) {
        if (buf.readBoolean()) {
            return LocalizedTooltipText.literal(NetworkTools.readStringUTF8(buf));
        }

        String translationKey = NetworkTools.readStringUTF8(buf);
        int argumentCount = buf.readInt();
        LocalizedTooltipText[] arguments = new LocalizedTooltipText[argumentCount];
        for (int index = 0; index < argumentCount; index++) {
            arguments[index] = readText(buf);
        }

        return LocalizedTooltipText.translated(translationKey, arguments);
    }
}