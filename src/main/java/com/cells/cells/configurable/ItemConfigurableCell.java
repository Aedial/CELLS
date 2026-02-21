package com.cells.cells.configurable;

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
import com.cells.config.CellsConfig;
import com.cells.core.CellsCreativeTab;
import com.cells.gui.CellsGuiHandler;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Configurable Storage Cell item.
 * <p>
 * A single-item cell that accepts any ME Storage Component to define its capacity
 * and storage channel (items or fluids). The user can configure a per-type capacity
 * limit via a GUI text field, and the cell has equal distribution built in.
 * <p>
 * Right-click opens the configuration GUI.
 * Shift-right-click disassembles the cell (returns housing, component, upgrades).
 * TODO: add a crafting recipe to combine a configurable cell + a component into a configured cell, to make it easier
 */
public class ItemConfigurableCell extends Item implements ICellWorkbenchItem, IItemGroup {

    public ItemConfigurableCell() {
        setMaxStackSize(64);
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
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
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
        if (info.isFluid()) {
            ICellInventoryHandler<IAEFluidStack> handler = AEApi.instance().registries().cell()
                .getCellInventory(stack, null,
                    AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class));
            AEApi.instance().client().addCellInformation(handler, tooltip);
        } else {
            ICellInventoryHandler<IAEItemStack> handler = AEApi.instance().registries().cell()
                .getCellInventory(stack, null,
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            AEApi.instance().client().addCellInformation(handler, tooltip);
        }

        // Show per-type capacity info
        long maxPerType = ComponentHelper.getMaxPerType(stack);
        long physicalMax = ComponentHelper.calculatePhysicalPerTypeCapacity(info, CellsConfig.configurableCellMaxTypes);

        long effectivePerType = Math.min(maxPerType, physicalMax);

        String unitKey = info.isFluid()
            ? "tooltip.cells.configurable_cell.capacity_per_type.mb"
            : "tooltip.cells.configurable_cell.capacity_per_type.items";
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
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
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
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
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
        // If nothing to disassemble, don't do anything
        ItemStack component = ComponentHelper.getInstalledComponent(stack);
        IItemHandler upgrades = getUpgradesInventory(stack);
        boolean hasUpgrades = false;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            if (!upgrades.getStackInSlot(i).isEmpty()) {
                hasUpgrades = true;
                break;
            }
        }

        if (component.isEmpty() && !hasUpgrades) return false;

        // Create a stripped housing: no component, no upgrades, but user configs intact.
        // Copy first so we preserve all NBT (maxPerType, FuzzyMode, etc.)
        ItemStack housing = stack.copy();
        housing.setCount(1);

        // Extract and return upgrades from the housing copy
        IItemHandler housingUpgrades = getUpgradesInventory(housing);
        for (int i = 0; i < housingUpgrades.getSlots(); i++) {
            ItemStack upgradeStack = housingUpgrades.extractItem(i, Integer.MAX_VALUE, false);
            if (!upgradeStack.isEmpty()) {
                ItemStack leftStack = ia.addItems(upgradeStack);
                if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
            }
        }

        // Extract and return the component from the housing copy (it contains the inventory NBT)
        ItemStack housingComponent = ComponentHelper.getInstalledComponent(housing);
        if (!housingComponent.isEmpty()) {
            ComponentHelper.setInstalledComponent(housing, ItemStack.EMPTY);
            ItemStack leftStack = ia.addItems(housingComponent);
            if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
        }

        // Decrease the stack count in the player's hand
        ItemStack heldItem = player.getHeldItem(hand);
        if (heldItem == stack) {
            if (heldItem.getCount() <= 1) {
                // Last item in stack - clear the slot entirely
                if (hand == EnumHand.MAIN_HAND) {
                    player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
                } else {
                    player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
                }
            } else {
                // More than one item - just shrink the stack
                heldItem.shrink(1);
            }
        }

        // Return the stripped housing to the player's inventory
        ItemStack leftHousing = ia.addItems(housing);
        if (!leftHousing.isEmpty()) player.dropItem(leftHousing, false);

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
        return new CustomCellUpgrades(is, 2);
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
