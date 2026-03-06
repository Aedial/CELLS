package com.cells.cells.hyperdensity.compacting;

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
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Abstract base class for hyper-density compacting storage cells.
 * <p>
 * These cells combine compacting functionality (compression chains) with
 * the hyper-density byte multiplier for massive storage capacity.
 * <p>
 * Due to overflow concerns with base unit calculations, HD compacting cells
 * are limited to 16M tier maximum (vs 2G for regular HD cells).
 */
public abstract class ItemHyperDensityCompactingCellBase extends AbstractTieredCellItem
        implements IItemHyperDensityCompactingCell {

    /**
     * The internal byte multiplier. Each "displayed byte" represents this many actual bytes.
     */
    public static final long BYTE_MULTIPLIER = Integer.MAX_VALUE;

    protected final String[] tierNames;
    protected final long[] displayBytes;
    protected final long[] bytesPerType;

    public ItemHyperDensityCompactingCellBase(String[] tierNames, long[] displayBytes, long[] bytesPerType) {
        super();
        this.tierNames = tierNames;
        this.displayBytes = displayBytes;
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

        // Get compacting cell info if available
        if (cellHandler != null) {
            ICellInventory<?> cellInv = cellHandler.getCellInv();

            if (cellInv instanceof HyperDensityCompactingCellInventory) {
                addHDCompactingCellInfo((HyperDensityCompactingCellInventory) cellInv, tooltip);
            }
        }

        CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);

        // Add hyper-density and compacting explanations
        tooltip.add("");
        tooltip.add("§d" + I18n.format("tooltip.cells.hyper_density_cell.info"));
        tooltip.add("§e" + I18n.format("tooltip.cells.compacting_cell.ioport_warning"));
    }

    /**
     * Add HD compacting-specific tooltip info (compression chain status).
     */
    protected void addHDCompactingCellInfo(HyperDensityCompactingCellInventory hdCompInv, List<String> tooltip) {
        if (!hdCompInv.hasPartition()) {
            tooltip.add("");
            tooltip.add("§c" + I18n.format("tooltip.cells.compacting_cell.not_partitioned"));
            return;
        }

        if (!hdCompInv.isChainInitialized() && !hdCompInv.hasStoredItems()) {
            tooltip.add("");
            tooltip.add("§e" + I18n.format("tooltip.cells.compacting_cell.insert_to_set_compression"));
            return;
        }

        List<ItemStack> higherTiers = hdCompInv.getAllHigherTierItems();
        List<ItemStack> lowerTiers = hdCompInv.getAllLowerTierItems();

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
     * Get the cell component for the given tier.
     */
    protected abstract ItemStack getCellComponent(int tier);

    // =====================
    // IItemHyperDensityCompactingCell implementation
    // =====================

    @Override
    public long getDisplayBytes(@Nonnull ItemStack cellItem) {
        return getValueByMeta(cellItem, displayBytes);
    }

    @Override
    public long getByteMultiplier() {
        return BYTE_MULTIPLIER;
    }

    @Override
    public long getBytes(@Nonnull ItemStack cellItem) {
        return CellMathHelper.multiplyWithOverflowProtection(getDisplayBytes(cellItem), BYTE_MULTIPLIER);
    }

    @Override
    public long getBytesPerType(@Nonnull ItemStack cellItem) {
        long displayBpt = getValueByMeta(cellItem, bytesPerType);
        return CellMathHelper.multiplyWithOverflowProtection(displayBpt, BYTE_MULTIPLIER);
    }

    @Override
    public double getIdleDrain() {
        return CellsConfig.hdCompactingIdleDrain;
    }

    @Override
    public boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    // =====================
    // IItemCompactingCell implementation
    // =====================

    @Override
    public void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem,
                                               @Nonnull World world) {
        HyperDensityCompactingCellInventory inventory = new HyperDensityCompactingCellInventory(this, cellStack, null);

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
