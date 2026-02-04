package com.cells.commands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/**
 * /fillCell <item id> <count>
 * Creative-only command. Uses AE2 insertion APIs to fill the storage cell in hand.
 */
public class FillCellCommand extends CommandBase {

    @Override
    public String getName() {
        return "fillCell";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/fillCell <item id>|<fluid id> <count> (with k,m,b,t,q,qq suffixes)";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("This command must be run by a player."));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
            return;
        }

        String itemId = args[0];
        String countStr = args[1].toLowerCase();

        // Try resolve item or fluid by id (may be null depending on request)
        Item item = Item.getByNameOrId(itemId);
        Fluid fluid = FluidRegistry.getFluid(itemId);

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
        if (held == null || held.isEmpty()) {
            sender.sendMessage(new TextComponentString("Hold a storage cell in your hand."));
            return;
        }

        IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEInventoryHandler<IAEItemStack> itemInv = AEApi.instance().registries().cell().getCellInventory(held, null, itemChannel);

        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IMEInventoryHandler<IAEFluidStack> fluidInv = AEApi.instance().registries().cell().getCellInventory(held, null, fluidChannel);

        // Decide insertion path based on cell capabilities.
        boolean canItem = itemInv != null;
        boolean canFluid = fluidInv != null;
        if (!canItem && !canFluid) {
            sender.sendMessage(new TextComponentString("Held item cannot store items or fluids."));
            return;
        }

        if (canItem) {
            // Item cell
            if (item == null) {
                sender.sendMessage(new TextComponentString("Unknown item: " + itemId));
                return;
            }

            ItemStack toInsert = new ItemStack(item, 1);
            IAEItemStack aeStack = itemChannel.createStack(toInsert);
            if (aeStack == null) {
                sender.sendMessage(new TextComponentString("Failed to create AE item stack for " + itemId));
                return;
            }

            aeStack.setStackSize(count);
            IAEItemStack remainder = itemInv.injectItems(aeStack, Actionable.MODULATE, null);
            if (remainder == null) {
                sender.sendMessage(new TextComponentString("Filled cell with " + args[1] + " of " + itemId));
                return;
            }

            long notInserted = remainder.getStackSize();
            if (notInserted <= 0) {
                sender.sendMessage(new TextComponentString("Filled cell with " + args[1] + " of " + itemId));
            } else {
                sender.sendMessage(new TextComponentString("Partially filled cell. Could not insert " + notInserted + " items."));
            }

            return;
        }

        if (canFluid) {
            // Fluid cell
            if (fluid == null) {
                sender.sendMessage(new TextComponentString("Unknown fluid: " + itemId));
                return;
            }

            int amount = (int) Math.min(count, (long) Integer.MAX_VALUE);
            FluidStack fs = new FluidStack(fluid, amount);
            IAEFluidStack aeFluid = fluidChannel.createStack(fs);
            if (aeFluid == null) {
                sender.sendMessage(new TextComponentString("Failed to create AE fluid stack for " + itemId));
                return;
            }

            aeFluid.setStackSize(count);
            IAEFluidStack remainder = fluidInv.injectItems(aeFluid, Actionable.MODULATE, null);
            if (remainder == null) {
                sender.sendMessage(new TextComponentString("Filled fluid cell with " + args[1] + " mB of " + itemId));
                return;
            }

            long notInserted = remainder.getStackSize();
            if (notInserted <= 0) {
                sender.sendMessage(new TextComponentString("Filled fluid cell with " + args[1] + " mB of " + itemId));
            } else {
                sender.sendMessage(new TextComponentString("Partially filled fluid cell. Could not insert " + notInserted + " mB."));
            }

            return;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        List<String> ret = new ArrayList<>();
        if (args.length == 1) {
            String last = args[args.length - 1];

            // Try to detect held cell type from the command sender (if player)
            if (sender.getCommandSenderEntity() instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
                ItemStack held = player.getHeldItemMainhand();

                if (held != null && !held.isEmpty()) {
                    IStorageChannel<IAEItemStack> itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                    IMEInventoryHandler<IAEItemStack> itemInv = AEApi.instance().registries().cell().getCellInventory(held, null, itemChannel);

                    IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
                    IMEInventoryHandler<IAEFluidStack> fluidInv = AEApi.instance().registries().cell().getCellInventory(held, null, fluidChannel);

                    if (itemInv != null) {
                        // item cell: suggest item ids
                        for (ResourceLocationWrapper rl : ResourceLocationWrapper.listItemRegistry()) {
                            String name = rl.toString();
                            if (name.startsWith(last)) ret.add(name);
                        }

                        return getListOfStringsMatchingLastWord(args, ret);
                    }

                    if (fluidInv != null) {
                        // fluid cell: suggest fluid ids
                        for (String fname : FluidRegistry.getRegisteredFluids().keySet()) {
                            if (fname.startsWith(last)) ret.add(fname);
                        }

                        return getListOfStringsMatchingLastWord(args, ret);
                    }
                }
            }
        }

        return getListOfStringsMatchingLastWord(args, ret);
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
