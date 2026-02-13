package com.cells.cells.hyperdensity.fluid;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
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

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.definitions.IMaterials;
import appeng.api.implementations.items.IItemGroup;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import com.cells.core.CellsCreativeTab;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

import com.cells.config.CellsConfig;
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Abstract base class for hyper-density fluid storage cells.
 * 
 * Hyper-density cells display a small byte count (e.g., "1k") but internally
 * multiply that by a large factor (2GB) to store vastly more fluids.
 * 
 * This allows circumventing int limitations in AE2's display while
 * maintaining compatibility with the existing system.
 */
public abstract class ItemFluidHyperDensityCellBase extends Item implements IItemFluidHyperDensityCell, IItemGroup {

    /**
     * The internal byte multiplier. Each "displayed byte" represents this many actual bytes.
     * Using Integer.MAX_VALUE (2,147,483,647) as the multiplier means:
     * - A "1k HD Fluid Cell" stores ~2.1 trillion bytes
     * - A "1G HD Fluid Cell" stores ~2.3 quintillion bytes (near Long.MAX_VALUE)
     */
    public static final long BYTE_MULTIPLIER = Integer.MAX_VALUE;

    protected final String[] tierNames;
    protected final long[] displayBytes;
    protected final long[] bytesPerType;

    public ItemFluidHyperDensityCellBase(String[] tierNames, long[] displayBytes, long[] bytesPerType) {
        this.tierNames = tierNames;
        this.displayBytes = displayBytes;
        this.bytesPerType = bytesPerType;

        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CellsCreativeTab.instance);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < tierNames.length) return getTranslationKey() + "." + tierNames[meta];

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) return;

        for (int i = 0; i < tierNames.length; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> cellHandler = AEApi.instance().registries().cell().getCellInventory(stack, null, channel);

        AEApi.instance().client().addCellInformation(cellHandler, tooltip);

        CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);

        tooltip.add("");
        tooltip.add("\u00a7d" + I18n.format("tooltip.cells.hyper_density_fluid_cell.info"));
    }

    /**
     * Get the cell component (storage component) for the given tier.
     * Returns the ItemStack for the component, or empty if not applicable.
     */
    protected abstract ItemStack getCellComponent(int tier);

    // =====================
    // IItemFluidHyperDensityCell implementation
    // =====================

    @Override
    public long getDisplayBytes(@Nonnull ItemStack cellItem) {
        int meta = cellItem.getMetadata();
        if (meta >= 0 && meta < displayBytes.length) return displayBytes[meta];

        return displayBytes[0];
    }

    @Override
    public long getByteMultiplier() {
        return BYTE_MULTIPLIER;
    }

    @Override
    public long getBytesPerType(@Nonnull ItemStack cellItem) {
        int meta = cellItem.getMetadata();
        if (meta >= 0 && meta < bytesPerType.length) {
            return CellMathHelper.multiplyWithOverflowProtection(bytesPerType[meta], BYTE_MULTIPLIER);
        }

        return CellMathHelper.multiplyWithOverflowProtection(bytesPerType[0], BYTE_MULTIPLIER);
    }

    @Override
    public int getMaxTypes() {
        return CellsConfig.hdFluidMaxTypes;
    }

    @Override
    public double getIdleDrain() {
        return CellsConfig.fluidHdIdleDrain;
    }

    @Override
    public boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEFluidStack requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isFluidHyperDensityCell(@Nonnull ItemStack i) {
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

    // =====================
    // Disassembly support (shift-right-click to break down)
    // =====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking() && disassembleDrive(stack, world, player)) {
            return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
        }

        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        return disassembleDrive(player.getHeldItem(hand), world, player) ? EnumActionResult.SUCCESS : EnumActionResult.PASS;
    }

    private boolean disassembleDrive(ItemStack stack, World world, EntityPlayer player) {
        if (!player.isSneaking()) return false;
        if (Platform.isClient()) return false;

        IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);

        IMEInventoryHandler<IAEFluidStack> inv = AEApi.instance().registries().cell().getCellInventory(stack, null, fluidChannel);
        if (inv == null) return false;

        IItemList<IAEFluidStack> list = inv.getAvailableItems(fluidChannel.createList());
        if (!list.isEmpty()) return false;

        InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
        if (ia == null) return false;

        // Remove one cell from the stack.
        // If the held stack has more than one item, shrink it by one.
        if (stack.getCount() > 1) {
            stack.shrink(1);
        } else {
            // Main hand
            if (stack == player.getHeldItemMainhand()) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
            // Off hand
            } else if (stack == player.getHeldItemOffhand()) {
                player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            }
        }

        // Return upgrades
        IItemHandler upgradesInventory = getUpgradesInventory(stack);
        for (int i = 0; i < upgradesInventory.getSlots(); i++) {
            ItemStack upgradeStack = upgradesInventory.getStackInSlot(i);
            if (!upgradeStack.isEmpty()) {
                ItemStack leftStack = ia.addItems(upgradeStack);
                if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
            }
        }

        // Return the cell housing
        IMaterials materials = AEApi.instance().definitions().materials();
        ItemStack housing = materials.emptyStorageCell().maybeStack(1).orElse(ItemStack.EMPTY);
        if (!housing.isEmpty()) {
            ItemStack leftStack = ia.addItems(housing);
            if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
        }

        // Return the cell component for this tier
        ItemStack component = getCellComponent(stack.getMetadata());
        if (!component.isEmpty()) {
            ItemStack leftStack = ia.addItems(component);
            if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
        }

        if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();

        return true;
    }
}
