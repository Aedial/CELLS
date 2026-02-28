package com.cells.commands;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.Platform;


/**
 * /fillCell &lt;item id&gt;|&lt;fluid id&gt;|&lt;gas name&gt;|&lt;aspect tag&gt; &lt;count&gt;
 * Creative-only command. Uses AE2 insertion APIs to fill the storage cell in hand.
 * Supports items, fluids, gases (MekanismEnergistics), and essentia (ThaumicEnergistics).
 */
public class FillCellCommand extends CommandBase {

    // Mod IDs for optional integrations
    private static final String MOD_MEKENG = "mekeng";
    private static final String MOD_THAUMICENERGISTICS = "thaumicenergistics";

    @Override
    @Nonnull
    public String getName() {
        return "fillCell";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/fillCell <item|fluid|gas|aspect> <count> (with k,m,b,t,q,qq suffixes)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, ICommandSender sender, @Nonnull String[] args) {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("This command must be run by a player."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
            return;
        }

        String targetId = args[0];
        String countStr = args[1].toLowerCase();

        long count;
        try {
            count = parseWithSuffix(countStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("Invalid count: " + args[1]));
            return;
        }

        if (count <= 0) {
            sender.sendMessage(new TextComponentString("Count must be > 0"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) {
            sender.sendMessage(new TextComponentString("Hold a storage cell in your hand."));
            return;
        }

        // Attempt to fill in priority order: Item -> Fluid -> Gas -> Essentia
        // We try each channel type and use the first valid one for the held cell

        // 1. Item channel
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEInventoryHandler<IAEItemStack> itemInv = AEApi.instance().registries().cell().getCellInventory(held, null, itemChannel);
        if (itemInv != null) {
            Item item = Item.getByNameOrId(targetId);
            if (item == null) {
                sender.sendMessage(new TextComponentString("Unknown item: " + targetId));
                return;
            }

            ItemStack toInsert = new ItemStack(item, 1);
            IAEItemStack aeStack = itemChannel.createStack(toInsert);
            if (aeStack == null) {
                sender.sendMessage(new TextComponentString("Failed to create AE item stack for " + targetId));
                return;
            }

            aeStack.setStackSize(count);
            IAEItemStack remainder = itemInv.injectItems(aeStack, Actionable.MODULATE, null);
            reportResult(sender, remainder, args[1], targetId, "items", "");

            return;
        }

        // 2. Fluid channel
        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IMEInventoryHandler<IAEFluidStack> fluidInv = AEApi.instance().registries().cell().getCellInventory(held, null, fluidChannel);
        if (fluidInv != null) {
            Fluid fluid = FluidRegistry.getFluid(targetId);
            if (fluid == null) {
                sender.sendMessage(new TextComponentString("Unknown fluid: " + targetId));
                return;
            }

            int amount = (int) Math.min(count, Integer.MAX_VALUE);
            FluidStack fs = new FluidStack(fluid, amount);
            IAEFluidStack aeFluid = fluidChannel.createStack(fs);
            if (aeFluid == null) {
                sender.sendMessage(new TextComponentString("Failed to create AE fluid stack for " + targetId));
                return;
            }

            aeFluid.setStackSize(count);
            IAEFluidStack remainder = fluidInv.injectItems(aeFluid, Actionable.MODULATE, null);
            reportResult(sender, remainder, args[1], targetId, "mB", "fluid ");

            return;
        }

        // 3. Gas channel (MekanismEnergistics)
        if (Platform.isModLoaded(MOD_MEKENG)) {
            String gasResult = tryFillGasCell(held, targetId, count, args[1]);
            if (gasResult != null) {
                sender.sendMessage(new TextComponentString(gasResult));
                return;
            }
        }

        // 4. Essentia channel (ThaumicEnergistics)
        if (Platform.isModLoaded(MOD_THAUMICENERGISTICS)) {
            String essentiaResult = tryFillEssentiaCell(held, targetId, count, args[1]);
            if (essentiaResult != null) {
                sender.sendMessage(new TextComponentString(essentiaResult));
                return;
            }
        }

        sender.sendMessage(new TextComponentString("Held item cannot store items, fluids, gases, or essentia."));
    }

    /**
     * Report the result of an injection operation to the command sender.
     */
    private void reportResult(ICommandSender sender, @Nullable IAEStack<?> remainder, String countArg,
                               String targetId, String unitName, String typePrefix) {
        if (remainder == null || remainder.getStackSize() <= 0) {
            sender.sendMessage(new TextComponentString("Filled " + typePrefix + "cell with " + countArg + " " + unitName + " of " + targetId));
        } else {
            sender.sendMessage(new TextComponentString("Partially filled " + typePrefix + "cell. Could not insert " + remainder.getStackSize() + " " + unitName + "."));
        }
    }

    /**
     * Try to fill a gas cell. Returns a result message if the cell supports gas,
     * or null if gas is not supported by the held cell.
     */
    @Nullable
    @Optional.Method(modid = MOD_MEKENG)
    private String tryFillGasCell(ItemStack held, String gasName, long count, String countArg) {
        com.mekeng.github.common.me.storage.IGasStorageChannel gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

        IMEInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> gasInv =
                AEApi.instance().registries().cell().getCellInventory(held, null, gasChannel);
        if (gasInv == null) return null;

        mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(gasName);
        if (gas == null) return "Unknown gas: " + gasName;

        mekanism.api.gas.GasStack gasStack = new mekanism.api.gas.GasStack(gas, (int) Math.min(count, Integer.MAX_VALUE));
        com.mekeng.github.common.me.data.IAEGasStack aeGasStack =
                com.mekeng.github.common.me.data.impl.AEGasStack.of(gasStack);
        if (aeGasStack == null) return "Failed to create AE gas stack for " + gasName;

        aeGasStack.setStackSize(count);
        com.mekeng.github.common.me.data.IAEGasStack remainder = gasInv.injectItems(aeGasStack, Actionable.MODULATE, null);

        if (remainder == null || remainder.getStackSize() <= 0) {
            return "Filled gas cell with " + countArg + " mB of " + gasName;
        } else {
            return "Partially filled gas cell. Could not insert " + remainder.getStackSize() + " mB.";
        }
    }

    /**
     * Try to fill an essentia cell. Returns a result message if the cell supports essentia,
     * or null if essentia is not supported by the held cell.
     */
    @Nullable
    @Optional.Method(modid = MOD_THAUMICENERGISTICS)
    private String tryFillEssentiaCell(ItemStack held, String aspectTag, long count, String countArg) {
        thaumicenergistics.api.storage.IEssentiaStorageChannel essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

        IMEInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaInv =
                AEApi.instance().registries().cell().getCellInventory(held, null, essentiaChannel);
        if (essentiaInv == null) return null;

        thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(aspectTag);
        if (aspect == null) return "Unknown aspect: " + aspectTag;

        thaumicenergistics.api.EssentiaStack essentiaStack =
                new thaumicenergistics.api.EssentiaStack(aspect, (int) Math.min(count, Integer.MAX_VALUE));
        thaumicenergistics.api.storage.IAEEssentiaStack aeEssentiaStack =
                thaumicenergistics.integration.appeng.AEEssentiaStack.fromEssentiaStack(essentiaStack);
        if (aeEssentiaStack == null) return "Failed to create AE essentia stack for " + aspectTag;

        aeEssentiaStack.setStackSize(count);
        thaumicenergistics.api.storage.IAEEssentiaStack remainder =
                essentiaInv.injectItems(aeEssentiaStack, Actionable.MODULATE, null);

        if (remainder == null || remainder.getStackSize() <= 0) {
            return "Filled essentia cell with " + countArg + " of " + aspectTag;
        } else {
            return "Partially filled essentia cell. Could not insert " + remainder.getStackSize() + ".";
        }
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                          String[] args, BlockPos pos) {
        List<String> ret = new ArrayList<>();
        if (args.length != 1) return getListOfStringsMatchingLastWord(args, ret);

        String last = args[0];

        // Try to detect held cell type from the command sender (if player)
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            return getListOfStringsMatchingLastWord(args, ret);
        }

        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        ItemStack held = player.getHeldItemMainhand();
        if (held.isEmpty()) return getListOfStringsMatchingLastWord(args, ret);

        // 1. Item cell
        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEInventoryHandler<IAEItemStack> itemInv = AEApi.instance().registries().cell().getCellInventory(held, null, itemChannel);
        if (itemInv != null) {
            for (ResourceLocationWrapper rl : ResourceLocationWrapper.listItemRegistry()) {
                String name = rl.toString();
                if (name.startsWith(last)) ret.add(name);
            }

            return getListOfStringsMatchingLastWord(args, ret);
        }

        // 2. Fluid cell
        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IMEInventoryHandler<IAEFluidStack> fluidInv = AEApi.instance().registries().cell().getCellInventory(held, null, fluidChannel);
        if (fluidInv != null) {
            for (String fname : FluidRegistry.getRegisteredFluids().keySet()) {
                if (fname.startsWith(last)) ret.add(fname);
            }

            return getListOfStringsMatchingLastWord(args, ret);
        }

        // 3. Gas cell (MekanismEnergistics)
        if (Platform.isModLoaded(MOD_MEKENG)) {
            List<String> gasCompletions = getGasTabCompletions(held, last);
            if (gasCompletions != null) {
                ret.addAll(gasCompletions);
                return getListOfStringsMatchingLastWord(args, ret);
            }
        }

        // 4. Essentia cell (ThaumicEnergistics)
        if (Platform.isModLoaded(MOD_THAUMICENERGISTICS)) {
            List<String> essentiaCompletions = getEssentiaTabCompletions(held, last);
            if (essentiaCompletions != null) {
                ret.addAll(essentiaCompletions);
                return getListOfStringsMatchingLastWord(args, ret);
            }
        }

        return getListOfStringsMatchingLastWord(args, ret);
    }

    /**
     * Get gas name completions for tab completion. Returns null if the cell doesn't support gas.
     */
    @Nullable
    @Optional.Method(modid = MOD_MEKENG)
    private List<String> getGasTabCompletions(ItemStack held, String prefix) {
        com.mekeng.github.common.me.storage.IGasStorageChannel gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);

        IMEInventoryHandler<com.mekeng.github.common.me.data.IAEGasStack> gasInv =
                AEApi.instance().registries().cell().getCellInventory(held, null, gasChannel);
        if (gasInv == null) return null;

        List<String> ret = new ArrayList<>();
        for (mekanism.api.gas.Gas gas : mekanism.api.gas.GasRegistry.getRegisteredGasses()) {
            String name = gas.getName();
            if (name.startsWith(prefix)) ret.add(name);
        }

        return ret;
    }

    /**
     * Get essentia aspect completions for tab completion. Returns null if the cell doesn't support essentia.
     */
    @Nullable
    @Optional.Method(modid = MOD_THAUMICENERGISTICS)
    private List<String> getEssentiaTabCompletions(ItemStack held, String prefix) {
        thaumicenergistics.api.storage.IEssentiaStorageChannel essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);

        IMEInventoryHandler<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaInv =
                AEApi.instance().registries().cell().getCellInventory(held, null, essentiaChannel);
        if (essentiaInv == null) return null;

        List<String> ret = new ArrayList<>();
        for (String tag : thaumcraft.api.aspects.Aspect.aspects.keySet()) {
            if (tag.startsWith(prefix)) ret.add(tag);
        }

        return ret;
    }

    private static long parseWithSuffix(String s) throws NumberFormatException {
        if (s.isEmpty()) throw new NumberFormatException();

        // common suffixes: k,m,b,t,q,qq where each step multiplies by 1000
        long mult = 1L;
        if (s.endsWith("qq")) {
            mult = 1_000_000_000_000_000_000L; // 1e18
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("q")) {
            mult = 1_000_000_000_000_000L; // 1e15
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("t")) {
            mult = 1_000_000_000_000L; // 1e12
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            mult = 1_000_000_000L; // 1e9
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            mult = 1_000_000L; // 1e6
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("k")) {
            mult = 1_000L; // 1e3
            s = s.substring(0, s.length() - 1);
        }

        long val = Long.parseLong(s);
        if (val > Long.MAX_VALUE/mult) return Long.MAX_VALUE;

        return val * mult;
    }

    // Lightweight wrapper to list item registry names without importing ResourceLocation everywhere
    private static class ResourceLocationWrapper {
        private final String name;

        ResourceLocationWrapper(String n) { this.name = n; }

        public String toString() { return name; }

        static Iterable<ResourceLocationWrapper> listItemRegistry() {
            List<ResourceLocationWrapper> out = new ArrayList<>();
            for (ResourceLocation rl : Item.REGISTRY.getKeys()) {
                out.add(new ResourceLocationWrapper(rl.toString()));
            }

            return out;
        }
    }
}
