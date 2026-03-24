package com.cells.network.sync;

import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.Optional;

import mekanism.api.gas.GasStack;

import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;


/**
 * Helper class for Gas stack serialization.
 * <p>
 * Uses @Optional.Method to properly handle the optional MekanismEnergistics dependency
 * without reflection, preventing class loading errors when the mod is not present.
 */
public final class GasSerializationHelper {

    private GasSerializationHelper() {}

    /**
     * Write an IAEGasStack to the buffer.
     * Uses MekEng's built-in serialization.
     */
    @Optional.Method(modid = "mekeng")
    public static void write(ByteBuf buf, Object stack) {
        if (stack instanceof IAEGasStack) {
            try {
                ((IAEGasStack) stack).writeToPacket(buf);
            } catch (IOException e) {
                // Serialization failed - this shouldn't happen with ByteBuf
            }
        }
    }

    /**
     * Read an IAEGasStack from the buffer.
     * Uses MekEng's built-in deserialization.
     */
    @Nullable
    @Optional.Method(modid = "mekeng")
    public static Object read(ByteBuf buf) {
        return AEGasStack.of(buf);
    }

    /**
     * Copy an IAEGasStack.
     */
    @Nullable
    @Optional.Method(modid = "mekeng")
    public static Object copy(@Nullable Object stack) {
        if (stack instanceof IAEGasStack) return ((IAEGasStack) stack).copy();

        return null;
    }

    /**
     * Check if two IAEGasStack are equal.
     */
    @Optional.Method(modid = "mekeng")
    public static boolean equals(@Nullable Object a, @Nullable Object b) {
        if (a == null) return b == null;
        if (!(a instanceof IAEGasStack) || !(b instanceof IAEGasStack)) return false;

        IAEGasStack gasA = (IAEGasStack) a;
        IAEGasStack gasB = (IAEGasStack) b;
        GasStack stackA = gasA.getGasStack();
        GasStack stackB = gasB.getGasStack();

        if (stackA == null) return stackB == null;
        if (stackB == null) return false;

        return stackA.getGas() == stackB.getGas();
    }
}
