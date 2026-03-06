package com.cells.cells.configurable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.cells.common.INBTSizeProvider;
import com.cells.config.CellsConfig;
import com.cells.core.CellsCreativeTab;
import com.cells.gui.CellsGuiHandler;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.mixin.MixinState;
import com.cells.util.CellDisassemblyHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;
import com.cells.util.NBTSizeHelper;


/**
 * Configurable Storage Cell item.
 * <p>
 * A single-item cell that accepts any ME Storage Component to define its capacity
 * and storage channel (items or fluids). The user can configure a per-type capacity
 * limit via a GUI text field, and the cell has equal distribution built in.
 * <p>
 * Right-click opens the configuration GUI.
 * Shift-right-click disassembles the cell (returns housing, component, upgrades).
 * <p>
 * The cell supports a shapeless crafting recipe: empty housing + any valid
 * component from the whitelist = configured cell with that component installed.
 */
public class ItemConfigurableCell extends Item implements ICellWorkbenchItem, IItemGroup {

    public ItemConfigurableCell() {
        // Cells stack to 64 only if mixins are active (fixing AE2 slot limits).
        // Without mixins, stacking cells causes duplication exploits in ME Chest/Workbench.
        setMaxStackSize(MixinState.areMixinsEnabled() ? 64 : 1);
        setHasSubtypes(false);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
        setRegistryName(Tags.MODID, "configurable_cell");
        setTranslationKey(Tags.MODID + ".configurable_cell");
    }

    // =====================
    // Display name
    // =====================

    /**
     * Include the installed component tier in the item name.
     * e.g., "Configurable ME Storage Cell (64K)" instead of just "Configurable ME Storage Cell".
     */
    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        ComponentInfo info = ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(stack));
        if (info == null) return super.getItemStackDisplayName(stack);

        return net.minecraft.util.text.translation.I18n.translateToLocalFormatted(
            "item.cells.configurable_cell.name_configured", info.getTierName().toUpperCase()).trim();
    }

    // =====================
    // Tooltip
    // =====================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        ItemStack component = ComponentHelper.getInstalledComponent(stack);
        ComponentInfo info = ComponentHelper.getComponentInfo(component);

        if (info == null) {
            // No component installed - show warning
            tooltip.add("§c" + I18n.format("tooltip.cells.configurable_cell.no_component"));
            tooltip.add("");
            tooltip.add("§7" + I18n.format("tooltip.cells.configurable_cell.info"));
            return;
        }

        // Show component itemStack name if present
        if (!component.isEmpty()) tooltip.add("§e" + component.getDisplayName());


        // Show AE2 cell info (bytes, types, stored items)
        ChannelType channelType = info.getChannelType();
        ICellInventory<?> cellInv = null;

        switch (channelType) {
            case ITEM:
                ICellInventoryHandler<IAEItemStack> itemHandler = AEApi.instance().registries().cell()
                    .getCellInventory(stack, null,
                        AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
                AEApi.instance().client().addCellInformation(itemHandler, tooltip);
                if (itemHandler != null) cellInv = itemHandler.getCellInv();
                break;

            case FLUID:
                ICellInventoryHandler<IAEFluidStack> fluidHandler = AEApi.instance().registries().cell()
                    .getCellInventory(stack, null,
                        AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
                AEApi.instance().client().addCellInformation(fluidHandler, tooltip);
                if (fluidHandler != null) cellInv = fluidHandler.getCellInv();
                break;

            case ESSENTIA:
                ThaumicEnergisticsIntegration.addCellInformation(stack, tooltip);
                break;

            case GAS:
                MekanismEnergisticsIntegration.addCellInformation(stack, tooltip);
                break;
        }

        // Add NBT size information (if enabled in config)
        if (CellsConfig.enableNbtSizeTooltip && cellInv instanceof INBTSizeProvider) {
            int nbtSize = ((INBTSizeProvider) cellInv).getTotalNbtSize();
            long warningThreshold = NBTSizeHelper.kbToBytes(CellsConfig.nbtSizeWarningThresholdKB);
            String sizeStr = NBTSizeHelper.formatSizeWithColor(nbtSize, warningThreshold);

            tooltip.add("");
            tooltip.add(I18n.format("tooltip.cells.nbt_size", sizeStr));

            if (NBTSizeHelper.exceedsThreshold(nbtSize, warningThreshold)) {
                tooltip.add("§c" + I18n.format("tooltip.cells.nbt_size.warning"));
            }
        }

        // Show per-type capacity info
        long maxPerType = ComponentHelper.getMaxPerType(stack);
        int maxTypes = ComponentHelper.getEffectiveMaxTypes(stack, channelType);
        long physicalMax = ComponentHelper.calculatePhysicalPerTypeCapacity(info, maxTypes);

        long effectivePerType = Math.min(maxPerType, physicalMax);

        String unitKey = "tooltip.cells.configurable_cell.capacity_per_type." + channelType.getLocalizationSuffix();
        tooltip.add("§b" + I18n.format(unitKey, ReadableNumberConverter.INSTANCE.toWideReadableForm(effectivePerType)));

        // Show upgrade information
        CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);

        // Show cell description
        tooltip.add("");
        tooltip.add("§7" + I18n.format("tooltip.cells.configurable_cell.info"));
    }

    // =====================
    // Right-click behavior
    // =====================

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, EntityPlayer player, @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (player.isSneaking()) {
            // Shift-right-click: disassemble
            if (disassembleDrive(stack, world, player, hand)) {
                return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
            }
        } else {
            // Right-click: open GUI
            if (!world.isRemote) {
                player.openGui(Cells.instance, CellsGuiHandler.GUI_CONFIGURABLE_CELL,
                    world, hand.ordinal(), 0, 0);
            }
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(EntityPlayer player, @Nonnull World world,
                                           @Nonnull BlockPos pos, @Nonnull EnumFacing side,
                                           float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        if (player.isSneaking()) {
            // Must return SUCCESS on both client and server so the client doesn't
            // fire a redundant onItemRightClick, which would disassemble a second time.
            if (!world.isRemote) disassembleDrive(player.getHeldItem(hand), world, player, hand);

            return EnumActionResult.SUCCESS;
        }

        // Open GUI on block right-click too (non-sneaking)
        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_CONFIGURABLE_CELL,
                world, hand.ordinal(), 0, 0);
        }

        return EnumActionResult.SUCCESS;
    }

    /**
     * Disassemble a single configurable cell, returning its components.
     * <p>
     * Unlike standard cells, the configurable cell returns a stripped empty housing
     * (with user configs preserved) plus the component and upgrades separately.
     *
     * @param stack The single cell to disassemble (stack size should be 1)
     * @param world The world
     * @param player The player performing the action
     * @param hand The hand holding the item (used only for stack size 1 case when clearing hand)
     * @return true if disassembly was successful
     */
    private boolean disassembleDrive(ItemStack stack, World world, EntityPlayer player, EnumHand hand) {
        InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
        if (ia == null) return false;

        if (ComponentHelper.hasContent(stack)) {
            // Don't allow disassembly if the cell still has content in it
            player.sendStatusMessage(new TextComponentString("§c" + I18n.format("message.cells.disassemble_fail_content")), true);
            return false;
        }

        // Check if there's anything to disassemble (component or upgrades)
        // If nothing to disassemble, this cell is already empty - do nothing
        ItemStack component = ComponentHelper.getInstalledComponent(stack);
        IItemHandler upgrades = getUpgradesInventory(stack);
        if (component.isEmpty() && !CellDisassemblyHelper.hasUpgrades(upgrades)) return false;

        // Create a stripped housing: no component, no upgrades, but user configs intact.
        // Copy first so we preserve all NBT (maxPerType, FuzzyMode, etc.)
        ItemStack housing = stack.copy();
        housing.setCount(1);

        // Extract and return upgrades from the housing copy
        CellDisassemblyHelper.extractAndReturnUpgrades(getUpgradesInventory(housing), ia, player);

        // Extract and return the component from the housing copy
        ItemStack housingComponent = ComponentHelper.getInstalledComponent(housing);
        if (!housingComponent.isEmpty()) {
            ComponentHelper.setInstalledComponent(housing, ItemStack.EMPTY);
            CellDisassemblyHelper.returnItem(housingComponent, ia, player);
        }

        // Remove one cell from the player's hand
        CellDisassemblyHelper.removeOneFromHand(stack, player);

        // Return the stripped housing to the player's inventory
        CellDisassemblyHelper.returnItem(housing, ia, player);

        if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();

        return true;
    }

    // =====================
    // ICellWorkbenchItem implementation
    // =====================

    @Override
    public boolean isEditable(ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(ItemStack is) {
        return new CustomCellUpgrades(is, 2, Arrays.asList(
            CustomCellUpgrades.CustomUpgrades.OVERFLOW
        ));
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
}
