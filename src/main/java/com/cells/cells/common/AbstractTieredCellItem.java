package com.cells.cells.common;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.util.Platform;

import com.cells.core.CellsCreativeTab;
import com.cells.util.CellDisassemblyHelper;


/**
 * Abstract base class for tiered storage cell items (using metadata for tiers).
 * <p>
 * Provides common functionality shared across all tiered cell types:
 * - Creative tab registration with all tiers
 * - Translation key with tier suffix
 * - Standard Item setup (max stack size, subtypes, creative tab)
 * - ICellWorkbenchItem implementation (FuzzyMode, config inventory)
 * - IItemGroup implementation
 * - Disassembly action handling (shift-right-click)
 * <p>
 * Subclasses must implement:
 * - {@link #getTierNames()} - array of tier name suffixes
 * - {@link #getUpgradesInventory(ItemStack)} - cell-specific upgrade slots
 * - {@link #disassembleCell(ItemStack, EntityPlayer)} - cell-specific disassembly
 * - {@link #addCellInformation(ItemStack, World, List, ITooltipFlag)} - cell-specific tooltip
 */
public abstract class AbstractTieredCellItem extends Item implements ICellWorkbenchItem, IItemGroup {

    public AbstractTieredCellItem() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
    }

    // =====================
    // Abstract methods for subclasses
    // =====================

    /**
     * Get the tier name suffixes for translation keys and creative tab.
     * e.g., {"1k", "4k", "16k", "64k", ...}
     */
    @Nonnull
    protected abstract String[] getTiers();

    /**
     * Perform cell-specific disassembly logic.
     * Called when the player shift-right-clicks with this cell.
     *
     * @param stack The cell ItemStack
     * @param player The player performing the action
     * @return true if disassembly was successful
     */
    protected abstract boolean disassembleCell(@Nonnull ItemStack stack, @Nonnull EntityPlayer player);

    /**
     * Add cell-specific tooltip information.
     * Called from addInformation after basic validation.
     */
    @SideOnly(Side.CLIENT)
    protected abstract void addCellInformation(@Nonnull ItemStack stack, World world,
                                                @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag);

    // =====================
    // Translation key with tier suffix
    // =====================

    @Override
    @Nonnull
    public String getTranslationKey(ItemStack stack) {
        String[] tierNames = getTiers();
        int meta = stack.getMetadata();

        if (meta >= 0 && meta < tierNames.length) return getTranslationKey() + "." + tierNames[meta];

        return getTranslationKey();
    }

    // =====================
    // Creative tab items
    // =====================

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        String[] tierNames = getTiers();
        for (int i = 0; i < tierNames.length; i++) items.add(new ItemStack(this, 1, i));
    }

    // =====================
    // Tooltip - delegates to subclass
    // =====================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        addCellInformation(stack, world, tooltip, flag);
    }

    // =====================
    // Disassembly (shift-right-click)
    // =====================

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        return CellDisassemblyHelper.handleRightClick(world, player, hand,
                stack -> disassembleCell(stack, player));
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        return CellDisassemblyHelper.handleUseFirst(player, hand, stack -> disassembleCell(stack, player));
    }

    // =====================
    // ICellWorkbenchItem - common implementation
    // =====================

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getConfigInventory(ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        String fz = Platform.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    // =====================
    // IItemGroup implementation
    // =====================

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    // =====================
    // Utility methods for subclasses
    // =====================

    /**
     * Get a value from an array by metadata, with fallback to index 0.
     */
    protected long getValueByMeta(@Nonnull ItemStack stack, long[] values) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < values.length) return values[meta];

        return values[0];
    }

    /**
     * Get the tier count (number of tiers available).
     */
    public int getNumTiers() {
        return getTiers().length;
    }
}
