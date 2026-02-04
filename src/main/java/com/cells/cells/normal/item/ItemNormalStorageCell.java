package com.cells.cells.normal.item;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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
import appeng.api.implementations.items.IItemGroup;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.CreativeTab;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.items.contents.CellUpgrades;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

import com.cells.ItemRegistry;
import com.cells.Tags;
import com.cells.config.CellsConfig;


/**
 * Normal AE2-style storage cell for tiers beyond NAE2's 16M cap.
 * Provides 64M, 256M, 1G, 2G storage cells.
 * 
 * These cells behave exactly like standard AE2 cells but with higher capacity.
 */
public class ItemNormalStorageCell extends AEBaseItem implements IStorageCell<IAEItemStack>, IItemGroup {

    private static final String[] TIER_NAMES = {"65536k", "262144k", "1048576k", "2097152k"};

    private static final long[] TIER_BYTES = {
        67108864L,      // 64M
        268435456L,     // 256M
        1073741824L,    // 1G
        2147483648L     // 2G
    };

    private static final long[] BYTES_PER_TYPE = {
        524288L,        // 64M
        2097152L,       // 256M
        8388608L,       // 1G
        16777216L       // 2G
    };

    private static final double[] IDLE_DRAIN = {
        3.0,    // 64M
        3.5,    // 256M
        4.0,    // 1G
        4.5     // 2G
    };

    // Modid for localization/texture namespace
    private static final String MODID = "nae2";

    public ItemNormalStorageCell() {
        setMaxStackSize(64);
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(CreativeTab.instance);
        setRegistryName(MODID, "storage_cell");
        setTranslationKey(MODID + ".storage_cell");
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        int meta = stack.getMetadata();
        if (meta >= 0 && meta < TIER_NAMES.length) return getTranslationKey() + "." + TIER_NAMES[meta];

        return getTranslationKey();
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void getCheckedSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        for (int i = 0; i < TIER_NAMES.length; i++) items.add(new ItemStack(this, 1, i));
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void addCheckedInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        AEApi.instance().client().addCellInformation(
            AEApi.instance().registries().cell().getCellInventory(stack, null, getChannel()),
            tooltip
        );
    }

    // IStorageCell implementation

    @Override
    public int getBytes(@Nonnull ItemStack cellItem) {
        int tier = cellItem.getMetadata();
        if (tier < 0 || tier >= TIER_BYTES.length) tier = 0;

        // AE2 API uses int for bytes, so we need to clamp
        long bytes = TIER_BYTES[tier];
        return (int) Math.min(bytes, Integer.MAX_VALUE);
    }

    @Override
    public int getBytesPerType(@Nonnull ItemStack cellItem) {
        int tier = cellItem.getMetadata();
        if (tier < 0 || tier >= BYTES_PER_TYPE.length) tier = 0;

        long bpt = BYTES_PER_TYPE[tier];
        return (int) Math.min(bpt, Integer.MAX_VALUE);
    }

    @Override
    public int getTotalTypes(@Nonnull ItemStack cellItem) {
        return 63;
    }

    @Override
    public boolean isBlackListed(@Nonnull ItemStack cellItem, @Nonnull IAEItemStack requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(@Nonnull ItemStack i) {
        return true;
    }

    @Override
    public double getIdleDrain() {
        return CellsConfig.normalIdleDrain;
    }

    public double getIdleDrain(@Nonnull ItemStack cellItem) {
        int tier = cellItem.getMetadata();
        if (tier < 0 || tier >= IDLE_DRAIN.length) tier = 0;

        return IDLE_DRAIN[tier] * (CellsConfig.normalIdleDrain / 3.0);
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    // IItemGroup implementation

    @Override
    public String getUnlocalizedGroupName(Set<ItemStack> others, ItemStack is) {
        return GuiText.StorageCells.getUnlocalized();
    }

    // ICellWorkbenchItem implementation

    @Override
    public boolean isEditable(@Nonnull ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(@Nonnull ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IItemHandler getConfigInventory(@Nonnull ItemStack is) {
        return new CellConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(@Nonnull ItemStack is) {
        NBTTagCompound tag = Platform.openNbtData(is);
        String fz = tag.getString("FuzzyMode");

        if (fz.isEmpty()) return FuzzyMode.IGNORE_ALL;

        try {
            return FuzzyMode.valueOf(fz);
        } catch (IllegalArgumentException e) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(@Nonnull ItemStack is, FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    // Disassembly support

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
        disassembleDrive(player.getHeldItem(hand), player);

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                           @Nonnull EnumFacing side, float hitX, float hitY, float hitZ, @Nonnull EnumHand hand) {
        if (disassembleDrive(player.getHeldItem(hand), player)) return EnumActionResult.SUCCESS;

        return EnumActionResult.PASS;
    }

    private boolean disassembleDrive(ItemStack stack, EntityPlayer player) {
        if (!player.isSneaking()) return false;
        if (Platform.isClient()) return false;

        InventoryPlayer playerInventory = player.inventory;
        IMEInventoryHandler<IAEItemStack> inv = AEApi.instance().registries().cell()
            .getCellInventory(stack, null, getChannel());

        if (inv == null) return false;
        if (playerInventory.getCurrentItem() != stack) return false;

        InventoryAdaptor ia = InventoryAdaptor.getAdaptor(player);
        IItemList<IAEItemStack> list = inv.getAvailableItems(getChannel().createList());

        if (!list.isEmpty()) return false;

        playerInventory.setInventorySlotContents(playerInventory.currentItem, ItemStack.EMPTY);

        // Return the component
        ItemStack component = getCellComponent(stack.getMetadata());
        if (!component.isEmpty()) {
            ItemStack extraB = ia.addItems(component);
            if (!extraB.isEmpty()) player.dropItem(extraB, false);
        }

        // Return upgrades
        IItemHandler upgrades = getUpgradesInventory(stack);
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack upgradeStack = upgrades.getStackInSlot(i);

            if (upgradeStack.isEmpty()) continue;

            ItemStack leftStack = ia.addItems(upgradeStack);
            if (!leftStack.isEmpty() && upgradeStack.getItem() instanceof IUpgradeModule) {
                player.dropItem(upgradeStack, false);
            }
        }

        // Return the housing
        AEApi.instance().definitions().materials().emptyStorageCell().maybeStack(1).ifPresent(is -> {
            ItemStack extraA = ia.addItems(is);
            if (!extraA.isEmpty()) player.dropItem(extraA, false);
        });

        if (player.inventoryContainer != null) player.inventoryContainer.detectAndSendChanges();

        return true;
    }

    /**
     * Get the component for this cell tier.
     */
    protected ItemStack getCellComponent(int tier) {
        return ItemNormalStorageComponent.create(tier);
    }

    /**
     * Create a cell ItemStack for the given tier.
     * @param tier 0=64M, 1=256M, 2=1G, 3=2G
     */
    public static ItemStack create(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) tier = 0;

        return new ItemStack(ItemRegistry.NORMAL_STORAGE_CELL, 1, tier);
    }

    /**
     * Get the tier names for model registration.
     */
    public static String[] getTierNames() {
        return TIER_NAMES;
    }
}
