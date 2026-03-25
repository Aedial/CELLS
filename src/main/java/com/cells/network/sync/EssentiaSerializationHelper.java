package com.cells.network.sync;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.Optional;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;


/**
 * Helper class for Essentia stack serialization.
 * <p>
 * Uses @Optional.Method to properly handle the optional ThaumicEnergistics dependency
 * without reflection, preventing class loading errors when the mod is not present.
 */
public final class EssentiaSerializationHelper {

    private EssentiaSerializationHelper() {}

    /**
     * Write an EssentiaStack or IAEEssentiaStack to the buffer.
     * <p>
     * Handles both raw EssentiaStack and IAEEssentiaStack (from interface containers).
     * The inner EssentiaStack is extracted and serialized.
     */
    @Optional.Method(modid = "thaumicenergistics")
    public static void write(ByteBuf buf, Object stack) {
        // Handle IAEEssentiaStack (from interfaces) - extract the inner EssentiaStack
        EssentiaStack essentia;
        if (stack instanceof IAEEssentiaStack) {
            essentia = ((IAEEssentiaStack) stack).getStack();
        } else if (stack instanceof EssentiaStack) {
            essentia = (EssentiaStack) stack;
        } else {
            // Invalid type - this should never happen if callers are correct
            throw new IllegalArgumentException("Expected EssentiaStack or IAEEssentiaStack, got: " + 
                (stack != null ? stack.getClass().getName() : "null"));
        }

        Aspect aspect = essentia.getAspect();
        if (aspect == null) {
            throw new IllegalStateException("EssentiaStack has null aspect - this should be filtered at the packet level");
        }

        String tag = aspect.getTag();
        byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(tagBytes.length);
        buf.writeBytes(tagBytes);
        buf.writeInt(essentia.getAmount());
    }

    /**
     * Read an IAEEssentiaStack from the buffer.
     */
    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    public static Object read(ByteBuf buf) {
        int tagLen = buf.readInt();
        byte[] tagBytes = new byte[tagLen];
        buf.readBytes(tagBytes);
        String tag = new String(tagBytes, StandardCharsets.UTF_8);

        int amount = buf.readInt();

        Aspect aspect = Aspect.getAspect(tag);
        if (aspect == null) return null;

        return AEEssentiaStack.fromEssentiaStack(new EssentiaStack(aspect, amount));
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
