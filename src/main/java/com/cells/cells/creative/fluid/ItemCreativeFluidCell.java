package com.cells.cells.creative.fluid;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import appeng.api.implementations.items.IItemGroup;
import appeng.core.localization.GuiText;
import appeng.util.ReadableNumberConverter;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;
import com.cells.gui.CellsGuiHandler;


/**
 * Creative ME Fluid Cell item.
 * <p>
 * A cell that exposes Long.MAX_VALUE/2 of each "partitioned" fluid but accepts nothing.
 * Only configurable in creative mode.
 */
public class ItemCreativeFluidCell extends Item implements IItemGroup {

    public ItemCreativeFluidCell() {
        setMaxStackSize(1);
        setHasSubtypes(false);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "creative_fluid_cell");
        setTranslationKey(Tags.MODID + ".creative_fluid_cell");
    }

    // =====================
    // Right-click behavior
    // =====================

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        // Only open GUI in creative mode
        if (!player.isCreative()) return new ActionResult<>(EnumActionResult.PASS, stack);

        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_CREATIVE_FLUID_CELL,
                world, hand.ordinal(), 0, 0);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        // Only open GUI in creative mode
        if (!player.isCreative()) return EnumActionResult.PASS;

        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_CREATIVE_FLUID_CELL,
                world, hand.ordinal(), 0, 0);
        }

        return EnumActionResult.SUCCESS;
    }

    // =====================
    // Tooltip
    // =====================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        // Show what the cell does
        tooltip.add("§7" + I18n.format("tooltip.cells.creative_fluid_cell.info"));

        // Show amount exposed per type
        String amountStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(CreativeFluidCellInventory.REPORTED_AMOUNT);
        CreativeFluidCellFilterHandler filterHandler = new CreativeFluidCellFilterHandler(stack);
        int filterCount = filterHandler.getFilterCount();
        tooltip.add("§b" + I18n.format("tooltip.cells.creative_fluid_cell.exposes", amountStr, filterCount));

        tooltip.add("");

        // Creative mode hint
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null && player.isCreative()) {
            tooltip.add("§a" + I18n.format("tooltip.cells.creative_fluid_cell.creative_hint"));
        } else {
            tooltip.add("§c" + I18n.format("tooltip.cells.creative_fluid_cell.survival_hint"));
        }

        // Show fluid list when shift is held
        if (filterCount > 0 && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            tooltip.add("");
            for (int i = 0; i < filterHandler.getSlots(); i++) {
                FluidStack filterFluid = filterHandler.getFluidInSlot(i);
                if (filterFluid != null) {
                    tooltip.add("§7- §9" + filterFluid.getLocalizedName());
                }
            }
        }

        // JEI Cell Preview hint
        addJeiCellViewHint(tooltip);
    }

    /**
     * Add JEI keybind hint for cell view feature.
     * Separated to handle JEI not being loaded gracefully.
     */
    @SideOnly(Side.CLIENT)
    private void addJeiCellViewHint(List<String> tooltip) {
        try {
            String keybind = mezz.jei.config.KeyBindings.showRecipe.getDisplayName();
            tooltip.add("");
            tooltip.add(I18n.format("tooltip.cells.jei_view_contents", keybind));
        } catch (NoClassDefFoundError e) {
            // JEI not loaded, skip hint
        }
    }

    // =====================
    // IItemGroup implementation
    // =====================

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }
}
