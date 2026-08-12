package com.cells.network.sync;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;


/**
 * Unified resource type enum for network packet serialization.
 * <p>
 * Each type knows how to serialize/deserialize its stacks to/from ByteBuf.
 * This enables a single unified packet to handle ALL resource types.
 * <p>
 * Optional mod resources (Gas, Essentia) are handled via @Optional.Method
 * in dedicated helper classes, preventing class loading errors.
 */
public enum ResourceType {
    /**
     * Item stacks - uses vanilla serialization + NBT.
     */
    ITEM("item"),

    /**
     * Fluid stacks - uses AE2's IAEFluidStack.
     */
    FLUID("fluid"),

    /**
     * Gas stacks - requires MekanismEnergistics.
     */
    GAS("gas"),

    /**
     * Essentia stacks - requires ThaumicEnergistics.
     */
    ESSENTIA("essentia");


    private final String name;

    ResourceType(String name) {
        this.name = name;
    }

    ResourceType() {
        this.name = this.name().toLowerCase();
    }

    public String getName() {
        return this.name;
    }

    public String getTranslationKey() {
        return "cells.type." + this.name;
    }

    public String getUnitTranslationKey() {
        return "cells.unit." + this.name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    // ================================= Serialization =================================

    /**
     * Write a resource to the buffer.
     * The resource can be null (representing clearing a slot).
     */
    public void write(ByteBuf buf, @Nullable Object resource) {
        if (resource == null) {
            buf.writeBoolean(false);
            return;
        }
        buf.writeBoolean(true);

        switch (this) {
            case ITEM:
                writeItem(buf, (IAEItemStack) resource);
                break;
            case FLUID:
                writeFluid(buf, (IAEFluidStack) resource);
                break;
            case GAS:
                if (GAS.isAvailable()) GasSerializationHelper.write(buf, resource);
                break;
            case ESSENTIA:
                if (ESSENTIA.isAvailable()) EssentiaSerializationHelper.write(buf, resource);
                break;
        }
    }

    /**
     * Read a resource from the buffer.
     * Returns null if the slot was cleared.
     */
    @Nullable
    public Object read(ByteBuf buf) {
        if (!buf.readBoolean()) return null;

        switch (this) {
            case ITEM:
                return readItem(buf);
            case FLUID:
                return readFluid(buf);
            case GAS:
                return GAS.isAvailable() ? GasSerializationHelper.read(buf) : null;
            case ESSENTIA:
                return ESSENTIA.isAvailable() ? EssentiaSerializationHelper.read(buf) : null;
            default:
                return null;
        }
    }

    // ================================= Item Serialization =================================

    /**
     * Write an IAEItemStack to the buffer.
     * Serializes the definition ItemStack internally but the API uses IAEItemStack.
     */
    private static void writeItem(ByteBuf buf, IAEItemStack stack) {
        ItemStack definition = stack.getDefinition();

        buf.writeInt(Item.getIdFromItem(definition.getItem()));
        buf.writeLong(stack.getStackSize());
        buf.writeShort(definition.getMetadata());

        NBTTagCompound nbt = definition.getTagCompound();
        if (nbt != null) {
            buf.writeBoolean(true);
            byte[] nbtBytes = nbt.toString().getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nbtBytes.length);
            buf.writeBytes(nbtBytes);
        } else {
            buf.writeBoolean(false);
        }
    }

    /**
     * Read an IAEItemStack from the buffer.
     */
    @Nullable
    private static IAEItemStack readItem(ByteBuf buf) {
        int itemId = buf.readInt();
        if (itemId < 0) return null;

        Item item = Item.getItemById(itemId);

        long count = buf.readLong();
        int meta = buf.readShort();

        ItemStack definition = new ItemStack(item, 1, meta);

        if (buf.readBoolean()) {
            int nbtLen = buf.readInt();
            byte[] nbtBytes = new byte[nbtLen];
            buf.readBytes(nbtBytes);

            try {
                String nbtString = new String(nbtBytes, StandardCharsets.UTF_8);
                definition.setTagCompound(JsonToNBT.getTagFromJson(nbtString));
            } catch (Exception e) {
                // Failed to parse NBT, continue without it
            }
        }

        IAEItemStack result = AEItemStack.fromItemStack(definition);
        if (result != null) result.setStackSize(count);

        return result;
    }

    // ================================= Fluid Serialization =================================

    private static void writeFluid(ByteBuf buf, IAEFluidStack stack) {
        FluidStack fluid = stack.getFluidStack();
        String fluidName = fluid.getFluid().getName();
        byte[] nameBytes = fluidName.getBytes(StandardCharsets.UTF_8);

        buf.writeInt(nameBytes.length);
        buf.writeBytes(nameBytes);
        buf.writeLong(stack.getStackSize());

        // Write NBT if present
        NBTTagCompound nbt = fluid.tag;
        if (nbt != null) {
            buf.writeBoolean(true);
            byte[] nbtBytes = nbt.toString().getBytes(StandardCharsets.UTF_8);
            buf.writeInt(nbtBytes.length);
            buf.writeBytes(nbtBytes);
        } else {
            buf.writeBoolean(false);
        }
    }

    @Nullable
    private static IAEFluidStack readFluid(ByteBuf buf) {
        int nameLen = buf.readInt();
        byte[] nameBytes = new byte[nameLen];
        buf.readBytes(nameBytes);
        String fluidName = new String(nameBytes, StandardCharsets.UTF_8);

        long amount = buf.readLong();

        // FluidRegistry.getFluidStack takes an int amount, but we store longs.
        // Pass 1 as a dummy, we only need the FluidStack for identity (fluid type + NBT).
        // The real amount is restored on the IAEFluidStack below.
        FluidStack fluid = FluidRegistry.getFluidStack(fluidName, 1);
        if (fluid == null) return null;

        // Read NBT if present
        if (buf.readBoolean()) {
            int nbtLen = buf.readInt();
            byte[] nbtBytes = new byte[nbtLen];
            buf.readBytes(nbtBytes);

            try {
                String nbtString = new String(nbtBytes, StandardCharsets.UTF_8);
                fluid.tag = JsonToNBT.getTagFromJson(nbtString);
            } catch (Exception e) {
                // Failed to parse NBT, continue without it
            }
        }

        IAEFluidStack result = AEFluidStack.fromFluidStack(fluid);
        if (result != null) result.setStackSize(amount);

        return result;
    }

    // ================================= Availability Checks =================================

    /**
     * Deep-copy a stack object for caching purposes.
     * All supported stack types (IAEItemStack, IAEFluidStack, IAEGasStack, IAEEssentiaStack)
     * extend IAEStack, which provides a copy() method.
     *
     * @param stack The stack to copy, or null
     * @return A deep copy of the stack, or null if the input was null
     */
    @Nullable
    @SuppressWarnings("rawtypes")
    public static Object copyStack(@Nullable Object stack) {
        if (stack == null) return null;

        if (stack instanceof IAEStack) {
            return ((IAEStack) stack).copy();
        }

        // Fallback: return as-is (should not happen for known types)
        return stack;
    }

    /**
     * Check if this resource type is available (mod loaded).
     */
    public boolean isAvailable() {
        switch (this) {
            case ITEM:
            case FLUID:
                return true;
            case GAS:
                return Loader.isModLoaded("mekeng");
            case ESSENTIA:
                return Loader.isModLoaded("thaumicenergistics");
            default:
                return false;
        }
    }

    /**
     * Get a ResourceType from its ordinal value.
     * Returns ITEM if the ordinal is out of range.
     */
    public static ResourceType fromOrdinal(int ordinal) {
        ResourceType[] values = values();
        if (ordinal >= 0 && ordinal < values.length) return values[ordinal];
        return ITEM;
    }

    /**
     * Get all ResourceTypes whose backing mod is currently loaded.
     * Always includes ITEM and FLUID; GAS and ESSENTIA depend on mod presence.
     */
    public static ResourceType[] getAvailableTypes() {
        List<ResourceType> list = new ArrayList<>();
        for (ResourceType type : values()) {
            if (type.isAvailable()) list.add(type);
        }
        return list.toArray(new ResourceType[0]);
    }
}
