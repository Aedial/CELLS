package com.cells.cells.hyperdensity.fluid;

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
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;

import com.cells.cells.common.AbstractTieredCellItem;
import com.cells.config.CellsConfig;
import com.cells.util.CellDisassemblyHelper;
import com.cells.util.CellMathHelper;
import com.cells.util.CellUpgradeHelper;
import com.cells.util.CustomCellUpgrades;


/**
 * Abstract base class for hyper-density fluid storage cells.
 * <p>
 * Hyper-density cells display a small byte count (e.g., "1k") but internally
 * multiply that by a large factor (2GB) to store vastly more fluids.
 * <p>
 * This allows circumventing int limitations in AE2's display while
 * maintaining compatibility with the existing system.
 */
public abstract class ItemFluidHyperDensityCellBase extends AbstractTieredCellItem implements IItemFluidHyperDensityCell {

    /**
     * The internal byte multiplier. Each "displayed byte" represents this many actual bytes.
     * Using Integer.MAX_VALUE (2,147,483,647) as the multiplier means:
     * - A "1k HD Fluid Cell" stores ~2.1 trillion bytes
     * - A "1G HD Fluid Cell" stores ~2.3 quintillion bytes (near Long.MAX_VALUE)
     */
    public static final long BYTE_MULTIPLIER = Integer.MAX_VALUE;

    protected final String[] tierNames;
    protected final long[] displayBytes;

    public ItemFluidHyperDensityCellBase(String[] tierNames, long[] displayBytes) {
        super();
        this.tierNames = tierNames;
        this.displayBytes = displayBytes;
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
        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        ICellInventoryHandler<IAEFluidStack> cellHandler = AEApi.instance().registries().cell()
                .getCellInventory(stack, null, channel);

        AEApi.instance().client().addCellInformation(cellHandler, tooltip);

        CellUpgradeHelper.addUpgradeTooltips(getUpgradesInventory(stack), tooltip);

        tooltip.add("");
        tooltip.add("§d" + I18n.format("tooltip.cells.hyper_density_fluid_cell.info"));
    }

    @Override
    protected boolean disassembleCell(@Nonnull ItemStack stack, @Nonnull EntityPlayer player) {
        IFluidStorageChannel channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        return CellDisassemblyHelper.disassembleCell(
                stack, player, channel, this, true,
                s -> getCellComponent(s.getMetadata()));
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
        return getValueByMeta(cellItem, displayBytes);
    }

    @Override
    public long getByteMultiplier() {
        return BYTE_MULTIPLIER;
    }

    /**
     * Bytes per type overhead, computed dynamically based on the effective max types.
     * <p>
     * Formula: displayBytes / 2 / effectiveMaxTypes * BYTE_MULTIPLIER.
     * This ensures total overhead remains ~50% of total bytes regardless of
     * how many types the config or equal distribution card allows.
     */
    @Override
    public long getBytesPerType(@Nonnull ItemStack cellItem) {
        long displayBytesValue = getValueByMeta(cellItem, displayBytes);

        // Effective max types: config value, possibly limited by equal distribution card
        int effectiveMaxTypes = getMaxTypes();
        int eqDistLimit = CellUpgradeHelper.getEqualDistributionLimit(getUpgradesInventory(cellItem));
        if (eqDistLimit > 0 && eqDistLimit < effectiveMaxTypes) {
            effectiveMaxTypes = eqDistLimit;
        }

        long displayBpt = displayBytesValue / 2 / effectiveMaxTypes;

        return CellMathHelper.multiplyWithOverflowProtection(displayBpt, BYTE_MULTIPLIER);
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
    public boolean isHyperDensityCell(@Nonnull ItemStack i) {
        return true;
    }

    // =====================
    // ICellWorkbenchItem - upgrades
    // =====================

    @Override
    public IItemHandler getUpgradesInventory(ItemStack is) {
        return new CustomCellUpgrades(is, 2, Arrays.asList(
            CustomCellUpgrades.CustomUpgrades.OVERFLOW,
            CustomCellUpgrades.CustomUpgrades.EQUAL_DISTRIBUTION
        ));
    }
}
