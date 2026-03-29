package com.cells.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.util.ReadableNumberConverter;

import com.cells.Tags;
import com.cells.ItemRegistry;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.util.FluidStackKey;


/**
 * An item that holds a single fluid, gas, or essentia type with up to Integer.MAX_VALUE quantity.
 * <p>
 * This item is spawned when an interface is broken/shrunk and cannot return all contents to the
 * network. It acts as a transient container allowing players to recover lost content.
 * <p>
 * Features:
 * <ul>
 *   <li>Stores up to Integer.MAX_VALUE of a single fluid/gas/essentia</li>
 *   <li>Displays quantity using AE2-style number formatting</li>
 *   <li>Tints texture based on contained fluid color</li>
 *   <li>Right-click on fluid containers or import interfaces to transfer contents</li>
 *   <li>Not craftable or in creative tabs, it only spawns when needed</li>
 * </ul>
 */
public class ItemRecoveryContainer extends Item {

    // NBT keys
    private static final String NBT_TYPE = "DropType";
    private static final String NBT_FLUID_NAME = "FluidName";
    private static final String NBT_FLUID_TAG = "FluidTag";
    private static final String NBT_ITEM_DATA = "ItemData";
    private static final String NBT_AMOUNT = "Amount";
    // For gas/essentia integration
    private static final String NBT_GAS_NAME = "GasName";
    private static final String NBT_ESSENTIA_TAG = "EssentiaTag";

    private static final Map<FluidStackKey, Integer> fluidColorCache = new HashMap<>();

    // Drop type constants
    public static final int TYPE_FLUID = 0;
    public static final int TYPE_GAS = 1;
    public static final int TYPE_ESSENTIA = 2;
    public static final int TYPE_ITEM = 3;

    public ItemRecoveryContainer() {
        setRegistryName(Tags.MODID, "recovery_container");
        setTranslationKey(Tags.MODID + ".recovery_container");
        setMaxStackSize(1);
        // No creative tab - this is a transient item only
    }

    private static ItemStack create(int type, String key, String name, long amount) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(NBT_TYPE, type);
        nbt.setString(key, name);
        nbt.setLong(NBT_AMOUNT, amount);

        ItemStack stack = new ItemStack(ItemRegistry.RECOVERY_CONTAINER);
        stack.setTagCompound(nbt);
        return stack;
    }

    /**
     * Create a new ItemStack containing the given fluid with a long amount.
     *
     * @param fluid The fluid identity (type + NBT, amount ignored)
     * @param amount The amount to store (up to Long.MAX_VALUE)
     * @return A new ItemStack, or empty if fluid is null/invalid
     */
    public static ItemStack createForFluid(@Nullable FluidStack fluid, long amount) {
        if (fluid == null || amount <= 0) return ItemStack.EMPTY;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(NBT_TYPE, TYPE_FLUID);
        nbt.setString(NBT_FLUID_NAME, fluid.getFluid().getName());
        nbt.setLong(NBT_AMOUNT, amount);

        // Store fluid's NBT (for potions, etc.)
        if (fluid.tag != null) nbt.setTag(NBT_FLUID_TAG, fluid.tag.copy());

        ItemStack stack = new ItemStack(ItemRegistry.RECOVERY_CONTAINER);
        stack.setTagCompound(nbt);
        return stack;
    }

    /**
     * Create a new ItemStack containing the given fluid.
     * Uses the fluid's current amount.
     *
     * @param fluid The fluid to store (copied internally)
     * @return A new ItemStack, or empty if fluid is null/empty
     * @deprecated Use {@link #createForFluid(FluidStack, long)} for long amounts
     */
    @Deprecated
    public static ItemStack createForFluid(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return ItemStack.EMPTY;
        return createForFluid(fluid, fluid.amount);
    }

    /**
     * Create a new ItemStack containing the given gas (Mekanism integration).
     *
     * @param gasName The registry name of the gas
     * @param amount  The amount in mB (up to Long.MAX_VALUE)
     * @return A new ItemStack, or empty if invalid
     */
    public static ItemStack createForGas(String gasName, long amount) {
        if (gasName == null || gasName.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        if (!MekanismEnergisticsIntegration.isModLoaded()) return ItemStack.EMPTY;

        return create(TYPE_GAS, NBT_GAS_NAME, gasName, amount);
    }

    /**
     * Create a new ItemStack containing the given essentia (Thaumcraft integration).
     *
     * @param aspectTag The aspect tag name
     * @param amount    The amount in units (up to Long.MAX_VALUE)
     * @return A new ItemStack, or empty if invalid
     */
    public static ItemStack createForEssentia(String aspectTag, long amount) {
        if (aspectTag == null || aspectTag.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return ItemStack.EMPTY;

        return create(TYPE_ESSENTIA, NBT_ESSENTIA_TAG, aspectTag, amount);
    }

    /**
     * Create a new ItemStack containing the given item with a long amount.
     *
     * @param item The item identity (type + NBT, count ignored)
     * @param amount The amount to store (up to Long.MAX_VALUE)
     * @return A new ItemStack, or empty if item is null/invalid
     */
    public static ItemStack createForItem(@Nullable ItemStack item, long amount) {
        if (item == null || item.isEmpty() || amount <= 0) return ItemStack.EMPTY;

        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(NBT_TYPE, TYPE_ITEM);
        nbt.setLong(NBT_AMOUNT, amount);

        // Store the full item data (including NBT)
        NBTTagCompound itemNbt = new NBTTagCompound();
        ItemStack identity = item.copy();
        identity.setCount(1);
        identity.writeToNBT(itemNbt);
        nbt.setTag(NBT_ITEM_DATA, itemNbt);

        ItemStack stack = new ItemStack(ItemRegistry.RECOVERY_CONTAINER);
        stack.setTagCompound(nbt);
        return stack;
    }

    /**
     * Get the type (TYPE_FLUID, TYPE_GAS, or TYPE_ESSENTIA).
     */
    public static int getType(ItemStack stack) {
        if (!stack.hasTagCompound()) return TYPE_FLUID;

        return stack.getTagCompound().getInteger(NBT_TYPE);
    }

    public static String getTypeKey(int type) {
        switch (type) {
            case TYPE_FLUID:
                return "fluid";
            case TYPE_GAS:
                return "gas";
            case TYPE_ESSENTIA:
                return "essentia";
            case TYPE_ITEM:
                return "item";
            default:
                return "unknown";
        }
    }

    /**
     * Get the stored amount as long.
     */
    public static long getAmount(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;

        // Try long first, fall back to int for backward compat
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt.hasKey(NBT_AMOUNT, Constants.NBT.TAG_LONG)) {
            return nbt.getLong(NBT_AMOUNT);
        }
        return nbt.getInteger(NBT_AMOUNT);
    }

    /**
     * Set the stored amount. If amount <= 0, the stack should be discarded.
     */
    public static void setAmount(ItemStack stack, long amount) {
        if (!stack.hasTagCompound()) return;

        stack.getTagCompound().setLong(NBT_AMOUNT, amount);
    }

    /**
     * Get the fluid name (only valid for TYPE_FLUID).
     */
    @Nullable
    public static String getFluidName(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        if (getType(stack) != TYPE_FLUID) return null;

        return stack.getTagCompound().getString(NBT_FLUID_NAME);
    }

    /**
     * Get the FluidStack for this drop (only valid for TYPE_FLUID).
     * Amount is clamped to Integer.MAX_VALUE for FluidStack compatibility.
     */
    @Nullable
    public static FluidStack getFluidStack(ItemStack stack) {
        String fluidName = getFluidName(stack);
        if (fluidName == null || fluidName.isEmpty()) return null;

        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) return null;

        // Clamp to int for FluidStack (external API limitation)
        int amount = (int) Math.min(getAmount(stack), Integer.MAX_VALUE);
        FluidStack fluidStack = new FluidStack(fluid, amount);

        // Restore fluid's NBT (for potions, etc.)
        NBTTagCompound itemNbt = stack.getTagCompound();
        if (itemNbt != null && itemNbt.hasKey(NBT_FLUID_TAG, Constants.NBT.TAG_COMPOUND)) {
            fluidStack.tag = itemNbt.getCompoundTag(NBT_FLUID_TAG).copy();
        }

        return fluidStack;
    }

    /**
     * Get the gas name (only valid for TYPE_GAS).
     */
    @Nullable
    public static String getGasName(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        if (getType(stack) != TYPE_GAS) return null;

        return stack.getTagCompound().getString(NBT_GAS_NAME);
    }

    /**
     * Get the essentia aspect tag (only valid for TYPE_ESSENTIA).
     */
    @Nullable
    public static String getEssentiaTag(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        if (getType(stack) != TYPE_ESSENTIA) return null;

        return stack.getTagCompound().getString(NBT_ESSENTIA_TAG);
    }

    /**
     * Get the contained ItemStack (only valid for TYPE_ITEM).
     * Amount is clamped to the item's max stack size for ItemStack compatibility.
     */
    @Nullable
    public static ItemStack getContainedItem(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        if (getType(stack) != TYPE_ITEM) return null;

        NBTTagCompound nbt = stack.getTagCompound();
        if (!nbt.hasKey(NBT_ITEM_DATA, Constants.NBT.TAG_COMPOUND)) return null;

        ItemStack contained = new ItemStack(nbt.getCompoundTag(NBT_ITEM_DATA));
        if (contained.isEmpty()) return null;

        // Clamp to item's max stack size for ItemStack API
        int amount = (int) Math.min(getAmount(stack), contained.getMaxStackSize());
        contained.setCount(amount);
        return contained;
    }

    // ============================== Display ==============================

    @Override
    @Nonnull
    @SideOnly(Side.CLIENT)
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        int type = getType(stack);
        String typeName = I18n.format("cells.type." + getTypeKey(type));
        String contentName = getContentDisplayName(stack, type);

        if (contentName == null || contentName.isEmpty()) {
            return I18n.format("item.cells.recovery_container.name.empty");
        }

        return I18n.format("item.cells.recovery_container.name", typeName, contentName);
    }

    @Nullable
    private static String getContentDisplayName(ItemStack stack, int type) {
        switch (type) {
            case TYPE_FLUID:
                FluidStack fluidStack = getFluidStack(stack);
                return (fluidStack != null) ? fluidStack.getLocalizedName() : null;

            case TYPE_GAS:
                if (!MekanismEnergisticsIntegration.isModLoaded()) return null;

                return GasDropHelper.getGasDisplayName(getGasName(stack));

            case TYPE_ESSENTIA:
                if (!ThaumicEnergisticsIntegration.isModLoaded()) return null;

                return EssentiaDropHelper.getEssentiaDisplayName(getEssentiaTag(stack));

            case TYPE_ITEM:
                ItemStack contained = getContainedItem(stack);
                return (contained != null && !contained.isEmpty()) ? contained.getDisplayName() : null;

            default:
                return null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<String> tooltip,
                               @Nonnull ITooltipFlag flag) {
        int type = getType(stack);
        long amount = getAmount(stack);
        String typeKey = getTypeKey(type);
        String typeName = I18n.format("cells.type." + typeKey);
        String unitName = I18n.format("cells.unit." + typeKey);

        tooltip.add("§7" + I18n.format("tooltip.cells.recovery_container.type", typeName));

        // Exact amount with type-appropriate unit (formatted as readable number)
        String formattedAmount = ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
        tooltip.add("§7" + I18n.format("tooltip.cells.recovery_container.amount", formattedAmount, unitName));

        // Usage hint
        tooltip.add("");
        tooltip.add("§8" + I18n.format("tooltip.cells.recovery_container.usage"));
    }

    /**
     * Show the AE2-style formatted amount instead of "1" for stack count.
     */
    @Override
    public boolean showDurabilityBar(@Nonnull ItemStack stack) {
        // Don't show durability bar
        return false;
    }

    /**
     * Custom stack count display showing amount in AE2 format.
     */
    @Nullable
    public static String getStackCountDisplay(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemRecoveryContainer)) return null;

        long amount = getAmount(stack);
        if (amount <= 0) return null;

        return ReadableNumberConverter.INSTANCE.toSlimReadableForm(amount);
    }

    // ============================== Color ==============================

    /**
     * Get the color for tinting this item's texture.
     *
     * @param stack     The ItemStack
     * @param tintIndex The tint layer (0 = base ball.png, 1 = overlay)
     * @return The color as 0xRRGGBB, or -1 (white/no tint) for overlay
     */
    @SideOnly(Side.CLIENT)
    public static int getColor(ItemStack stack, int tintIndex) {
        // Only tint layer 0 (the ball.png base texture)
        if (tintIndex != 0) return -1;

        int type = getType(stack);
        switch (type) {
            case TYPE_FLUID:
                FluidStack fluidStack = getFluidStack(stack);
                if (fluidStack != null && fluidStack.getFluid() != null) {
                    return getFluidColor(fluidStack);
                }
                break;

            case TYPE_GAS:
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    return GasDropHelper.getGasColor(getGasName(stack));
                }
                break;

            case TYPE_ESSENTIA:
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    return EssentiaDropHelper.getEssentiaColor(getEssentiaTag(stack));
                }
                break;

            case TYPE_ITEM:
                // Items don't have a simple color - use default white
                // The renderer will show the item icon instead of tinting
                break;
        }

        // Default to white if no color found
        return 0xFFFFFF;
    }

    /**
     * Get the color for a fluid, falling back to texture sampling if getColor() returns default white.
     * <p>
     * Many fluids don't override getColor() and return 0xFFFFFF (white) by default.
     * For these, we sample the center pixel of the fluid's still texture to get a representative color.
     *
     * @param fluidStack The fluid to get color for
     * @return The color as 0xRRGGBB
     */
    @SideOnly(Side.CLIENT)
    private static int getFluidColor(FluidStack fluidStack) {
        Fluid fluid = fluidStack.getFluid();
        int color = fluid.getColor(fluidStack);

        // If the fluid returns default white, try to extract color from its texture
        if (color == 0xFFFFFF || color == -1) color = getFluidTextureColor(fluid, fluidStack);

        return color;
    }

    private static int cacheColor(FluidStackKey key, int color) {
        fluidColorCache.put(key, color);
        return color;
    }

    /**
     * Extract an approximate color from the fluid's still texture by sampling the center pixel.
     *
     * @param fluid      The fluid
     * @param fluidStack The fluid stack (for texture variants)
     * @return The sampled color, or white if texture unavailable
     */
    @SideOnly(Side.CLIENT)
    private static int getFluidTextureColor(Fluid fluid, FluidStack fluidStack) {
        FluidStackKey key = FluidStackKey.of(fluidStack);
        if (fluidColorCache.containsKey(key)) return fluidColorCache.get(key);

        try {
            if (fluid.getStill(fluidStack) == null) return cacheColor(key, 0xFFFFFF);

            TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(fluid.getStill(fluidStack).toString());

            if (sprite.getFrameCount() == 0) return cacheColor(key, 0xFFFFFF);

            // Sample the center pixel of the first frame
            int width = sprite.getIconWidth();
            int height = sprite.getIconHeight();
            int centerX = width / 2;
            int centerY = height / 2;

            // getFrameTextureData returns [frame][pixel data as ARGB]
            int[][] frameData = sprite.getFrameTextureData(0);
            if (frameData.length == 0 || frameData[0] == null) {
                return cacheColor(key, 0xFFFFFF);
            }

            int pixelIndex = centerY * width + centerX;
            if (pixelIndex >= frameData[0].length) return cacheColor(key, 0xFFFFFF);

            int argb = frameData[0][pixelIndex];

            // Extract RGB, ignore alpha
            return cacheColor(key, argb & 0x00FFFFFF);
        } catch (Exception e) {
            // If anything goes wrong, fall back to white
            return cacheColor(key, 0xFFFFFF);
        }
    }

    // ============================== Interactions ==============================

    /**
     * Use onItemUseFirst to handle block interactions BEFORE block's onBlockActivated.
     * This prevents the block GUI from opening when using Recovery Container.
     */
    @Override
    @Nonnull
    public EnumActionResult onItemUseFirst(@Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos,
                                           @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ,
                                           @Nonnull EnumHand hand) {
        ItemStack heldStack = player.getHeldItem(hand);
        if (heldStack.isEmpty()) return EnumActionResult.PASS;

        int type = getType(heldStack);
        int amount = (int) Math.min(getAmount(heldStack), Integer.MAX_VALUE);
        if (amount <= 0) return EnumActionResult.PASS;

        // Check if there's a valid target tile entity before deciding to handle this interaction
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return EnumActionResult.PASS;

        // On client side, always return SUCCESS to prevent the block GUI from opening.
        // The transfer (or failure message) will be handled server-side.
        if (world.isRemote) return EnumActionResult.SUCCESS;

        int transferred = 0;

        switch (type) {
            case TYPE_FLUID:
                transferred = tryTransferFluid(heldStack, te, facing);
                break;

            case TYPE_GAS:
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    transferred = GasDropHelper.tryTransferGas(heldStack, te, facing);
                }
                break;

            case TYPE_ESSENTIA:
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    transferred = EssentiaDropHelper.tryTransferEssentia(heldStack, te, facing);
                }
                break;
        }

        // TODO: add special interaction for Import Interfaces to allow transfering more than Integer.MAX_VALUE

        // Use type-appropriate unit for message
        String typeKey = getTypeKey(type);
        String unitName = new TextComponentTranslation("cells.unit." + typeKey).getFormattedText();

        if (transferred > 0) {
            int remaining = amount - transferred;
            if (remaining <= 0) {
                player.setHeldItem(hand, ItemStack.EMPTY);
            } else {
                setAmount(heldStack, remaining);
            }

            player.sendStatusMessage(new TextComponentTranslation(
                "cells.recovery_container.transferred",
                transferred,
                unitName
            ), true);
        } else {
            // No transfer occurred - inform player
            player.sendStatusMessage(new TextComponentTranslation(
                "cells.recovery_container.no_transfer",
                new TextComponentTranslation("cells.type." + typeKey)
            ), true);
        }

        // Always return SUCCESS to prevent block activation (opening container GUI)
        return EnumActionResult.SUCCESS;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
                                                    @Nonnull EnumHand hand) {
        // Only onItemUseFirst handles block interactions
        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    /**
    /**
     * Try to transfer fluid from this drop to a tile entity via fluid handler capability.
     *
     * @return The amount transferred
     */
    private static int tryTransferFluid(ItemStack dropStack, TileEntity te, EnumFacing facing) {
        FluidStack fluidStack = getFluidStack(dropStack);
        if (fluidStack == null) return 0;

        // Use standard fluid handler capability
        IFluidHandler fluidHandler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
        if (fluidHandler != null) return fluidHandler.fill(fluidStack, true);

        return 0;
    }

    // ============================== Integration helpers (inner classes) ==============================

    /**
     * Gas integration helper. Only loaded if Mekanism Energistics is present.
     */
    public static class GasDropHelper {

        private GasDropHelper() {}

        @Nullable
        public static String getGasDisplayName(@Nullable String gasName) {
            if (gasName == null || gasName.isEmpty()) return null;

            try {
                return GasDropHelperImpl.getDisplayName(gasName);
            } catch (NoClassDefFoundError e) {
                return gasName;
            }
        }

        public static int getGasColor(@Nullable String gasName) {
            if (gasName == null || gasName.isEmpty()) return 0xFFFFFF;

            try {
                return GasDropHelperImpl.getColor(gasName);
            } catch (NoClassDefFoundError e) {
                return 0xFFFFFF;
            }
        }

        public static int tryTransferGas(ItemStack dropStack, TileEntity te, EnumFacing facing) {
            try {
                return GasDropHelperImpl.tryTransfer(dropStack, te, facing);
            } catch (NoClassDefFoundError e) {
                return 0;
            }
        }

        public static boolean canAcceptGas(TileEntity te, EnumFacing facing) {
            try {
                return GasDropHelperImpl.canAccept(te, facing);
            } catch (NoClassDefFoundError e) {
                return false;
            }
        }

        /**
         * Inner implementation that references Mekanism classes directly.
         * Only loaded when Mekanism is present.
         */
        private static class GasDropHelperImpl {

            static String getDisplayName(String gasName) {
                mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(gasName);
                return (gas != null) ? gas.getLocalizedName() : gasName;
            }

            static int getColor(String gasName) {
                mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(gasName);
                if (gas == null) return 0xFFFFFF;

                // Gas tint is ARGB, extract RGB
                int tint = gas.getTint();
                return tint & 0x00FFFFFF;
            }

            static boolean canAccept(TileEntity te, EnumFacing facing) {
                return te.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, facing);
            }

            static int tryTransfer(ItemStack dropStack, TileEntity te, EnumFacing facing) {
                String gasName = getGasName(dropStack);
                if (gasName == null) return 0;

                mekanism.api.gas.Gas gas = mekanism.api.gas.GasRegistry.getGas(gasName);
                if (gas == null) return 0;

                int amount = (int) Math.min(getAmount(dropStack), Integer.MAX_VALUE);
                mekanism.api.gas.GasStack gasStack = new mekanism.api.gas.GasStack(gas, amount);

                // Check for gas handler capability
                if (te.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, facing)) {
                    mekanism.api.gas.IGasHandler handler =
                        te.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, facing);
                    if (handler != null) return handler.receiveGas(facing, gasStack, true);
                }

                return 0;
            }
        }
    }

    /**
     * Essentia integration helper. Only loaded if Thaumic Energistics is present.
     */
    public static class EssentiaDropHelper {

        private EssentiaDropHelper() {}

        @Nullable
        public static String getEssentiaDisplayName(@Nullable String aspectTag) {
            if (aspectTag == null || aspectTag.isEmpty()) return null;

            try {
                return EssentiaDropHelperImpl.getDisplayName(aspectTag);
            } catch (NoClassDefFoundError e) {
                return aspectTag;
            }
        }

        public static int getEssentiaColor(@Nullable String aspectTag) {
            if (aspectTag == null || aspectTag.isEmpty()) return 0xFFFFFF;

            try {
                return EssentiaDropHelperImpl.getColor(aspectTag);
            } catch (NoClassDefFoundError e) {
                return 0xFFFFFF;
            }
        }

        public static int tryTransferEssentia(ItemStack dropStack, TileEntity te, EnumFacing facing) {
            try {
                return EssentiaDropHelperImpl.tryTransfer(dropStack, te, facing);
            } catch (NoClassDefFoundError e) {
                return 0;
            }
        }

        public static boolean canAcceptEssentia(TileEntity te) {
            try {
                return EssentiaDropHelperImpl.canAccept(te);
            } catch (NoClassDefFoundError e) {
                return false;
            }
        }

        /**
         * Inner implementation that references Thaumcraft/ThaumicEnergistics classes directly.
         * Only loaded when Thaumic Energistics is present.
         */
        private static class EssentiaDropHelperImpl {

            static String getDisplayName(String aspectTag) {
                thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(aspectTag);
                return (aspect != null) ? aspect.getName() : aspectTag;
            }

            static int getColor(String aspectTag) {
                thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(aspectTag);
                if (aspect == null) return 0xFFFFFF;

                return aspect.getColor();
            }

            static boolean canAccept(TileEntity te) {
                return te instanceof thaumcraft.api.aspects.IAspectContainer;
            }

            static int tryTransfer(ItemStack dropStack, TileEntity te, EnumFacing facing) {
                String aspectTag = getEssentiaTag(dropStack);
                if (aspectTag == null) return 0;

                thaumcraft.api.aspects.Aspect aspect = thaumcraft.api.aspects.Aspect.getAspect(aspectTag);
                if (aspect == null) return 0;

                int amount = (int) Math.min(getAmount(dropStack), Integer.MAX_VALUE);

                // Check for essentia transport capability (Thaumcraft jars, etc.)
                if (te instanceof thaumcraft.api.aspects.IAspectContainer) {
                    thaumcraft.api.aspects.IAspectContainer container =
                        (thaumcraft.api.aspects.IAspectContainer) te;

                    // Try to add essentia - returns amount actually added
                    int added = container.addToContainer(aspect, amount);

                    // addToContainer returns what was added
                    return added;
                }

                return 0;
            }
        }
    }
}
