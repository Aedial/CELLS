package com.cells.integration.thaumicenergistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.EssentiaStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.items.ItemRecoveryContainer;


/**
 * Pending essentia returns after redirect destinations and original sources refuse the remainder.
 * This buffer keeps track of essentia that could not be returned to its original source or redirected destination,
 * and ensures it is eventually returned when possible (or at least avoid transferring any more of it).
 */
public final class EssentiaRedirectBuffer {

    private static final String NBT_KEY = "essentiaRedirectBuffer";

    private final EssentiaInterfaceLogic logic;
    private final AbstractResourceInterfaceLogic.Host host;
    private final Map<String, PendingReturn> pending = new LinkedHashMap<>();
    private String activeAspect;
    private boolean transferring;
    private boolean removed;

    public EssentiaRedirectBuffer(EssentiaInterfaceLogic logic, AbstractResourceInterfaceLogic.Host host) {
        this.logic = logic;
        this.host = host;
    }

    public boolean isEmpty() {
        return this.pending.isEmpty();
    }

    public boolean isBlocked(Aspect aspect) {
        return aspect != null && (aspect.getTag().equals(this.activeAspect)
            || this.pending.containsKey(aspect.getTag()));
    }

    public long getAmount() {
        long amount = 0;
        for (PendingReturn packet : this.pending.values()) {
            if (packet.amount > Long.MAX_VALUE - amount) return Long.MAX_VALUE;

            amount += packet.amount;
        }

        return amount;
    }

    public int getTypes() {
        return this.pending.size();
    }

    public long transfer(Object source, Object destination, EssentiaStackKey key, int amount,
                         EnumFacing sourceSide, EnumFacing destinationSide) {
        Aspect aspect = key.getAspect();
        if (this.transferring || this.removed || amount <= 0 || aspect == null || isBlocked(aspect)) return 0;

        World world = this.host.getHostWorld();
        if (world == null || world.isRemote) return 0;

        BlockPos pos = this.host.getHostPos();
        if (pos == null) return 0;
        if (!isLiveContainer(world, pos.offset(sourceSide), source)
                || !isLiveContainer(world, pos.offset(destinationSide), destination)) return 0;

        PendingReturn packet = new PendingReturn(aspect.getTag(), world.provider.getDimension(),
            pos.offset(sourceSide), sourceSide, 0);
        this.transferring = true;
        try {
            // Remove from the source before crediting the redirect destination
            packet.amount = this.logic.extractResourceFromHandler(source, key, amount, sourceSide);
            if (packet.amount <= 0) return 0;

            this.activeAspect = packet.aspectTag;
            long accepted = 0;
            if (!this.removed && isLiveContainer(world, pos.offset(destinationSide), destination)) {
                int offered = (int) Math.min(packet.amount, Integer.MAX_VALUE);
                accepted = this.logic.insertResourceIntoHandler(destination,
                    new EssentiaStack(aspect, 1), offered, destinationSide);
                accepted = Math.max(0, Math.min(offered, accepted));
            }

            packet.amount -= accepted;
            returnToSource(world, packet);
            return accepted;
        } finally {
            try {
                retain(packet, world, pos);
                if (packet.amount > 0 && !this.removed) {
                    this.host.markDirtyAndSave();
                    this.logic.wakeUpIfAdaptive();
                }
            } finally {
                this.activeAspect = null;
                this.transferring = false;
            }
        }
    }

    public boolean retryReturns() {
        if (this.transferring || this.removed || this.pending.isEmpty()) return false;

        World world = this.host.getHostWorld();
        if (world == null || world.isRemote) return false;

        BlockPos pos = this.host.getHostPos();
        if (pos == null) return false;

        boolean changed = false;
        this.transferring = true;
        try {
            for (PendingReturn packet : new ArrayList<>(this.pending.values())) {
                if (this.removed) break;

                // Keep the active quantity out of removal drops while the source handles it
                this.pending.remove(packet.aspectTag);
                this.activeAspect = packet.aspectTag;
                long previousAmount = packet.amount;
                try {
                    returnToSource(world, packet);
                } finally {
                    retain(packet, world, pos);
                    this.activeAspect = null;
                    if (packet.amount != previousAmount) {
                        changed = true;
                        this.host.markDirtyAndSave();
                    }
                }
            }
        } finally {
            this.activeAspect = null;
            this.transferring = false;
        }

        return changed;
    }

    private void returnToSource(World world, PendingReturn packet) {
        if (packet.amount <= 0 || world.provider.getDimension() != packet.dimension
                || !world.isBlockLoaded(packet.sourcePos)) return;

        Aspect aspect = Aspect.getAspect(packet.aspectTag);
        TileEntity source = world.getTileEntity(packet.sourcePos);
        if (aspect == null || !(source instanceof IAspectContainer) || source.isInvalid()) return;
        if (EssentiaAdjacentHandler.isContainerBlacklisted(world, packet.sourcePos, source)) return;

        int offered = (int) Math.min(packet.amount, Integer.MAX_VALUE);
        long accepted = this.logic.insertResourceIntoHandler(source,
            new EssentiaStack(aspect, 1), offered, packet.sourceSide);
        packet.amount -= Math.max(0, Math.min(offered, accepted));
    }

    private void retain(PendingReturn packet, World world, BlockPos pos) {
        if (packet.amount <= 0) return;

        if (this.removed) {
            // Recover remainders after a callback removes this interface
            Block.spawnAsEntity(world, pos, ItemRecoveryContainer.createForEssentia(packet.aspectTag, packet.amount));
            packet.amount = 0;
            return;
        }

        this.pending.put(packet.aspectTag, packet);
    }

    private boolean isLiveContainer(World world, BlockPos pos, Object container) {
        return container instanceof TileEntity && container instanceof IAspectContainer
            && !((TileEntity) container).isInvalid() && world.isBlockLoaded(pos)
            && world.getTileEntity(pos) == container;
    }

    public void getDrops(List<ItemStack> drops) {
        this.removed = true;
        for (PendingReturn packet : this.pending.values()) {
            ItemStack drop = ItemRecoveryContainer.createForEssentia(packet.aspectTag, packet.amount);
            if (!drop.isEmpty()) drops.add(drop);

            packet.amount = 0;
        }

        this.pending.clear();
        this.host.markDirtyAndSave();
    }

    public void readFromNBT(NBTTagCompound data) {
        this.pending.clear();
        NBTTagCompound buffer = data.getCompoundTag(this.host.getNBTKeyPrefix() + NBT_KEY);
        for (String aspectTag : buffer.getKeySet()) {
            NBTTagCompound tag = buffer.getCompoundTag(aspectTag);
            long amount = tag.getLong("amount");
            int side = tag.getInteger("sourceSide");
            if (aspectTag.isEmpty() || amount <= 0 || side < 0 || side >= EnumFacing.VALUES.length) continue;

            this.pending.put(aspectTag, new PendingReturn(aspectTag, tag.getInteger("dimension"),
                BlockPos.fromLong(tag.getLong("sourcePos")), EnumFacing.VALUES[side], amount));
        }
    }

    public void writeToNBT(NBTTagCompound data) {
        NBTTagCompound buffer = new NBTTagCompound();
        for (PendingReturn packet : this.pending.values()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("amount", packet.amount);
            tag.setInteger("dimension", packet.dimension);
            tag.setLong("sourcePos", packet.sourcePos.toLong());
            tag.setInteger("sourceSide", packet.sourceSide.getIndex());
            buffer.setTag(packet.aspectTag, tag);
        }

        data.setTag(this.host.getNBTKeyPrefix() + NBT_KEY, buffer);
    }

    private static final class PendingReturn {

        private final String aspectTag;
        private final int dimension;
        private final BlockPos sourcePos;
        private final EnumFacing sourceSide;
        private long amount;

        private PendingReturn(String aspectTag, int dimension, BlockPos sourcePos,
                              EnumFacing sourceSide, long amount) {
            this.aspectTag = aspectTag;
            this.dimension = dimension;
            this.sourcePos = sourcePos;
            this.sourceSide = sourceSide;
            this.amount = amount;
        }
    }
}
