package com.cells.cells.normal.compacting;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;

import appeng.core.CreativeTab;
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
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.localization.GuiText;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

import com.cells.config.CellsConfig;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Abstract base class for compacting storage cells.
 * Provides common implementation shared between standard and dense compacting cells.
 */
public abstract class ItemCompactingCellBase extends Item implements IInternalCompactingCell, IItemGroup {

    protected final String[] tierNames;
    protected final long[] tierBytes;
    protected final long[] bytesPerType;

    public ItemCompactingCellBase(String[] tierNames, long[] tierBytes, long[] bytesPerType) {
        this.tierNames = tierNames;
        this.tierBytes = tierBytes;
        this.bytesPerType = bytesPerType;

        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CreativeTab.instance);
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
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> cellHandler = AEApi.instance().registries().cell().getCellInventory(stack, null, channel);

        AEApi.instance().client().addCellInformation(cellHandler, tooltip);

        // Try to get the internal CompactingCellInventory for compression info
        if (cellHandler != null) {
            ICellInventory<?> cellInv = cellHandler.getCellInv();

            if (cellInv instanceof CompactingCellInventory) {
                CompactingCellInventory compactingInv = (CompactingCellInventory) cellInv;

                if (!compactingInv.hasPartition()) {
                    // Not partitioned - tell user they need to partition
                    tooltip.add("");
                    tooltip.add("\u00a7c" + I18n.format("tooltip.cells.compacting_cell.not_partitioned"));
                } else if (!compactingInv.isChainInitialized() && !compactingInv.hasStoredItems()) {
                    // Partitioned but chain not initialized and no items - tell user to insert items
                    tooltip.add("");
                    tooltip.add("\u00a7e" + I18n.format("tooltip.cells.compacting_cell.insert_to_set_compression"));
                } else {
                    // Has items stored - show compression info
                    ItemStack higherTier = compactingInv.getHigherTierItem();
                    ItemStack lowerTier = compactingInv.getLowerTierItem();

                    if (!higherTier.isEmpty() || !lowerTier.isEmpty()) {
                        tooltip.add("");

                        if (!higherTier.isEmpty()) {
                            tooltip.add("\u00a7a" + I18n.format("tooltip.cells.compacting_cell.converts_up", higherTier.getDisplayName()));
                        }

                        if (!lowerTier.isEmpty()) {
                            tooltip.add("\u00a7b" + I18n.format("tooltip.cells.compacting_cell.converts_down", lowerTier.getDisplayName()));
                        }
                    } else {
                        // Items stored but no compression found
                        tooltip.add("");
                        tooltip.add("\u00a78" + I18n.format("tooltip.cells.compacting_cell.no_compression"));
                    }
                }

                // Add upgrade information (e.g., Overflow Card active)
                CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);

                return;
            }
        }

        // Fallback for when cell inventory isn't available
        tooltip.add("");
        tooltip.add("\u00a78" + I18n.format("tooltip.cells.compacting_cell.stores_one_type"));
    }

    /**
     * Get the cell component (storage component) for the given tier.
     * Returns the AE2 ItemStack for the component, or empty if not applicable.
     */
    protected abstract ItemStack getCellComponent(int tier);

    // =====================
    // Internal cell methods
    // =====================

    public long getBytes(@Nonnull ItemStack cellItem) {
        int meta = cellItem.getMetadata();
        if (meta >= 0 && meta < tierBytes.length) return tierBytes[meta];

        return tierBytes[0];
    }

    public long getBytesPerType(@Nonnull ItemStack cellItem) {
        int meta = cellItem.getMetadata();
        if (meta >= 0 && meta < bytesPerType.length) return bytesPerType[meta];

        return bytesPerType[0];
    }

    public double getIdleDrain() {
        return CellsConfig.compactingIdleDrain;
    }

    public boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition) {
        return false;
    }

    public boolean storableInStorageCell() {
        return false;
    }

    // =====================
    // IItemCompactingCell implementation
    // =====================

    @Override
    public void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem, @Nonnull World world) {
        CompactingCellInventory inventory = new CompactingCellInventory(this, cellStack, null);

        if (!partitionItem.isEmpty()) {
            inventory.initializeChainForItem(partitionItem, world);
        } else {
            inventory.initializeChainFromPartition(world);
        }
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

        IItemStorageChannel itemChannel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);

        IMEInventoryHandler<IAEItemStack> inv = AEApi.instance().registries().cell().getCellInventory(stack, null, itemChannel);
        if (inv == null) return false;

        IItemList<IAEItemStack> list = inv.getAvailableItems(itemChannel.createList());
        if (!list.isEmpty()) return false; // Cell not empty

        InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
        if (ia == null) return false;

        // Clear the cell from hand
        player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);

        // Return upgrades
        IItemHandler upgradesInventory = getUpgradesInventory(stack);
        for (int i = 0; i < upgradesInventory.getSlots(); i++) {
            ItemStack upgradeStack = upgradesInventory.getStackInSlot(i);
            if (!upgradeStack.isEmpty()) {
                ItemStack leftStack = ia.addItems(upgradeStack);
                if (!leftStack.isEmpty()) player.dropItem(leftStack, false);
            }
        }

        // Return the cell housing (AE2's empty storage cell)
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
