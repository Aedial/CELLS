package com.cells.items.upgrades.ratio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;

import com.cells.network.sync.ResourceType;


public final class RatioCardRecipe {

    private static final ResourceLocation FLUID_PACKET = new ResourceLocation("ae2fc", "fluid_packet");
    private static final ResourceLocation FLUID_DROP = new ResourceLocation("ae2fc", "fluid_drop");
    private static final ResourceLocation GAS_PACKET = new ResourceLocation("ae2fc", "gas_packet");
    private static final ResourceLocation GAS_DROP = new ResourceLocation("ae2fc", "gas_drop");

    public final int slot;
    public final ItemStack pattern;
    public final ItemStack output;
    public final List<Ingredient> inputs;

    private RatioCardRecipe(int slot, ItemStack pattern, ItemStack output, List<Ingredient> inputs) {
        this.slot = slot;
        this.pattern = pattern.copy();
        this.output = output;
        this.inputs = Collections.unmodifiableList(inputs);
    }

    public static RatioCardRecipe read(int slot, ItemStack pattern, @Nullable World world) {
        if (!(pattern.getItem() instanceof ICraftingPatternItem)) return invalid(slot, pattern);

        try {
            ICraftingPatternDetails details = ((ICraftingPatternItem) pattern.getItem())
                .getPatternForItem(pattern, world);
            if (details == null) return invalid(slot, pattern);

            Map<IAEStack<?>, Ingredient> inputs = new LinkedHashMap<>();
            for (IAEItemStack input : details.getInputs()) {
                if (input == null || input.getStackSize() <= 0) continue;

                Ingredient ingredient = decode(input);
                IAEStack<?> key = ingredient.stack.copy().setStackSize(1);
                Ingredient existing = inputs.get(key);
                if (existing == null) {
                    inputs.put(key, ingredient);
                } else {
                    long amount = Math.addExact(existing.stack.getStackSize(), ingredient.stack.getStackSize());
                    existing.stack.setStackSize(amount);
                }
            }

            IAEItemStack[] outputs = details.getCondensedOutputs();
            ItemStack output = outputs.length > 0 && outputs[0] != null
                ? outputs[0].createItemStack() : ItemStack.EMPTY;
            return new RatioCardRecipe(slot, pattern, output, new ArrayList<>(inputs.values()));
        } catch (RuntimeException e) {
            return invalid(slot, pattern);
        }
    }

    private static RatioCardRecipe invalid(int slot, ItemStack pattern) {
        return new RatioCardRecipe(slot, pattern, ItemStack.EMPTY, Collections.emptyList());
    }

    private static Ingredient decode(IAEItemStack input) {
        Ingredient fluid = decodeFluid(input);
        if (fluid != null) return fluid;

        if (ResourceType.GAS.isAvailable()) {
            Ingredient gas = decodeGas(input);
            if (gas != null) return gas;
        }

        if (ResourceType.ESSENTIA.isAvailable()) {
            Ingredient essentia = decodeEssentia(input);
            if (essentia != null) return essentia;
        }

        return new Ingredient(ResourceType.ITEM, input.copy());
    }

    @Nullable
    private static Ingredient decodeFluid(IAEItemStack input) {
        ItemStack item = input.getDefinition();
        ResourceLocation name = item.getItem().getRegistryName();
        NBTTagCompound tag = item.getTagCompound();
        FluidStack fluid;
        if (item.getItem() instanceof FluidDummyItem) {
            fluid = ((FluidDummyItem) item.getItem()).getFluidStack(item);
        } else if (FLUID_PACKET.equals(name)) {
            fluid = tag == null ? null : FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidStack"));
        } else if (FLUID_DROP.equals(name)) {
            fluid = tag == null ? null : FluidRegistry.getFluidStack(tag.getString("Fluid"), 1);
            if (fluid != null && tag.hasKey("FluidTag")) fluid.tag = tag.getCompoundTag("FluidTag").copy();
        } else {
            return null;
        }

        if (fluid == null || fluid.amount <= 0) throw new IllegalArgumentException("Invalid pattern fluid");

        IAEStack<?> stack = AEFluidStack.fromFluidStack(fluid);
        stack.setStackSize(Math.multiplyExact(input.getStackSize(), (long) fluid.amount));
        return new Ingredient(ResourceType.FLUID, stack);
    }

    @Nullable
    @Optional.Method(modid = "mekeng")
    private static Ingredient decodeGas(IAEItemStack input) {
        ItemStack item = input.getDefinition();
        ResourceLocation name = item.getItem().getRegistryName();
        NBTTagCompound tag = item.getTagCompound();
        mekanism.api.gas.GasStack gas;
        if (item.getItem() instanceof com.mekeng.github.common.item.ItemDummyGas) {
            gas = ((com.mekeng.github.common.item.ItemDummyGas) item.getItem()).getGasStack(item);
        } else if (GAS_PACKET.equals(name)) {
            gas = tag == null ? null : mekanism.api.gas.GasStack.readFromNBT(tag.getCompoundTag("GasStack"));
        } else if (GAS_DROP.equals(name)) {
            mekanism.api.gas.Gas type = tag == null ? null
                : mekanism.api.gas.GasRegistry.getGas(tag.getString("Gas"));
            gas = type == null ? null : new mekanism.api.gas.GasStack(type, 1);
        } else {
            return null;
        }

        if (gas == null || gas.amount <= 0 || gas.getGas() == null) {
            throw new IllegalArgumentException("Invalid pattern gas");
        }

        IAEStack<?> stack = com.mekeng.github.common.me.data.impl.AEGasStack.of(gas);
        stack.setStackSize(Math.multiplyExact(input.getStackSize(), (long) gas.amount));
        return new Ingredient(ResourceType.GAS, stack);
    }

    @Nullable
    @Optional.Method(modid = "thaumicenergistics")
    private static Ingredient decodeEssentia(IAEItemStack input) {
        ItemStack item = input.getDefinition();
        if (!(item.getItem() instanceof thaumicenergistics.item.ItemDummyAspect)) return null;

        thaumcraft.api.aspects.Aspect aspect = ((thaumicenergistics.item.ItemDummyAspect) item.getItem())
            .getAspect(item);
        if (aspect == null) throw new IllegalArgumentException("Invalid pattern essentia");

        IAEStack<?> stack = thaumicenergistics.integration.appeng.AEEssentiaStack.fromEssentiaStack(
            new thaumicenergistics.api.EssentiaStack(aspect, 1));
        stack.setStackSize(input.getStackSize());
        return new Ingredient(ResourceType.ESSENTIA, stack);
    }

    public static final class Ingredient {

        public final ResourceType type;
        public final IAEStack<?> stack;

        public Ingredient(ResourceType type, IAEStack<?> stack) {
            this.type = type;
            this.stack = stack;
        }
    }
}
