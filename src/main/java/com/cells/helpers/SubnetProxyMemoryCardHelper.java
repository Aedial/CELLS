package com.cells.helpers;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional.Method;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.definitions.IItemDefinition;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.item.AEItemStack;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.util.ThEUtil;

import com.cells.api.FilterHostUtil;
import com.cells.network.sync.ResourceType;
import com.cells.parts.CellsPartType;
import com.mekeng.github.common.ItemAndBlocks;
import com.mekeng.github.common.item.ItemDummyGas;

import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTank;


/**
 * Adapts AE2 storage-bus memory card payloads to the Subnet Proxy format.
 * <p>
 * The proxy stores its filters in an {@link AppEngInternalInventory} of
 * display stacks, while AE2 storage buses serialize either item filters via
 * {@link AppEngInternalAEInventory} or fluid filters via
 * {@link AEFluidInventory}. This helper translates those card payloads so the
 * proxy can load them through one internal upload path.
 */
public final class SubnetProxyMemoryCardHelper {

    private static final String FALLBACK_ITEM_STORAGE_BUS_NAME = "item.appliedenergistics2.part.storage_bus";
    private static final String FALLBACK_FLUID_STORAGE_BUS_NAME = "item.appliedenergistics2.part.fluid_storage_bus";
    private static final String FALLBACK_GAS_STORAGE_BUS_NAME = "mekeng:gas_storage_bus";
    private static final String FALLBACK_GAS_STORAGE_BUS_ALT_NAME = "item.mekeng.gas_storage_bus";
    private static final String FALLBACK_ESSENTIA_STORAGE_BUS_NAME = "thaumicenergistics:essentia_storage_bus";
    private static final String FALLBACK_ESSENTIA_STORAGE_BUS_ALT_NAME = "item.thaumicenergistics.essentia_storage_bus";
    private static final String SUBNET_PROXY_FRONT_NAME = CellsPartType.SUBNET_PROXY_FRONT.getUnlocalizedName();

    private SubnetProxyMemoryCardHelper() {
    }

    public static String getMemoryCardName() {
        return SUBNET_PROXY_FRONT_NAME;
    }

    /**
     * Prepare a memory-card payload for the Subnet Proxy.
     * Returns null when the stored card data is not compatible.
     */
    @Nullable
    public static NBTTagCompound prepareUploadData(String storedName, NBTTagCompound sourceData) {
        if (storedName == null || sourceData == null) return null;

        if (SUBNET_PROXY_FRONT_NAME.equals(storedName)) return sourceData.copy();

        if (matchesItemStorageBus(storedName)) return prepareItemStorageBusUpload(sourceData);
        if (matchesFluidStorageBus(storedName)) return prepareFluidStorageBusUpload(sourceData);
        if (Loader.isModLoaded("mekeng") && matchesGasStorageBus(storedName)) {
            return prepareGasStorageBusUpload(sourceData);
        }
        if (Loader.isModLoaded("thaumicenergistics") && matchesEssentiaStorageBus(storedName)) {
            return prepareEssentiaStorageBusUpload(sourceData);
        }

        return null;
    }

    private static boolean matchesItemStorageBus(String storedName) {
        return getTranslationKey(AEApi.instance().definitions().parts().storageBus(), FALLBACK_ITEM_STORAGE_BUS_NAME)
            .equals(storedName);
    }

    private static boolean matchesFluidStorageBus(String storedName) {
        return getTranslationKey(AEApi.instance().definitions().parts().fluidStorageBus(), FALLBACK_FLUID_STORAGE_BUS_NAME)
            .equals(storedName);
    }

    private static boolean matchesGasStorageBus(String storedName) {
        return matchesTranslatedName(
            storedName,
            "gas_storage_bus",
            FALLBACK_GAS_STORAGE_BUS_NAME,
            FALLBACK_GAS_STORAGE_BUS_ALT_NAME);
    }

    private static boolean matchesEssentiaStorageBus(String storedName) {
        return matchesTranslatedName(
            storedName,
            "essentia_storage_bus",
            FALLBACK_ESSENTIA_STORAGE_BUS_NAME,
            FALLBACK_ESSENTIA_STORAGE_BUS_ALT_NAME);
    }

    private static boolean matchesTranslatedName(String storedName, String suffix, String... aliases) {
        for (String alias : aliases) {
            if (alias.equals(storedName)) return true;
        }

        return storedName.equals(suffix)
            || storedName.endsWith('.' + suffix)
            || storedName.endsWith(':' + suffix);
    }

    private static String getTranslationKey(IItemDefinition definition, String fallback) {
        Optional<ItemStack> stack = definition.maybeStack(1);
        if (!stack.isPresent()) return fallback;

        return stack.get().getTranslationKey();
    }

    private static NBTTagCompound prepareItemStorageBusUpload(NBTTagCompound sourceData) {
        Map<Integer, ItemStack> importedFilters = new TreeMap<>();
        NBTTagCompound sourceConfig = sourceData.getCompoundTag("config");

        for (String key : sourceConfig.getKeySet()) {
            int slot = parseIndexedSlot(key, "#");
            if (slot < 0) continue;

            ItemStack stack = itemToFilterStack(AEItemStack.fromNBT(sourceConfig.getCompoundTag(key)));
            if (stack.isEmpty()) continue;

            importedFilters.put(slot, stack);
        }

        return createSubnetProxyUpload(sourceData, importedFilters, ResourceType.ITEM);
    }

    private static NBTTagCompound prepareFluidStorageBusUpload(NBTTagCompound sourceData) {
        Map<Integer, ItemStack> importedFilters = new TreeMap<>();
        NBTTagCompound sourceConfig = sourceData.getCompoundTag("config");

        for (String key : sourceConfig.getKeySet()) {
            int slot = parseIndexedSlot(key, "#");
            if (slot < 0) continue;

            ItemStack stack = fluidToFilterStack(AEFluidStack.fromNBT(sourceConfig.getCompoundTag(key)));
            if (stack.isEmpty()) continue;

            importedFilters.put(slot, stack);
        }

        return createSubnetProxyUpload(sourceData, importedFilters, ResourceType.FLUID);
    }

    @Method(modid = "mekeng")
    private static NBTTagCompound prepareGasStorageBusUpload(NBTTagCompound sourceData) {
        Map<Integer, ItemStack> importedFilters = new TreeMap<>();
        NBTTagCompound sourceConfig = sourceData.getCompoundTag("config");

        for (String key : sourceConfig.getKeySet()) {
            int slot = parseIndexedSlot(key, "#");
            if (slot < 0) continue;

            GasTank tank = GasTank.readFromNBT(sourceConfig.getCompoundTag(key));
            GasStack gas = tank == null ? null : tank.getGas();
            ItemStack stack = gasToFilterStack(gas);
            if (stack.isEmpty()) continue;

            importedFilters.put(slot, stack);
        }

        return createSubnetProxyUpload(sourceData, importedFilters, ResourceType.GAS);
    }

    @Method(modid = "thaumicenergistics")
    private static NBTTagCompound prepareEssentiaStorageBusUpload(NBTTagCompound sourceData) {
        Map<Integer, ItemStack> importedFilters = new TreeMap<>();
        NBTTagCompound sourceConfig = sourceData.getCompoundTag("config");

        for (String key : sourceConfig.getKeySet()) {
            int slot = parseIndexedSlot(key, "aspect#");
            if (slot < 0) continue;

            ItemStack stack = essentiaToFilterStack(Aspect.getAspect(sourceConfig.getString(key)));
            if (stack.isEmpty()) continue;

            importedFilters.put(slot, stack);
        }

        return createSubnetProxyUpload(sourceData, importedFilters, ResourceType.ESSENTIA);
    }

    private static NBTTagCompound createSubnetProxyUpload(NBTTagCompound sourceData,
                                                          Map<Integer, ItemStack> importedFilters,
                                                          ResourceType filterMode) {
        AppEngInternalInventory proxyConfig = createProxyConfig(importedFilters);
        NBTTagCompound output = new NBTTagCompound();
        output.setTag("config", proxyConfig.serializeNBT());
        output.setInteger("filterMode", filterMode.ordinal());

        if (sourceData.hasKey("priority")) {
            output.setInteger("priority", sourceData.getInteger("priority"));
        }

        FuzzyMode fuzzyMode = getStoredFuzzyMode(sourceData);
        if (fuzzyMode != null) output.setInteger("fuzzyMode", fuzzyMode.ordinal());

        return output;
    }

    private static AppEngInternalInventory createProxyConfig(Map<Integer, ItemStack> importedFilters) {
        int slotCount = 1;
        for (int slot : importedFilters.keySet()) {
            slotCount = Math.max(slotCount, slot + 1);
        }

        AppEngInternalInventory proxyConfig = new AppEngInternalInventory(null, slotCount, 1);
        for (Map.Entry<Integer, ItemStack> entry : importedFilters.entrySet()) {
            proxyConfig.setStackInSlot(entry.getKey(), FilterHostUtil.normalizeFilter(entry.getValue()));
        }

        return proxyConfig;
    }

    private static int parseIndexedSlot(String key, String prefix) {
        if (!key.startsWith(prefix)) return -1;

        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    @Nullable
    private static FuzzyMode getStoredFuzzyMode(NBTTagCompound sourceData) {
        if (!sourceData.hasKey(Settings.FUZZY_MODE.name())) return null;

        String rawMode = sourceData.getString(Settings.FUZZY_MODE.name());
        for (FuzzyMode mode : FuzzyMode.values()) {
            if (mode.name().equals(rawMode) || mode.toString().equals(rawMode)) return mode;
        }

        return null;
    }

    private static ItemStack itemToFilterStack(@Nullable IAEItemStack aeItem) {
        if (aeItem == null) return ItemStack.EMPTY;

        return FilterHostUtil.normalizeFilter(aeItem.createItemStack());
    }

    private static ItemStack fluidToFilterStack(IAEFluidStack aeFluid) {
        if (aeFluid == null) return ItemStack.EMPTY;

        FluidStack fluid = aeFluid.getFluidStack();
        if (fluid == null) return ItemStack.EMPTY;

        FluidStack filterFluid = fluid.copy();
        filterFluid.amount = Fluid.BUCKET_VOLUME;

        ItemStack dummyStack = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack(1)
            .orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return ItemStack.EMPTY;
        if (!(dummyStack.getItem() instanceof FluidDummyItem)) return ItemStack.EMPTY;

        FluidDummyItem dummyItem = (FluidDummyItem) dummyStack.getItem();
        dummyItem.setFluidStack(dummyStack, filterFluid);

        return dummyStack;
    }

    @Method(modid = "mekeng")
    private static ItemStack gasToFilterStack(@Nullable GasStack gas) {
        if (gas == null) return ItemStack.EMPTY;

        GasStack filterGas = gas.copy();
        filterGas.amount = Fluid.BUCKET_VOLUME;

        ItemStack dummyStack = new ItemStack(ItemAndBlocks.DUMMY_GAS);
        if (!(dummyStack.getItem() instanceof ItemDummyGas)) return ItemStack.EMPTY;

        ItemDummyGas dummyItem = (ItemDummyGas) dummyStack.getItem();
        dummyItem.setGasStack(dummyStack, filterGas);

        return dummyStack;
    }

    @Method(modid = "thaumicenergistics")
    private static ItemStack essentiaToFilterStack(@Nullable Aspect aspect) {
        if (aspect == null) return ItemStack.EMPTY;

        ItemStack dummyStack = ThEApi.instance().items().dummyAspect().maybeStack(1).orElse(ItemStack.EMPTY);
        if (dummyStack.isEmpty()) return ItemStack.EMPTY;

        return ThEUtil.setAspect(dummyStack, aspect);
    }
}