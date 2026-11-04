package com.cells.blocks.interfacebase.managers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.InterfaceRatioChannel;
import com.cells.items.upgrades.ratio.RatioCardRecipe;
import com.cells.items.upgrades.ratio.RatioCardRecipe.Ingredient;


/**
 * Encoded input batches and unfinished transfers for a pattern in the Ratio Card.
 */
public final class InterfaceRatioPattern {

    public final RatioCardRecipe recipe;

    // Track unfinished quantities while their resources stay in the interface inventory
    private final Map<EnumFacing, long[]> pending = new EnumMap<>(EnumFacing.class);

    public InterfaceRatioPattern(RatioCardRecipe recipe) {
        this.recipe = recipe;
    }

    public boolean hasFilters(AbstractResourceInterfaceLogic<?, ?, ?> context) {
        if (this.recipe.inputs.isEmpty()) return false;

        for (Ingredient input : this.recipe.inputs) {
            InterfaceRatioChannel<?, ?, ?> channel = context.getRatioChannel(input.type);
            if (channel == null || channel.getSlot(input.stack) < 0) return false;
        }

        return true;
    }

    private long getBatchCount(AbstractResourceInterfaceLogic<?, ?, ?> context,
                               EnumFacing facing, int quantity, int keepQuantity) {
        long batches = quantity;
        for (Ingredient input : this.recipe.inputs) {
            InterfaceRatioChannel<?, ?, ?> channel = context.getRatioChannel(input.type);
            long available = Math.min(quantity, channel.getAmount(input.stack));
            if (keepQuantity > 0) {
                long adjacent = channel.getAdjacentAmount(facing, input.stack);
                available = Math.min(available, Math.max(0, keepQuantity - adjacent));
            }

            int limit = channel.getInsertionLimit(facing, input.stack, (int) available);
            batches = Math.min(batches, Math.min(available, limit) / input.stack.getStackSize());
            if (batches <= 0) return 0;
        }

        return batches;
    }

    public boolean push(AbstractResourceInterfaceLogic<?, ?, ?> context,
                        EnumFacing facing, int quantity, int keepQuantity) {
        if (!hasFilters(context)) return false;

        long[] remaining = this.pending.get(facing);
        if (remaining == null) {
            long batches = getBatchCount(context, facing, quantity, keepQuantity);
            if (batches <= 0) return false;

            remaining = new long[this.recipe.inputs.size()];
            for (int index = 0; index < remaining.length; index++) {
                remaining[index] = this.recipe.inputs.get(index).stack.getStackSize() * batches;
            }
        }

        List<InterfaceRatioChannel<?, ?, ?>> channels = new ArrayList<>();
        for (Ingredient input : this.recipe.inputs) {
            InterfaceRatioChannel<?, ?, ?> channel = context.getRatioChannel(input.type);
            if (channel != context.getRatioChannel() && !channels.contains(channel)) channels.add(channel);
        }

        boolean didWork;
        try {
            for (InterfaceRatioChannel<?, ?, ?> channel : channels) channel.beginPush();
            didWork = deliver(context, facing, remaining, quantity, keepQuantity);
        } finally {
            for (InterfaceRatioChannel<?, ?, ?> channel : channels) channel.finishPush();
        }

        boolean complete = true;
        for (long amount : remaining) {
            if (amount > 0) {
                complete = false;
                break;
            }
        }

        if (complete) this.pending.remove(facing);
        else if (didWork) this.pending.put(facing, remaining);

        return didWork;
    }

    private boolean deliver(AbstractResourceInterfaceLogic<?, ?, ?> context, EnumFacing facing,
                            long[] remaining, int quantity, int keepQuantity) {
        boolean didWork = false;
        for (int index = 0; index < remaining.length; index++) {
            if (remaining[index] <= 0) continue;

            Ingredient input = this.recipe.inputs.get(index);
            InterfaceRatioChannel<?, ?, ?> channel = context.getRatioChannel(input.type);
            long available = Math.min(remaining[index], channel.getAmount(input.stack));
            if (keepQuantity > 0) {
                long adjacent = channel.getAdjacentAmount(facing, input.stack);
                available = Math.min(available, Math.max(0, keepQuantity - adjacent));
            }

            int amount = (int) Math.min(quantity, available);
            if (amount <= 0) break;

            long accepted = channel.push(facing, input.stack, amount);
            if (accepted <= 0) break;

            remaining[index] -= accepted;
            didWork = true;
            if (remaining[index] > 0) break;
        }

        return didWork;
    }

    public boolean hasPending(EnumFacing facing) {
        return this.pending.containsKey(facing);
    }

    public void readFromNBT(NBTTagList transfers) {
        for (int index = 0; index < transfers.tagCount(); index++) {
            NBTTagCompound transfer = transfers.getCompoundTagAt(index);
            if (this.recipe.inputs.isEmpty() || transfer.getInteger("slot") != this.recipe.slot) continue;

            ItemStack pattern = new ItemStack(transfer.getCompoundTag("pattern"));
            if (!ItemStack.areItemsEqual(this.recipe.pattern, pattern)
                    || !ItemStack.areItemStackTagsEqual(this.recipe.pattern, pattern)) continue;

            int side = transfer.getInteger("side");
            if (side < 0 || side >= EnumFacing.VALUES.length) continue;

            long[] remaining = new long[this.recipe.inputs.size()];
            NBTTagCompound amounts = transfer.getCompoundTag("remaining");
            boolean incomplete = false;
            for (int input = 0; input < remaining.length; input++) {
                remaining[input] = Math.max(0, Math.min(Integer.MAX_VALUE, amounts.getLong("#" + input)));
                incomplete |= remaining[input] > 0;
            }

            if (incomplete) this.pending.put(EnumFacing.VALUES[side], remaining);
        }
    }

    public void writeToNBT(NBTTagList transfers) {
        for (Map.Entry<EnumFacing, long[]> entry : this.pending.entrySet()) {
            NBTTagCompound transfer = new NBTTagCompound();
            transfer.setInteger("side", entry.getKey().ordinal());
            transfer.setInteger("slot", this.recipe.slot);
            transfer.setTag("pattern", this.recipe.pattern.writeToNBT(new NBTTagCompound()));
            NBTTagCompound remaining = new NBTTagCompound();
            for (int input = 0; input < entry.getValue().length; input++) {
                remaining.setLong("#" + input, entry.getValue()[input]);
            }

            transfer.setTag("remaining", remaining);
            transfers.appendTag(transfer);
        }
    }
}
