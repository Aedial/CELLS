package com.cells.cells.normal.compacting;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;

import com.cells.cells.common.AbstractTieredCellItem;
import com.cells.config.CellsConfig;
import com.cells.util.CellDisassemblyHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Abstract base class for compacting storage cells.
 * Provides common implementation shared between standard and dense compacting cells.
 */
public abstract class ItemCompactingCellBase extends AbstractTieredCellItem implements IInternalCompactingCell {

    protected final String[] tierNames;
    protected final long[] tierBytes;
    protected final long[] bytesPerType;

    public ItemCompactingCellBase(String[] tierNames, long[] tierBytes, long[] bytesPerType) {
        super();
        this.tierNames = tierNames;
        this.tierBytes = tierBytes;
        this.bytesPerType = bytesPerType;
    }

    // =====================
    // AbstractTieredCellItem implementation
    // =====================

    @Override
    @Nonnull
    protected String[] getTiers() {
        return tierNames;
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void addCellInformation(@Nonnull ItemStack stack, World world,
                                       @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> cellHandler = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, channel);

        AEApi.instance().client().addCellInformation(cellHandler, tooltip);

        // Try to get the internal CompactingCellInventory for compression info
        if (cellHandler != null) {
            ICellInventory<?> cellInv = cellHandler.getCellInv();

            if (cellInv instanceof CompactingCellInventory) {
                addCompactingCellInfo((CompactingCellInventory) cellInv, tooltip);

                tooltip.add("§e" + I18n.format("tooltip.cells.compacting_cell.ioport_warning"));
                CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);
                return;
            }
        }

        // Fallback for when cell inventory isn't available
        tooltip.add("");
        tooltip.add("§8" + I18n.format("tooltip.cells.compacting_cell.stores_one_type"));
    }

    /**
     * Add compacting-specific tooltip info (compression chain status).
     * Extracted to allow reuse in HD compacting cells.
     */
    protected void addCompactingCellInfo(CompactingCellInventory compactingInv, List<String> tooltip) {
        if (!compactingInv.hasPartition()) {
            tooltip.add("");
            tooltip.add("§c" + I18n.format("tooltip.cells.compacting_cell.not_partitioned"));
            return;
        }

        if (!compactingInv.isChainInitialized() && !compactingInv.hasStoredItems()) {
            tooltip.add("");
            tooltip.add("§e" + I18n.format("tooltip.cells.compacting_cell.insert_to_set_compression"));
            return;
        }

        // Has items stored - show compression info for ALL tiers
        List<ItemStack> higherTiers = compactingInv.getAllHigherTierItems();
        List<ItemStack> lowerTiers = compactingInv.getAllLowerTierItems();

        if (!higherTiers.isEmpty() || !lowerTiers.isEmpty()) {
            tooltip.add("");
            for (ItemStack tier : higherTiers) {
                tooltip.add("§a" + I18n.format("tooltip.cells.compacting_cell.converts_up", tier.getDisplayName()));
            }
            for (ItemStack tier : lowerTiers) {
                tooltip.add("§b" + I18n.format("tooltip.cells.compacting_cell.converts_down", tier.getDisplayName()));
            }
        } else {
            tooltip.add("");
            tooltip.add("§e" + I18n.format("tooltip.cells.compacting_cell.no_compression"));
        }
    }

    @Override
    protected boolean disassembleCell(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        return CellDisassemblyHelper.disassembleCell(
                stack, player, channel, this, true,
                s -> getCellComponent(s.getMetadata()));
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
        return getValueByMeta(cellItem, tierBytes);
    }

    public long getBytesPerType(@Nonnull ItemStack cellItem) {
        return getValueByMeta(cellItem, bytesPerType);
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
    public void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem,
                                               @Nonnull World world) {
        CompactingCellInventory inventory = new CompactingCellInventory(this, cellStack, null);

        if (!partitionItem.isEmpty()) {
            inventory.initializeChainForItem(partitionItem, world);
        } else {
            inventory.initializeChainFromPartition(world);
        }
    }

    // =====================
    // ICellWorkbenchItem - upgrades
    // =====================

    @Override
    public IItemHandler getUpgradesInventory(ItemStack is) {
        return new CustomCellUpgrades(is, 2, Arrays.asList(
            CustomCellUpgrades.CustomUpgrades.OVERFLOW,
            CustomCellUpgrades.CustomUpgrades.OREDICT,
            CustomCellUpgrades.CustomUpgrades.COMPRESSION_TIER,
            CustomCellUpgrades.CustomUpgrades.DECOMPRESSION_TIER
        ));
    }
}
