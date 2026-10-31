package com.cells.integration.waila;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.cells.util.AbstractProbeTooltipHelper;
import com.cells.util.LocalizedTooltipText;


public final class WailaProbeData extends AbstractProbeTooltipHelper<LocalizedTooltipText> {

    private static final WailaProbeData INSTANCE = new WailaProbeData();
    private static final String DATA = "cellsProbe";

    private WailaProbeData() {}

    public static boolean write(@Nullable Object target, NBTTagCompound tag) {
        List<LocalizedTooltipText> lines = new ArrayList<>();
        if (!INSTANCE.appendLines(target, lines::add)) return false;

        NBTTagCompound data = new NBTTagCompound();
        NBTTagList entries = new NBTTagList();
        for (LocalizedTooltipText line : lines) entries.appendTag(writeText(line));
        data.setTag("lines", entries);
        tag.setTag(DATA, data);
        return true;
    }

    @Nullable
    public static NBTTagCompound getData(@Nullable NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(DATA, Constants.NBT.TAG_COMPOUND)) return null;

        return tag.getCompoundTag(DATA);
    }

    @Nonnull
    public static List<LocalizedTooltipText> readLines(NBTTagCompound data) {
        NBTTagList entries = data.getTagList("lines", Constants.NBT.TAG_COMPOUND);
        List<LocalizedTooltipText> lines = new ArrayList<>();
        for (int i = 0; i < entries.tagCount(); i++) lines.add(readText(entries.getCompoundTagAt(i)));
        return lines;
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

    private static NBTTagCompound writeText(LocalizedTooltipText text) {
        NBTTagCompound tag = new NBTTagCompound();
        if (text.isLiteral()) {
            tag.setString("literal", text.getLiteral());
            return tag;
        }

        tag.setString("key", text.getTranslationKey());
        NBTTagList arguments = new NBTTagList();
        for (LocalizedTooltipText argument : text.getArguments()) arguments.appendTag(writeText(argument));
        tag.setTag("arguments", arguments);
        return tag;
    }

    private static LocalizedTooltipText readText(NBTTagCompound tag) {
        if (tag.hasKey("literal", Constants.NBT.TAG_STRING)) {
            return LocalizedTooltipText.literal(tag.getString("literal"));
        }

        NBTTagList entries = tag.getTagList("arguments", Constants.NBT.TAG_COMPOUND);
        LocalizedTooltipText[] arguments = new LocalizedTooltipText[entries.tagCount()];
        for (int i = 0; i < arguments.length; i++) arguments[i] = readText(entries.getCompoundTagAt(i));
        return LocalizedTooltipText.translated(tag.getString("key"), arguments);
    }
}
