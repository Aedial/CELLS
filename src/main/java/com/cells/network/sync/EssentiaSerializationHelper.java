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

        // Write the IAEEssentiaStack's long stack size, not the inner EssentiaStack's int amount.
        // InterfaceInventoryManager stores amounts as longs, which would be truncated by int.
        long stackSize = (stack instanceof IAEEssentiaStack)
            ? ((IAEEssentiaStack) stack).getStackSize()
            : essentia.getAmount();
        buf.writeLong(stackSize);
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

        // Read as long to match the writeLong in write(). Essentia amounts can exceed int range
        // in our storage system (InterfaceInventoryManager stores amounts as longs).
        long amount = buf.readLong();

        Aspect aspect = Aspect.getAspect(tag);
        if (aspect == null) return null;

        // EssentiaStack constructor takes int, so pass 1 as dummy for identity only.
        // Restore the real long amount on the IAEEssentiaStack.
        IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(new EssentiaStack(aspect, 1));
        result.setStackSize(amount);

        return result;
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
