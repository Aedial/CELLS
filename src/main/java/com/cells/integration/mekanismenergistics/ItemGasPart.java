package com.cells.integration.mekanismenergistics;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;

import com.cells.Tags;
import com.cells.core.CellsCreativeTab;


/**
 * Item for CELLS gas parts. Uses damage values to distinguish between part types.
 * Registered only when MekanismEnergistics is loaded.
 */
public class ItemGasPart extends Item implements IPartItem<IPart> {

    public ItemGasPart() {
        this.setRegistryName(Tags.MODID, "gas_part");
        this.setTranslationKey(Tags.MODID + ".part.gas");
        this.setCreativeTab(CellsCreativeTab.instance);
        this.setHasSubtypes(true);
        this.setMaxDamage(0);
    }

    @Override
    @Nonnull
    public String getTranslationKey(ItemStack stack) {
        CellsGasPartType type = CellsGasPartType.getById(stack.getItemDamage());
        if (type != null) return type.getUnlocalizedName();

        return "item." + Tags.MODID + ".part.gas.invalid";
    }

    @Override
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!this.isInCreativeTab(tab)) return;

        for (CellsGasPartType type : CellsGasPartType.values()) {
            items.add(new ItemStack(this, 1, type.getBaseDamage()));
        }
    }

    @Nullable
    @Override
    public IPart createPartFromItemStack(ItemStack stack) {
        CellsGasPartType type = CellsGasPartType.getById(stack.getItemDamage());
        if (type == null) return null;

        Class<? extends IPart> partClass = type.getPartClass();
        if (partClass == null) return null;

        try {
            Constructor<? extends IPart> constructor = type.getConstructor();
            if (constructor == null) {
                constructor = partClass.getConstructor(ItemStack.class);
                type.setConstructor(constructor);
            }

            return constructor.newInstance(stack);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to create gas part from stack: " + stack, e);
        }
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                      @Nonnull EnumHand hand, @Nonnull EnumFacing facing,
                                      float hitX, float hitY, float hitZ) {
        return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, facing, player, hand, world);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        CellsGasPartType type = CellsGasPartType.getById(stack.getItemDamage());
        if (type == null) return;

        // Generate tooltip key based on part type
        String tooltipKey = getTooltipKeyForPartType(type);
        if (tooltipKey != null) tooltip.add("§7" + I18n.format(tooltipKey));
    }

    /**
     * Get the tooltip localization key for a given gas part type.
     */
    @Nullable
    private static String getTooltipKeyForPartType(CellsGasPartType type) {
        switch (type) {
            case GAS_IMPORT_INTERFACE:
                return "tooltip.cells.import_interface.gas.info";
            case GAS_EXPORT_INTERFACE:
                return "tooltip.cells.export_interface.gas.info";
            default:
                return null;
        }
    }

    /**
     * Register item models for all gas part types.
     */
    @SideOnly(Side.CLIENT)
    public void registerModels() {
        for (CellsGasPartType type : CellsGasPartType.values()) {
            List<ModelResourceLocation> models = type.getItemModels();
            if (!models.isEmpty()) {
                ModelLoader.setCustomModelResourceLocation(
                    this,
                    type.getBaseDamage(),
                    models.get(0)
                );
            }
        }
    }
}
