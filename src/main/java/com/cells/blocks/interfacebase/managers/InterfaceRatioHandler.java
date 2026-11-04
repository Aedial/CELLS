package com.cells.blocks.interfacebase.managers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import appeng.api.storage.data.IAEStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.items.upgrades.ratio.RatioCardInventory;
import com.cells.items.upgrades.ratio.RatioCardRecipe;
import com.cells.items.upgrades.ratio.RatioCardRecipe.Ingredient;


/**
 * Pattern filter merging and ordered Push Card batches for each destination face.
 */
public final class InterfaceRatioHandler {

    public static final String NBT_KEY = "ratioTransfers";

    private final List<InterfaceRatioPattern> patterns = new ArrayList<>();
    private final Set<String> mergedChannels = new HashSet<>();
    private ItemStack cachedCard = ItemStack.EMPTY;
    private NBTTagCompound savedTransfers;
    private boolean loaded;
    private long lastPushTick = Long.MIN_VALUE;

    public void refresh(ItemStack card, World world) {
        if (world == null) return;

        if (this.loaded && ItemStack.areItemsEqual(this.cachedCard, card)
                && ItemStack.areItemStackTagsEqual(this.cachedCard, card)) return;

        if (this.loaded) this.savedTransfers = null;
        this.cachedCard = card.copy();
        this.patterns.clear();
        this.mergedChannels.clear();
        this.lastPushTick = Long.MIN_VALUE;
        if (!card.isEmpty()) {
            RatioCardInventory inventory = new RatioCardInventory(card);
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack pattern = inventory.getStackInSlot(slot);
                if (!pattern.isEmpty()) {
                    this.patterns.add(new InterfaceRatioPattern(RatioCardRecipe.read(slot, pattern, world)));
                }
            }
        }

        this.loaded = true;
        if (this.savedTransfers != null) {
            NBTTagList transfers = this.savedTransfers.getTagList("transfers", Constants.NBT.TAG_COMPOUND);
            for (InterfaceRatioPattern pattern : this.patterns) pattern.readFromNBT(transfers);
            this.savedTransfers = null;
        }
    }

    public void mergeFilters(AbstractResourceInterfaceLogic<?, ?, ?> channel) {
        List<IAEStack<?>> inputs = new ArrayList<>();
        for (InterfaceRatioPattern pattern : this.patterns) {
            for (Ingredient input : pattern.recipe.inputs) {
                if (channel.getTypeName().equals(input.type.getName())) inputs.add(input.stack);
            }
        }

        channel.getRatioChannel().mergeFilters(inputs);
        this.mergedChannels.add(channel.getTypeName());
    }

    public int getInactiveMask(AbstractResourceInterfaceLogic<?, ?, ?> context) {
        int mask = 0;
        for (InterfaceRatioPattern pattern : this.patterns) {
            if (!pattern.hasFilters(context)) mask |= 1 << pattern.recipe.slot;
        }

        return mask;
    }

    public void initializeFilters(List<IInterfaceLogic> channels) {
        for (IInterfaceLogic channel : channels) {
            if (!this.mergedChannels.contains(channel.getTypeName())) {
                mergeFilters((AbstractResourceInterfaceLogic<?, ?, ?>) channel);
            }
        }
    }

    public boolean push(AbstractResourceInterfaceLogic<?, ?, ?> context,
                        AbstractResourceInterfaceLogic.Host host, int quantity, int keepQuantity) {
        if (quantity <= 0 || host.getHostWorld() == null) return false;

        List<IInterfaceLogic> channels = context.getRatioChannels();
        if (channels.size() > 1) {
            // Share each push attempt across the Universal Interface's resource callbacks
            long tick = host.getHostWorld().getTotalWorldTime();
            if (this.lastPushTick == tick) return false;

            this.lastPushTick = tick;
        }

        boolean didWork = false;
        try {
            Map<EnumFacing, Integer> firstPatterns = new EnumMap<>(EnumFacing.class);
            for (EnumFacing facing : host.getTargetFacings()) {
                for (int index = 0; index < this.patterns.size(); index++) {
                    if (!this.patterns.get(index).hasPending(facing)) continue;

                    firstPatterns.put(facing, index);
                    break;
                }
            }

            EnumSet<EnumFacing> blocked = EnumSet.noneOf(EnumFacing.class);
            for (int index = 0; index < this.patterns.size(); index++) {
                InterfaceRatioPattern pattern = this.patterns.get(index);
                for (EnumFacing facing : host.getTargetFacings()) {
                    if (blocked.contains(facing) || index < firstPatterns.getOrDefault(facing, 0)) continue;

                    didWork |= pattern.push(context, facing, quantity, keepQuantity);
                    if (pattern.hasPending(facing)) blocked.add(facing);
                }
            }
        } finally {
            for (IInterfaceLogic channel : channels) {
                ((AbstractResourceInterfaceLogic<?, ?, ?>) channel).getRatioChannel().flushCaches();
            }
        }

        return didWork;
    }

    public void readFromNBT(NBTTagCompound data) {
        this.patterns.clear();
        this.mergedChannels.clear();
        NBTTagList transfers = data.getTagList("transfers", Constants.NBT.TAG_COMPOUND);
        this.savedTransfers = transfers.tagCount() > 0 ? data.copy() : null;
        this.loaded = false;
    }

    public void writeToNBT(NBTTagCompound data, String key) {
        if (!this.loaded && this.savedTransfers != null) {
            data.setTag(key, this.savedTransfers.copy());
            return;
        }

        NBTTagList transfers = new NBTTagList();
        for (InterfaceRatioPattern pattern : this.patterns) pattern.writeToNBT(transfers);
        if (transfers.tagCount() == 0) {
            data.removeTag(key);
            return;
        }

        NBTTagCompound saved = new NBTTagCompound();
        saved.setTag("transfers", transfers);
        data.setTag(key, saved);
    }
}
