package com.cells.cells.creative;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.lwjgl.input.Keyboard;

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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.items.IItemGroup;
import appeng.core.localization.GuiText;
import appeng.util.ReadableNumberConverter;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Abstract Creative Cell item.
 * <p>
 * A cell that exposes Long.MAX_VALUE/2 of each "partitioned" content but voids matching inserts.
 * Only configurable in creative mode.
 */
public abstract class AbstractCreativeCellItem<T, H extends AbstractCreativeCellFilterHandler<T, ?>> extends Item implements IItemGroup {

    private final int guiId;
    private final String typeKey;
    private final Function<ItemStack, H> filterFactory;

    protected AbstractCreativeCellItem(String registryName, int guiId, String typeKey,
                                       Function<ItemStack, H> filterFactory) {
        setMaxStackSize(1);
        setHasSubtypes(false);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, registryName);
        setTranslationKey(Tags.MODID + ".creative_cell." + typeKey);
        this.guiId = guiId;
        this.typeKey = typeKey;
        this.filterFactory = filterFactory;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (!player.isCreative()) return new ActionResult<>(EnumActionResult.PASS, stack);

        if (!world.isRemote) {
            player.openGui(Cells.instance, this.guiId, world, hand.ordinal(), 0, 0);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        if (!player.isCreative()) return EnumActionResult.PASS;

        // If not sneaking, let the block handle the interaction (e.g., open chest/machine GUI)
        if (!player.isSneaking()) return EnumActionResult.PASS;

        if (!world.isRemote) {
            player.openGui(Cells.instance, this.guiId, world, hand.ordinal(), 0, 0);
        }

        return EnumActionResult.SUCCESS;
    }

    /**
     * JEI hint helper (available for subclasses to call from their tooltip code).
     */
    @SideOnly(Side.CLIENT)
    protected void addJeiCellViewHint(List<String> tooltip) {
        try {
            String keybind = mezz.jei.config.KeyBindings.showRecipe.getDisplayName();
            tooltip.add("");
            tooltip.add(I18n.format("tooltip.cells.jei_view_contents", keybind));
        } catch (NoClassDefFoundError e) {
            // JEI not loaded, skip hint
        }
    }

    /**
     * Return a formatted (including color codes if desired) display string for a single filter stack.
     */
    protected abstract String formatStackDisplay(T stack);

    /**
     * Check if a filter stack is empty/null and should be skipped in tooltip display.
     * Subclasses should override if their stack type uses a different emptiness check.
     */
    protected abstract boolean isFilterEmpty(T filter);

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        String unitName = I18n.format("cells.unit_name." + this.typeKey);
        String unitNames = I18n.format("cells.unit_names." + this.typeKey);

        // Show what the cell does
        tooltip.add("§7" + I18n.format("tooltip.cells.creative_cell.info", unitName, unitNames));

        // Show amount exposed per type
        String amountStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(AbstractCreativeCellInventory.REPORTED_AMOUNT);
        H filterHandler = this.filterFactory.apply(stack);
        int filterCount = filterHandler.getFilterCount();
        tooltip.add("§b" + I18n.format("tooltip.cells.creative_cell.exposes", amountStr, unitNames, filterCount));

        tooltip.add("");

        // Creative mode hint
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player != null && player.isCreative()) {
            tooltip.add("§a" + I18n.format("tooltip.cells.creative_cell.creative_hint"));
        } else {
            tooltip.add("§c" + I18n.format("tooltip.cells.creative_cell.survival_hint"));
        }

        // Show list when shift is held
        if (filterCount > 0 && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            tooltip.add("");
            for (int i = 0; i < filterHandler.getSlots(); i++) {
                T filter = filterHandler.getStackInSlot(i);
                if (filter != null && !isFilterEmpty(filter)) {
                    tooltip.add("§7- " + formatStackDisplay(filter));
                }
            }
        }

        // JEI Cell Preview hint
        addJeiCellViewHint(tooltip);
    }

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }
}
