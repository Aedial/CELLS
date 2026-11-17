package com.cells.scanner;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.common.util.Constants;

import com.cells.network.sync.ResourceType;


/**
 * Snapshot of the empty filtered slots in a resource interface buffer.
 */
public final class InterfaceScanEntry {

    public final BlockPos pos;
    public final int dimension;
    public final int facing;
    public final ResourceType resourceType;
    public final boolean export;
    public final boolean directionLabel;
    public final boolean typeLabel;
    public final ItemStack icon;
    public final List<ItemStack> missing;

    public InterfaceScanEntry(BlockPos pos, int dimension, int facing, ResourceType resourceType,
                              boolean export, boolean directionLabel, boolean typeLabel,
                              ItemStack icon, List<ItemStack> missing) {
        this.pos = pos.toImmutable();
        this.dimension = dimension;
        this.facing = facing;
        this.resourceType = resourceType;
        this.export = export;
        this.directionLabel = directionLabel;
        this.typeLabel = typeLabel;
        this.icon = icon.copy();
        this.missing = new ArrayList<>(missing);
    }

    public String getKey() {
        return this.dimension + ":" + this.pos.toLong() + ":" + this.facing
            + ":" + this.resourceType.name() + ":" + this.export;
    }

    public InterfaceScanEntry slice(int start, int end) {
        return new InterfaceScanEntry(this.pos, this.dimension, this.facing, this.resourceType,
            this.export, this.directionLabel, this.typeLabel, this.icon, this.missing.subList(start, end));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("pos", this.pos.toLong());
        tag.setInteger("dim", this.dimension);
        tag.setInteger("facing", this.facing);
        tag.setString("type", this.resourceType.name());
        tag.setBoolean("export", this.export);
        tag.setBoolean("directionLabel", this.directionLabel);
        tag.setBoolean("typeLabel", this.typeLabel);
        tag.setTag("icon", this.icon.writeToNBT(new NBTTagCompound()));

        NBTTagList missingList = new NBTTagList();
        for (ItemStack stack : this.missing) missingList.appendTag(stack.writeToNBT(new NBTTagCompound()));
        tag.setTag("missing", missingList);

        return tag;
    }

    public static InterfaceScanEntry readFromNBT(NBTTagCompound tag) {
        ResourceType type = ResourceType.valueOf(tag.getString("type"));
        List<ItemStack> missing = new ArrayList<>();
        NBTTagList missingList = tag.getTagList("missing", Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < missingList.tagCount(); index++) {
            ItemStack stack = new ItemStack(missingList.getCompoundTagAt(index));
            if (!stack.isEmpty()) missing.add(stack);
        }

        return new InterfaceScanEntry(BlockPos.fromLong(tag.getLong("pos")), tag.getInteger("dim"),
            tag.getInteger("facing"), type, tag.getBoolean("export"), tag.getBoolean("directionLabel"),
            tag.getBoolean("typeLabel"), new ItemStack(tag.getCompoundTag("icon")), missing);
    }
}
