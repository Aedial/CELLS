package com.cells.network.sync;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.Optional;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.EssentiaStack;


/**
 * Helper class for Essentia stack serialization.
 * <p>
 * Uses @Optional.Method to properly handle the optional ThaumicEnergistics dependency
 * without reflection, preventing class loading errors when the mod is not present.
 */
public final class EssentiaSerializationHelper {

    private EssentiaSerializationHelper() {}

    /**
     * Write an EssentiaStack to the buffer.
     */
    @Optional.Method(modid = "thaumicenergistics")
    public static void write(ByteBuf buf, Object stack) {
        if (!(stack instanceof EssentiaStack)) return;

        EssentiaStack essentia = (EssentiaStack) stack;
        Aspect aspect = essentia.getAspect();
        if (aspect == null) return;

        String tag = aspect.getTag();
        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(tagBytes.length);
        buf.writeBytes(tagBytes);
        buf.writeLong(essentia.getAmount());
    }

    /**
     * Read an EssentiaStack from the buffer.
     */
    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    public static Object read(ByteBuf buf) {
        int tagLen = buf.readInt();
        byte[] tagBytes = new byte[tagLen];
        buf.readBytes(tagBytes);
        String tag = new String(tagBytes, StandardCharsets.UTF_8);

        long amount = buf.readLong();

        Aspect aspect = Aspect.getAspect(tag);
        if (aspect == null) return null;

        // EssentiaStack constructor takes int for amount
        return new EssentiaStack(aspect, (int) Math.min(amount, Integer.MAX_VALUE));
    }

    /**
     * Copy an EssentiaStack.
     */
    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    public static Object copy(@Nullable Object stack) {
        if (stack instanceof EssentiaStack) return ((EssentiaStack) stack).copy();

        return null;
    }

    /**
     * Check if two EssentiaStack are equal.
     */
    @Optional.Method(modid = "thaumicenergistics")
    public static boolean equals(@Nullable Object a, @Nullable Object b) {
        if (a == null) return b == null;
        if (!(a instanceof EssentiaStack) || !(b instanceof EssentiaStack)) return false;

        EssentiaStack stackA = (EssentiaStack) a;
        EssentiaStack stackB = (EssentiaStack) b;

        return stackA.getAspect() == stackB.getAspect();
    }
}
