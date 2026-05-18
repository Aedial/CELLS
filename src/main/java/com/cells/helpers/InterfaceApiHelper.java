package com.cells.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.items.FluidDummyItem;
import appeng.util.item.AEItemStack;

import com.cells.api.FilterHostUtil;
import com.cells.api.IInterfaceHost;
import com.cells.api.ResourcePreviewEntry;
import com.cells.api.ResourceType;
import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.gui.QuickAddHelper;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;


/**
 * Builds public CELLS API views without duplicating a host's filter or upgrade state.
 */
public final class InterfaceApiHelper {

    private static final String MEKENG_MODID = "mekeng";
    private static final String THAUMICENERGISTICS_MODID = "thaumicenergistics";

    private InterfaceApiHelper() {}

    @Nonnull
    public static List<IInterfaceHost> createInterfaceHosts(com.cells.blocks.interfacebase.IInterfaceHost owner,
                                                            IInterfaceLogic logic,
                                                            Collection<EnumFacing> targetFacings) {
        if (owner == null) return Collections.emptyList();

        List<IInterfaceHost> hosts = new ArrayList<>(1);
        // Single-direction interface: no directional label, no type label needed.
        addInterfaceHost(hosts, owner, logic, targetFacings, owner.isExport(), false, false);
        return Collections.unmodifiableList(hosts);
    }

    @Nonnull
    public static List<IInterfaceHost> createInterfaceHosts(ICombinedInterfaceHost owner,
                                                            Collection<EnumFacing> targetFacings) {
        if (owner == null) return Collections.emptyList();

        // Each combined interface host needs a type-label prefix so the user can tell
        // Item/Fluid/Gas/Essentia views apart when multiple appear in the terminal.
        List<IInterfaceHost> hosts = new ArrayList<>(4);
        addInterfaceHost(hosts, owner, owner.getItemLogic(), targetFacings, owner.isExport(), false, true);
        addInterfaceHost(hosts, owner, owner.getFluidLogic(), targetFacings, owner.isExport(), false, true);
        addInterfaceHost(hosts, owner, owner.getGasLogic(), targetFacings, owner.isExport(), false, true);
        addInterfaceHost(hosts, owner, owner.getEssentiaLogic(), targetFacings, owner.isExport(), false, true);
        return Collections.unmodifiableList(hosts);
    }

    @Nonnull
    public static List<IInterfaceHost> createInterfaceHosts(IIOInterfaceHost owner,
                                                            Collection<EnumFacing> targetFacings) {
        if (owner == null) return Collections.emptyList();

        List<IInterfaceHost> hosts = new ArrayList<>(2);
        // directionalView=true so the terminal shows the Import/Export prefix.
        // typeLabel=false because the IO view already distinguishes directions.
        addInterfaceHost(hosts, owner, owner.getImportLogic(), targetFacings, false, true, false);
        addInterfaceHost(hosts, owner, owner.getExportLogic(), targetFacings, true, true, false);
        return Collections.unmodifiableList(hosts);
    }

    private static void addInterfaceHost(List<IInterfaceHost> hosts,
                                         com.cells.blocks.interfacebase.IInterfaceHost owner,
                                         IInterfaceLogic logic,
                                         Collection<EnumFacing> targetFacings,
                                         boolean export,
                                         boolean directionalView,
                                         boolean typeLabel) {
        if (!(logic instanceof AbstractResourceInterfaceLogic)) return;

        ResourceType resourceType = toApiResourceType(logic);
        if (resourceType == null) return;

        hosts.add(new LogicInterfaceHost(
            owner,
            (AbstractResourceInterfaceLogic<?, ?, ?>) logic,
            resourceType,
            targetFacings,
            export,
            directionalView,
            typeLabel));
    }

    @Nullable
    private static ResourceType toApiResourceType(IInterfaceLogic logic) {
        if (logic == null || logic.getTypeName() == null) return null;

        switch (logic.getTypeName().toLowerCase(Locale.ROOT)) {
            case "item":
                return ResourceType.ITEM;
            case "fluid":
                return ResourceType.FLUID;
            case "gas":
                return ResourceType.GAS;
            case "essentia":
                return ResourceType.ESSENTIA;
            default:
                return null;
        }
    }

    @Nonnull
    private static List<EnumFacing> freezeTargetFacings(Collection<EnumFacing> targetFacings) {
        if (targetFacings == null || targetFacings.isEmpty()) return Collections.emptyList();

        LinkedHashSet<EnumFacing> uniqueFacings = new LinkedHashSet<>();
        for (EnumFacing facing : targetFacings) {
            if (facing != null) uniqueFacings.add(facing);
        }

        return Collections.unmodifiableList(new ArrayList<>(uniqueFacings));
    }

    @Nonnull
    private static List<ResourcePreviewEntry> collectAdjacentPreviewEntries(com.cells.blocks.interfacebase.IInterfaceHost owner,
                                                                            ResourceType resourceType,
                                                                            EnumFacing facing,
                                                                            int limit) {
        TileEntity targetTile = getAdjacentTile(owner, facing);
        if (targetTile == null) return Collections.emptyList();

        List<ResourcePreviewEntry> previewEntries = new ArrayList<>();
        switch (resourceType) {
            case ITEM:
                collectItemPreviewEntries(targetTile, facing, previewEntries, limit);
                break;
            case FLUID:
                collectFluidPreviewEntries(targetTile, facing, previewEntries, limit);
                break;
            case GAS:
                collectGasPreviewEntries(targetTile, facing, previewEntries, limit);
                break;
            case ESSENTIA:
                collectEssentiaPreviewEntries(targetTile, previewEntries, limit);
                break;
            default:
                break;
        }

        return previewEntries;
    }

    @Nullable
    private static TileEntity getAdjacentTile(com.cells.blocks.interfacebase.IInterfaceHost owner, EnumFacing facing) {
        if (owner == null || facing == null) return null;

        World world = owner.getHostWorld();
        BlockPos pos = owner.getHostPos();
        if (world == null || pos == null) return null;

        return world.getTileEntity(pos.offset(facing));
    }

    private static void collectItemPreviewEntries(TileEntity targetTile,
                                                  EnumFacing facing,
                                                  List<ResourcePreviewEntry> previewEntries,
                                                  int limit) {
        IItemHandler itemHandler = targetTile.getCapability(
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
            facing.getOpposite());
        if (itemHandler == null) return;

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            addPreviewEntry(previewEntries, ResourceType.ITEM, stack, stack.getCount(), limit);
        }
    }

    private static void collectFluidPreviewEntries(TileEntity targetTile,
                                                   EnumFacing facing,
                                                   List<ResourcePreviewEntry> previewEntries,
                                                   int limit) {
        IFluidHandler fluidHandler = targetTile.getCapability(
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
            facing.getOpposite());
        if (fluidHandler == null) return;

        for (IFluidTankProperties tank : fluidHandler.getTankProperties()) {
            if (tank == null) continue;

            FluidStack contents = tank.getContents();
            if (contents == null || contents.amount <= 0) continue;

            addPreviewEntry(previewEntries, ResourceType.FLUID, toFluidDisplayStack(contents), contents.amount, limit);
        }
    }

    private static void collectGasPreviewEntries(TileEntity targetTile,
                                                 EnumFacing facing,
                                                 List<ResourcePreviewEntry> previewEntries,
                                                 int limit) {
        if (!MekanismEnergisticsIntegration.isModLoaded()) return;

        collectGasPreviewEntriesInternal(targetTile, facing, previewEntries, limit);
    }

    @Optional.Method(modid = MEKENG_MODID)
    private static void collectGasPreviewEntriesInternal(TileEntity targetTile,
                                                         EnumFacing facing,
                                                         List<ResourcePreviewEntry> previewEntries,
                                                         int limit) {
        if (!targetTile.hasCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY, facing.getOpposite())) {
            return;
        }

        mekanism.api.gas.IGasHandler gasHandler = targetTile.getCapability(
            mekanism.common.capabilities.Capabilities.GAS_HANDLER_CAPABILITY,
            facing.getOpposite());
        if (gasHandler == null) return;

        for (mekanism.api.gas.GasTankInfo tank : gasHandler.getTankInfo()) {
            if (tank == null) continue;

            mekanism.api.gas.GasStack contents = tank.getGas();
            if (contents == null || contents.amount <= 0 || contents.getGas() == null) continue;

            addPreviewEntry(previewEntries, ResourceType.GAS, toGasDisplayStack(contents), contents.amount, limit);
        }
    }

    private static void collectEssentiaPreviewEntries(TileEntity targetTile,
                                                      List<ResourcePreviewEntry> previewEntries,
                                                      int limit) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return;

        collectEssentiaPreviewEntriesInternal(targetTile, previewEntries, limit);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    private static void collectEssentiaPreviewEntriesInternal(TileEntity targetTile,
                                                              List<ResourcePreviewEntry> previewEntries,
                                                              int limit) {
        if (!(targetTile instanceof thaumcraft.api.aspects.IAspectContainer)) return;

        thaumcraft.api.aspects.AspectList aspects = ((thaumcraft.api.aspects.IAspectContainer) targetTile).getAspects();
        if (aspects == null) return;

        for (thaumcraft.api.aspects.Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;

            int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;

            addPreviewEntry(
                previewEntries,
                ResourceType.ESSENTIA,
                toEssentiaDisplayStack(new thaumicenergistics.api.EssentiaStack(aspect, 1)),
                amount,
                limit);
        }
    }

    private static void addPreviewEntry(List<ResourcePreviewEntry> previewEntries,
                                        ResourceType resourceType,
                                        ItemStack displayStack,
                                        long amount,
                                        int limit) {
        ItemStack normalized = FilterHostUtil.normalizeFilter(displayStack);
        if (normalized.isEmpty() || amount <= 0) return;

        for (int index = 0; index < previewEntries.size(); index++) {
            ResourcePreviewEntry existing = previewEntries.get(index);
            if (existing.getResourceType() != resourceType) continue;
            if (!FilterHostUtil.matchesFilter(existing.getDisplayStack(), normalized)) continue;

            previewEntries.set(index, new ResourcePreviewEntry(
                resourceType,
                normalized,
                existing.getAmount() + amount));
            return;
        }

        if (limit > 0 && previewEntries.size() >= limit) return;

        previewEntries.add(new ResourcePreviewEntry(resourceType, normalized, amount));
    }

    @Nonnull
    private static ItemStack toFilterDisplayStack(ResourceType resourceType, @Nullable Object filter) {
        if (filter == null) return ItemStack.EMPTY;

        switch (resourceType) {
            case ITEM:
                if (filter instanceof IAEItemStack) {
                    return FilterHostUtil.normalizeFilter(((IAEItemStack) filter).createItemStack());
                }
                if (filter instanceof ItemStack) {
                    return FilterHostUtil.normalizeFilter((ItemStack) filter);
                }
                return ItemStack.EMPTY;
            case FLUID:
                if (filter instanceof IAEFluidStack) {
                    return FilterHostUtil.normalizeFilter(((IAEFluidStack) filter).asItemStackRepresentation());
                }
                if (filter instanceof FluidStack) {
                    return FilterHostUtil.normalizeFilter(toFluidDisplayStack((FluidStack) filter));
                }
                return ItemStack.EMPTY;
            case GAS:
                return FilterHostUtil.normalizeFilter(toGasDisplayStack(filter));
            case ESSENTIA:
                return FilterHostUtil.normalizeFilter(toEssentiaDisplayStack(filter));
            default:
                return ItemStack.EMPTY;
        }
    }

    @Nullable
    private static Object createFilterValue(ResourceType resourceType, ItemStack stack) {
        ItemStack normalized = FilterHostUtil.normalizeFilter(stack);
        if (normalized.isEmpty()) return null;

        switch (resourceType) {
            case ITEM:
                ItemStack itemFilter = QuickAddHelper.getItemFromItemStack(normalized);
                return itemFilter.isEmpty() ? null : AEItemStack.fromItemStack(itemFilter);
            case FLUID:
                return createFluidFilterValue(normalized);
            case GAS:
                return createGasFilterValue(normalized);
            case ESSENTIA:
                return createEssentiaFilterValue(normalized);
            default:
                return null;
        }
    }

    @Nullable
    private static Object createFluidFilterValue(ItemStack stack) {
        FluidStack fluid = null;
        if (stack.getItem() instanceof FluidDummyItem) {
            fluid = ((FluidDummyItem) stack.getItem()).getFluidStack(stack);
        }

        if (fluid == null) {
            fluid = QuickAddHelper.getFluidFromItemStack(stack);
        }

        if (fluid == null || fluid.getFluid() == null) return null;

        FluidStack normalizedFluid = fluid.copy();
        normalizedFluid.amount = Fluid.BUCKET_VOLUME;

        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        return fluidChannel.createStack(normalizedFluid);
    }

    @Nullable
    private static Object createGasFilterValue(ItemStack stack) {
        if (!MekanismEnergisticsIntegration.isModLoaded()) return null;

        return createGasFilterValueInternal(stack);
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static Object createGasFilterValueInternal(ItemStack stack) {
        mekanism.api.gas.GasStack gas = getGasFromFilterStack(stack);
        if (gas == null || gas.getGas() == null) return null;

        IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
            AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
        return gasChannel.createStack(new mekanism.api.gas.GasStack(gas.getGas(), 1));
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nullable
    private static mekanism.api.gas.GasStack getGasFromFilterStack(ItemStack stack) {
        if (stack.getItem() instanceof com.mekeng.github.common.item.ItemDummyGas) {
            return ((com.mekeng.github.common.item.ItemDummyGas) stack.getItem()).getGasStack(stack);
        }

        return QuickAddHelper.getGasFromItemStack(stack);
    }

    @Nullable
    private static Object createEssentiaFilterValue(ItemStack stack) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return null;

        return createEssentiaFilterValueInternal(stack);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nullable
    private static Object createEssentiaFilterValueInternal(ItemStack stack) {
        thaumicenergistics.api.EssentiaStack essentia = QuickAddHelper.getEssentiaFromItemStack(stack);
        if (essentia == null || essentia.getAspect() == null) return null;

        IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
            AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
        return essentiaChannel.createStack(new thaumicenergistics.api.EssentiaStack(essentia.getAspect(), 1));
    }

    @Nonnull
    private static ItemStack toFluidDisplayStack(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return ItemStack.EMPTY;

        FluidStack normalizedFluid = fluid.copy();
        normalizedFluid.amount = Fluid.BUCKET_VOLUME;

        IStorageChannel<IAEFluidStack> fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        IAEFluidStack aeFluid = fluidChannel.createStack(normalizedFluid);
        if (aeFluid == null) return ItemStack.EMPTY;

        return aeFluid.asItemStackRepresentation();
    }

    @Nonnull
    private static ItemStack toGasDisplayStack(@Nullable Object gas) {
        if (!MekanismEnergisticsIntegration.isModLoaded()) return ItemStack.EMPTY;

        return toGasDisplayStackInternal(gas);
    }

    @Optional.Method(modid = MEKENG_MODID)
    @Nonnull
    private static ItemStack toGasDisplayStackInternal(@Nullable Object gas) {
        if (gas instanceof com.mekeng.github.common.me.data.IAEGasStack) {
            return ((com.mekeng.github.common.me.data.IAEGasStack) gas).asItemStackRepresentation();
        }

        if (gas instanceof mekanism.api.gas.GasStack) {
            mekanism.api.gas.GasStack gasStack = (mekanism.api.gas.GasStack) gas;
            if (gasStack.getGas() == null) return ItemStack.EMPTY;

            IStorageChannel<com.mekeng.github.common.me.data.IAEGasStack> gasChannel =
                AEApi.instance().storage().getStorageChannel(com.mekeng.github.common.me.storage.IGasStorageChannel.class);
            com.mekeng.github.common.me.data.IAEGasStack aeGas = gasChannel.createStack(
                new mekanism.api.gas.GasStack(gasStack.getGas(), Math.max(1, gasStack.amount)));
            return aeGas != null ? aeGas.asItemStackRepresentation() : ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    @Nonnull
    private static ItemStack toEssentiaDisplayStack(@Nullable Object essentia) {
        if (!ThaumicEnergisticsIntegration.isModLoaded()) return ItemStack.EMPTY;

        return toEssentiaDisplayStackInternal(essentia);
    }

    @Optional.Method(modid = THAUMICENERGISTICS_MODID)
    @Nonnull
    private static ItemStack toEssentiaDisplayStackInternal(@Nullable Object essentia) {
        if (essentia instanceof thaumicenergistics.api.storage.IAEEssentiaStack) {
            return ((thaumicenergistics.api.storage.IAEEssentiaStack) essentia).asItemStackRepresentation();
        }

        if (essentia instanceof thaumicenergistics.api.EssentiaStack) {
            thaumicenergistics.api.EssentiaStack essentiaStack = (thaumicenergistics.api.EssentiaStack) essentia;
            if (essentiaStack.getAspect() == null) return ItemStack.EMPTY;

            IStorageChannel<thaumicenergistics.api.storage.IAEEssentiaStack> essentiaChannel =
                AEApi.instance().storage().getStorageChannel(thaumicenergistics.api.storage.IEssentiaStorageChannel.class);
            thaumicenergistics.api.storage.IAEEssentiaStack aeEssentia = essentiaChannel.createStack(essentiaStack);
            return aeEssentia != null ? aeEssentia.asItemStackRepresentation() : ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static final class LogicInterfaceHost implements IInterfaceHost {

        private final com.cells.blocks.interfacebase.IInterfaceHost owner;
        private final AbstractResourceInterfaceLogic logic;
        private final ResourceType resourceType;
        private final List<EnumFacing> targetFacings;
        private final boolean export;
        private final boolean directionalView;
        /** True for combined (Universal) interfaces: shows the resource type as a prefix. */
        private final boolean typeLabel;

        private LogicInterfaceHost(com.cells.blocks.interfacebase.IInterfaceHost owner,
                                   AbstractResourceInterfaceLogic logic,
                                   ResourceType resourceType,
                                   Collection<EnumFacing> targetFacings,
                                   boolean export,
                                   boolean directionalView,
                                   boolean typeLabel) {
            this.owner = owner;
            this.logic = logic;
            this.resourceType = resourceType;
            this.targetFacings = freezeTargetFacings(targetFacings);
            this.export = export;
            this.directionalView = directionalView;
            this.typeLabel = typeLabel;
        }

        @Nonnull
        @Override
        public ResourceType getResourceType() {
            return this.resourceType;
        }

        @Override
        public boolean isExport() {
            return this.export;
        }

        @Override
        public boolean isDirectionalView() {
            return this.directionalView;
        }

        @Override
        public boolean isTypeLabeled() {
            return this.typeLabel;
        }

        @Override
        public IItemHandler getUpgradeInventory() {
            return this.logic.getUpgradeInventory();
        }

        @Nonnull
        @Override
        public EnumFacing getPrimaryFacing() {
            return this.targetFacings.isEmpty() ? EnumFacing.NORTH : this.targetFacings.get(0);
        }

        @Nonnull
        @Override
        public Collection<EnumFacing> getTargetFacings() {
            return this.targetFacings;
        }

        @Override
        public int getFilterSlots() {
            return this.logic.getEffectiveFilterSlots();
        }

        @Nonnull
        @Override
        public ItemStack getFilter(int slot) {
            if (slot < 0 || slot >= getFilterSlots()) return ItemStack.EMPTY;

            return toFilterDisplayStack(this.resourceType, this.logic.getFilter(slot));
        }

        @Override
        public void setFilter(int slot, @Nonnull ItemStack stack) {
            if (slot < 0 || slot >= getFilterSlots()) return;

            if (stack.isEmpty()) {
                this.logic.setFilter(slot, null);
                return;
            }

            ItemStack normalized = FilterHostUtil.normalizeFilter(stack);
            if (normalized.isEmpty()) return;

            int slotCount = getFilterSlots();
            for (int index = 0; index < slotCount; index++) {
                if (index == slot) continue;
                if (!FilterHostUtil.matchesFilter(getFilter(index), normalized)) continue;

                return;
            }

            Object filterValue = createFilterValue(this.resourceType, normalized);
            if (filterValue == null) return;

            this.logic.setFilter(slot, filterValue);
        }

        @Override
        public void clearFilters() {
            this.logic.clearFilters();
        }

        @Nonnull
        @Override
        public List<ResourcePreviewEntry> getPreviewEntries(int limit) {
            return getPreviewEntries(getPrimaryFacing(), limit);
        }

        @Nonnull
        @Override
        public List<ResourcePreviewEntry> getPreviewEntries(@Nonnull EnumFacing facing, int limit) {
            if (facing == null || !this.targetFacings.contains(facing)) return Collections.emptyList();

            return collectAdjacentPreviewEntries(this.owner, this.resourceType, facing, limit);
        }
    }
}