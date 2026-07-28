package com.cells.cells.emc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

import com.latmod.mods.projectex.ProjectEXUtils;
import com.latmod.mods.projectex.integration.PersonalEMC;
import com.latmod.mods.projectex.tile.TileLink;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;

import com.cells.ItemRegistry;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;
import com.cells.items.ItemEmcCapacityCard;
import com.cells.util.CellMathHelper;
import com.cells.util.ItemStackKey;


/**
 * Workbench-editable item shell for the EMC cell.
 */
public class ItemEmcCell extends Item implements ICellWorkbenchItem, IItemGroup {

    private static final String NBT_OWNER_MOST = "EmcOwnerMost";
    private static final String NBT_OWNER_LEAST = "EmcOwnerLeast";
    private static final String NBT_OWNER_NAME = "EmcOwnerName";

    public ItemEmcCell() {
        setMaxStackSize(1);
        setHasSubtypes(false);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "emc_cell");
        setTranslationKey(Tags.MODID + ".emc_cell");
    }

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(ItemStack is) {
        return new EmcCellUpgradeInventory(is);
    }

    @Override
    public IItemHandler getConfigInventory(ItemStack is) {
        return new EmcCellFilterHandler(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        // Matching is always fuzzy because partition checks follow ProjectEX normalization,
        // not strict item-plus-NBT equality from the raw stack.
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
    }

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    @Nullable
    public static UUID getOwnerId(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_OWNER_MOST) || !tag.hasKey(NBT_OWNER_LEAST)) return null;

        return new UUID(tag.getLong(NBT_OWNER_MOST), tag.getLong(NBT_OWNER_LEAST));
    }

    @Nullable
    public static String getOwnerName(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_OWNER_NAME)) return null;

        String ownerName = tag.getString(NBT_OWNER_NAME);
        return ownerName.isEmpty() ? null : ownerName;
    }

    public static boolean ensureOwner(ItemStack stack, @Nullable EntityPlayer player) {
        if (player == null) return false;
        if (getOwnerId(stack) != null) return true;

        NBTTagCompound tag = Platform.openNbtData(stack);
        UUID ownerId = player.getUniqueID();
        tag.setLong(NBT_OWNER_MOST, ownerId.getMostSignificantBits());
        tag.setLong(NBT_OWNER_LEAST, ownerId.getLeastSignificantBits());
        tag.setString(NBT_OWNER_NAME, player.getName());
        return true;
    }

    public static boolean ensureOwner(ItemStack stack, @Nullable IActionSource src) {
        if (src == null || !src.player().isPresent()) return false;

        return ensureOwner(stack, src.player().get());
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) ensureOwner(stack, player);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        if (world.isRemote) return;
        if (!(entity instanceof EntityPlayer)) return;

        ensureOwner(stack, (EntityPlayer) entity);
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        if (!player.isSneaking()) return EnumActionResult.PASS;

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof TileLink)) return EnumActionResult.PASS;

        TileLink link = (TileLink) tileEntity;
        // TODO: Maybe gate behind a config?
        //       Users of the same Team should already have the same pool, but they would lose it if the owner quits.
        //       Currently, it mirrors BlockLink's owner-only access so shift-copy does not expose other users.
        if (!player.getUniqueID().equals(link.owner)) return EnumActionResult.PASS;

        if (!world.isRemote) copyPartitionFromLink(player.getHeldItem(hand), player, link);

        return EnumActionResult.SUCCESS;
    }

    @Optional.Method(modid = "jei")
    @SideOnly(Side.CLIENT)
    private void addJeiCellViewHint(@Nonnull List<String> tooltip) {
        String keybind = mezz.jei.config.KeyBindings.showRecipe.getDisplayName();
        tooltip.add("");
        tooltip.add(I18n.format("tooltip.cells.jei_view_contents", keybind));
    }

    @Optional.Method(modid = "jei")
    @SideOnly(Side.CLIENT)
    private static boolean isJeiCellViewEnabled() {
        return com.cells.integration.jei.CellsJEIPlugin.enableCellView;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        tooltip.add("§7" + I18n.format("tooltip.cells.emc_cell.info"));

        PartitionTooltipInfo partitionInfo = getPartitionTooltipInfo(stack, world);

        ItemEmcCapacityCard capacityCard = ItemRegistry.EMC_CAPACITY_CARD;
        if (capacityCard != null) {
            tooltip.add(
                "§b" + I18n.format(
                    "tooltip.cells.emc_cell.slots",
                    partitionInfo.getLearnedFilterCount() + "/" + partitionInfo.unlockedSlots));
        }

        String ownerName = getOwnerName(stack);
        if (ownerName == null) {
            tooltip.add("§c" + I18n.format("tooltip.cells.emc_cell.unbound"));
        } else {
            tooltip.add("§a" + I18n.format("tooltip.cells.emc_cell.owner", ownerName));
        }

        long bufferedEmc = CellMathHelper.loadLong(Platform.openNbtData(stack), EmcCellInventory.NBT_STORED_EMC);
        if (bufferedEmc > 0) {
            tooltip.add("§6" + I18n.format("tooltip.cells.emc_cell.buffered", bufferedEmc));
        }

        addUnlearnedPartitionTooltip(partitionInfo, tooltip);

        if (Loader.isModLoaded("jei") && isJeiCellViewEnabled()) addJeiCellViewHint(tooltip);
    }

    @SideOnly(Side.CLIENT)
    private void addUnlearnedPartitionTooltip(@Nonnull PartitionTooltipInfo partitionInfo,
                                              @Nonnull List<String> tooltip) {
        List<String> unlearnedFilters = partitionInfo.unlearnedFilters;
        if (unlearnedFilters.isEmpty()) return;

        tooltip.add("");
        tooltip.add("§c" + I18n.format("tooltip.cells.emc_cell.unlearned", unlearnedFilters.size()));

        if (!isShiftDown()) {
            tooltip.add("§7" + I18n.format("tooltip.cells.emc_cell.unlearned_hint"));
            return;
        }

        for (String filterName : unlearnedFilters) tooltip.add("§e - " + filterName);
        tooltip.add("");
    }

    @Nonnull
    @SideOnly(Side.CLIENT)
    private PartitionTooltipInfo getPartitionTooltipInfo(@Nonnull ItemStack stack, @Nullable World world) {
        World tooltipWorld = getTooltipWorld(world);
        UUID ownerId = getOwnerId(stack);
        IKnowledgeProvider provider = tooltipWorld != null && ownerId != null ? PersonalEMC.get(tooltipWorld, ownerId) : null;

        EmcCellFilterHandler filterHandler = new EmcCellFilterHandler(stack);
        Set<ItemStackKey> seenFilters = new LinkedHashSet<>();
        List<String> unlearnedFilters = new ArrayList<>();
        int unlockedSlots = filterHandler.getUnlockedSlots();
        int configuredFilters = 0;

        for (int slot = 0; slot < unlockedSlots; slot++) {
            ItemStack filterStack = filterHandler.getStackInSlot(slot);
            if (filterStack.isEmpty()) continue;

            ItemStack normalized = ProjectEXUtils.fixOutput(filterStack);
            if (normalized.isEmpty()) continue;

            ItemStackKey key = ItemStackKey.of(normalized);
            if (key == null || !seenFilters.add(key)) continue;
            configuredFilters++;
            if (provider == null || provider.hasKnowledge(normalized)) continue;

            unlearnedFilters.add(filterStack.getDisplayName());
        }

        return new PartitionTooltipInfo(unlockedSlots, configuredFilters, unlearnedFilters);
    }

    @SideOnly(Side.CLIENT)
    private static class PartitionTooltipInfo {

        private int unlockedSlots;
        private int configuredFilters;
        private List<String> unlearnedFilters;

        private PartitionTooltipInfo(int unlockedSlots, int configuredFilters, List<String> unlearnedFilters) {
            this.unlockedSlots = unlockedSlots;
            this.configuredFilters = configuredFilters;
            this.unlearnedFilters = unlearnedFilters;
        }

        private int getLearnedFilterCount() {
            return this.configuredFilters - this.unlearnedFilters.size();
        }
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    private World getTooltipWorld(@Nullable World world) {
        if (world != null) return world;

        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null ? minecraft.world : null;
    }

    @SideOnly(Side.CLIENT)
    private boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private void copyPartitionFromLink(@Nonnull ItemStack cellStack, @Nonnull EntityPlayer player, @Nonnull TileLink link) {
        ensureOwner(cellStack, player);

        EmcCellFilterHandler filterHandler = new EmcCellFilterHandler(cellStack);
        EmcCellFilterHandler.MergeResult result = filterHandler.mergeMissingStacks(Arrays.asList(link.outputSlots));
        int addedCount = result.getAddedCount();
        int blockedByCapacityCount = result.getBlockedByCapacityCount();

        String prefix = "message.cells.emc_cell.link_copy";

        if (blockedByCapacityCount > 0) {
            TextComponentTranslation msg;
            if (addedCount > 0) {
                msg = new TextComponentTranslation(prefix + "_full", addedCount, blockedByCapacityCount);
            } else {
                msg = new TextComponentTranslation(prefix + "_full_none", blockedByCapacityCount);
            }
            player.sendStatusMessage(msg, true);

            return;
        }

        if (addedCount > 0) {
            player.sendStatusMessage(new TextComponentTranslation(prefix, addedCount), true);
            return;
        }

        player.sendStatusMessage(new TextComponentTranslation(prefix + "_none"), true);
    }
}