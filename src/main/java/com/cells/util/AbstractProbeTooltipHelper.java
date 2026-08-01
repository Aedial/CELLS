package com.cells.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.core.settings.TickRates;
import appeng.util.ReadableNumberConverter;

import com.cells.api.IFilterHost;
import com.cells.api.IInterfaceHost;
import com.cells.api.IInterfaceProvider;
import com.cells.api.ResourceType;
import com.cells.blocks.compactingpatternexposer.TileCompactingPatternExposer;
import com.cells.blocks.interfacebase.managers.InterfaceTickScheduler;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;
import com.cells.items.ItemOverflowCard;
import com.cells.items.ItemTrashUnselectedCard;
import com.cells.items.pullpush.ContainerPullPushCard;
import com.cells.parts.subnetproxy.PartSubnetProxyBack;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


public abstract class AbstractProbeTooltipHelper<T> {

    public enum Scope {
        NONE("tooltip.cells.probe.scope.none"),
        ADJACENT("tooltip.cells.probe.scope.adjacent"),
        FACING("tooltip.cells.probe.scope.facing");

        private final String langKey;

        Scope(String langKey) {
            this.langKey = langKey;
        }

        public String getLangKey() {
            return this.langKey;
        }
    }

    protected final boolean appendLines(@Nullable Object target, Consumer<T> sink) {
        if (sink == null) return false;

        if (target instanceof IInterfaceProvider) {
            appendInterfaceLines((IInterfaceProvider) target, sink);
            return true;
        }

        if (target instanceof TileCompactingPatternExposer) {
            appendCompactingPatternExposerLines((TileCompactingPatternExposer) target, sink);
            return true;
        }

        if (target instanceof PartSubnetProxyFront) {
            appendSubnetProxyLines((PartSubnetProxyFront) target, sink);
            return true;
        }

        if (target instanceof PartSubnetProxyBack) {
            appendSubnetProxyLines((PartSubnetProxyBack) target, sink);
            return true;
        }

        return false;
    }

    protected final void appendInterfaceLines(@Nullable IInterfaceProvider provider, Consumer<T> sink) {
        if (provider == null || sink == null) return;

        List<IInterfaceHost> interfaceHosts = provider.getInterfaceHosts();
        if (interfaceHosts == null || interfaceHosts.isEmpty()) return;

        List<IInterfaceHost> uniqueUpgradeHosts = new ArrayList<>();
        Map<IItemHandler, Boolean> seenUpgradeInventories = new IdentityHashMap<>();

        appendSeparator(sink);
        appendInterfaceNetworkLines(interfaceHosts, sink);
        appendInterfaceFilterSummaryLines(interfaceHosts, sink);

        for (IInterfaceHost interfaceHost : interfaceHosts) {
            if (interfaceHost == null) continue;

            addUniqueInterfaceUpgradeHost(uniqueUpgradeHosts, seenUpgradeInventories, interfaceHost);
        }

        appendInterfaceBehaviorSummaryLines(uniqueUpgradeHosts, sink);
        appendInterfaceCardSummaryLines(interfaceHosts, sink);
    }

    protected abstract void appendSeparator(Consumer<T> sink);

    @Nonnull
    protected abstract T renderLine(LocalizedTooltipText line);

    @Nonnull
    protected LocalizedTooltipText literal(String text) {
        return LocalizedTooltipText.literal(text);
    }

    @Nonnull
    protected LocalizedTooltipText translate(String key) {
        return LocalizedTooltipText.translated(key);
    }

    @Nonnull
    protected LocalizedTooltipText translate(String key, LocalizedTooltipText... arguments) {
        return LocalizedTooltipText.translated(key, arguments);
    }

    private void appendCompactingPatternExposerLines(TileCompactingPatternExposer tile, Consumer<T> sink) {
        appendSeparator(sink);

        acceptLine(
            sink,
            buildFilterCountSummaryLine(
                tile.getConfiguredFilterCount(),
                TileCompactingPatternExposer.FILTER_SLOTS));
        acceptLine(
            sink,
            translate(
                "tooltip.cells.probe.compacting.summary",
                literal(Integer.toString(tile.getExposedPatternCount())),
                literal(Integer.toString(tile.getCustomMultiplierCount()))));
    }

    private void appendSubnetProxyLines(PartSubnetProxyFront front, Consumer<T> sink) {
        appendSeparator(sink);

        if (front.findBackPart() == null) acceptLine(sink, translate("tooltip.cells.probe.subnet_proxy.unpaired_front"));

        appendSubnetProxyStateLines(front, sink);
    }

    private void appendSubnetProxyLines(PartSubnetProxyBack back, Consumer<T> sink) {
        appendSeparator(sink);

        PartSubnetProxyFront front = back.findFrontPart();
        if (front == null) {
            acceptLine(sink, translate("tooltip.cells.probe.subnet_proxy.unpaired_back"));
            return;
        }

        appendSubnetProxyStateLines(front, sink);
    }

    private void appendSubnetProxyStateLines(PartSubnetProxyFront front, Consumer<T> sink) {
        acceptLine(sink, buildSubnetProxyChannelsLine(front));
        acceptLine(sink, buildFilterCountSummaryLine(countConfiguredFilterSlots(front), front.getFilterSlots()));
        acceptLine(sink, buildSubnetProxyBehaviorLine(front));
        acceptLine(sink, buildSubnetProxyMatchingLine(front));
    }

    private void appendInterfaceNetworkLines(List<IInterfaceHost> interfaceHosts, Consumer<T> sink) {
        boolean[] merged = new boolean[interfaceHosts.size()];

        for (int index = 0; index < interfaceHosts.size(); index++) {
            if (merged[index]) continue;

            IInterfaceHost interfaceHost = interfaceHosts.get(index);
            if (interfaceHost == null) continue;

            int pairedIndex = findSharedInterfaceNetworkPairIndex(interfaceHosts, index, merged);
            if (pairedIndex >= 0) {
                merged[pairedIndex] = true;
                acceptLine(
                    sink,
                    buildSharedInterfaceNetworkLine(interfaceHost, interfaceHosts.get(pairedIndex)));
                continue;
            }

            acceptLine(sink, buildInterfaceNetworkLine(interfaceHost));
        }
    }

    private void appendInterfaceFilterSummaryLines(List<IInterfaceHost> interfaceHosts, Consumer<T> sink) {
        for (IInterfaceHost interfaceHost : interfaceHosts) {
            if (interfaceHost == null) continue;

            acceptLine(sink, buildInterfaceFilterCountLine(interfaceHost));
        }
    }

    private void addUniqueInterfaceUpgradeHost(List<IInterfaceHost> uniqueUpgradeHosts,
                                               Map<IItemHandler, Boolean> seenUpgradeInventories,
                                               IInterfaceHost interfaceHost) {
        IItemHandler upgradeInventory = interfaceHost.getUpgradeInventory();
        if (upgradeInventory == null) return;
        if (seenUpgradeInventories.containsKey(upgradeInventory)) return;

        seenUpgradeInventories.put(upgradeInventory, Boolean.TRUE);
        uniqueUpgradeHosts.add(interfaceHost);
    }

    private void appendInterfaceCardSummaryLines(List<IInterfaceHost> interfaceHosts, Consumer<T> sink) {
        List<InterfaceCardLine> cardLines = new ArrayList<>();

        for (IInterfaceHost interfaceHost : interfaceHosts) {
            if (interfaceHost == null) continue;

            ItemStack interfaceCardStack = findInterfaceAutoTransferCard(interfaceHost);
            if (interfaceCardStack.isEmpty()) continue;

            Collection<EnumFacing> cardTargetFacings = getInterfaceCardTargetFacings(interfaceHost);
            cardLines.add(new InterfaceCardLine(interfaceHost, cardTargetFacings));

            acceptLine(sink, buildInterfaceCardLine(interfaceHost, interfaceCardStack, cardTargetFacings));
        }

        appendInterfaceCardSideLines(cardLines, sink);
    }

    private void appendInterfaceCardSideLines(List<InterfaceCardLine> cardLines, Consumer<T> sink) {
        boolean[] merged = new boolean[cardLines.size()];

        for (int index = 0; index < cardLines.size(); index++) {
            if (merged[index]) continue;

            InterfaceCardLine cardLine = cardLines.get(index);
            List<IInterfaceHost> groupedHosts = new ArrayList<>();
            groupedHosts.add(cardLine.interfaceHost);

            for (int pairedIndex = index + 1; pairedIndex < cardLines.size(); pairedIndex++) {
                if (merged[pairedIndex]) continue;
                if (!shouldMergeInterfaceCardSideLines(cardLine, cardLines.get(pairedIndex))) continue;

                merged[pairedIndex] = true;
                groupedHosts.add(cardLines.get(pairedIndex).interfaceHost);
            }

            acceptLine(sink, buildInterfaceCardSideLine(groupedHosts, cardLine.targetFacings));
        }
    }

    private boolean shouldMergeInterfaceCardSideLines(InterfaceCardLine firstCardLine,
                                                      InterfaceCardLine secondCardLine) {
        if (firstCardLine.interfaceHost.getResourceType() != secondCardLine.interfaceHost.getResourceType()) {
            return false;
        }

        if (firstCardLine.interfaceHost.isExport() == secondCardLine.interfaceHost.isExport()) return false;

        return haveSameTargetFacings(firstCardLine.targetFacings, secondCardLine.targetFacings);
    }

    private void appendInterfaceBehaviorSummaryLines(List<IInterfaceHost> interfaceHosts, Consumer<T> sink) {
        for (IInterfaceHost interfaceHost : interfaceHosts) {
            if (interfaceHost == null) continue;

            acceptLine(sink, buildInterfaceBehaviorLine(interfaceHost));
        }
    }

    private void acceptLine(Consumer<T> sink, @Nullable LocalizedTooltipText line) {
        if (line == null || line.isEmpty()) return;

        sink.accept(renderLine(line));
    }

    private int findSharedInterfaceNetworkPairIndex(List<IInterfaceHost> interfaceHosts, int index, boolean[] merged) {
        for (int pairedIndex = index + 1; pairedIndex < interfaceHosts.size(); pairedIndex++) {
            if (merged[pairedIndex]) continue;
            if (shouldMergeSharedInterfaceNetworkLines(interfaceHosts.get(index), interfaceHosts.get(pairedIndex))) {
                return pairedIndex;
            }
        }

        return -1;
    }

    // IO interface API views are directional, but the host exposes a single shared AE2 polling control.
    @Nonnull
    private LocalizedTooltipText buildInterfaceNetworkLine(IInterfaceHost interfaceHost) {
        PollingSpan pollingSpan = getInterfacePollingSpan(interfaceHost);
        String key = "tooltip.cells.probe.network." + (interfaceHost.isExport() ? "export" : "import");
        LocalizedTooltipText resourceType = getInterfaceResourceTypeText(interfaceHost);
        LocalizedTooltipText quantityText = buildSingleTransferQuantityText(
            getInterfaceTooltipTransferQuantity(interfaceHost));

        if (pollingSpan.isAdaptive()) {
            return translate(
                key + ".adaptive",
                resourceType,
                literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
                literal(PollingRateUtils.format(pollingSpan.getMaxTicks())),
                quantityText);
        }

        return translate(
            key + ".fixed",
            resourceType,
            literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
            quantityText);
    }

    @Nonnull
    private LocalizedTooltipText buildSharedInterfaceNetworkLine(IInterfaceHost firstInterfaceHost,
                                                                 IInterfaceHost secondInterfaceHost) {
        PollingSpan pollingSpan = getInterfacePollingSpan(firstInterfaceHost);
        long firstQuantity = getInterfaceTooltipTransferQuantity(firstInterfaceHost);
        long secondQuantity = getInterfaceTooltipTransferQuantity(secondInterfaceHost);
        long importQuantity = firstInterfaceHost.isExport() ? secondQuantity : firstQuantity;
        long exportQuantity = firstInterfaceHost.isExport() ? firstQuantity : secondQuantity;
        LocalizedTooltipText resourceType = getInterfaceResourceTypeText(firstInterfaceHost);
        LocalizedTooltipText quantityText = buildSharedTransferQuantityText(importQuantity, exportQuantity);

        if (pollingSpan.isAdaptive()) {
            return translate(
                "tooltip.cells.probe.network.io.adaptive",
                resourceType,
                literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
                literal(PollingRateUtils.format(pollingSpan.getMaxTicks())),
                quantityText);
        }

        return translate(
            "tooltip.cells.probe.network.io.fixed",
            resourceType,
            literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
            quantityText);
    }

    private boolean shouldMergeSharedInterfaceNetworkLines(@Nullable IInterfaceHost firstInterfaceHost,
                                                           @Nullable IInterfaceHost secondInterfaceHost) {
        if (firstInterfaceHost == null || secondInterfaceHost == null) return false;
        if (!firstInterfaceHost.isDirectionalView() || !secondInterfaceHost.isDirectionalView()) return false;
        if (firstInterfaceHost.isExport() == secondInterfaceHost.isExport()) return false;
        if (firstInterfaceHost.getResourceType() != secondInterfaceHost.getResourceType()) return false;
        if (!haveSamePollingSpan(
            getInterfacePollingSpan(firstInterfaceHost),
            getInterfacePollingSpan(secondInterfaceHost))) return false;

        return haveSameTargetFacings(
            firstInterfaceHost.getTargetFacings(),
            secondInterfaceHost.getTargetFacings());
    }

    private boolean haveSameTargetFacings(@Nullable Collection<?> firstFacings,
                                          @Nullable Collection<?> secondFacings) {
        if (firstFacings == secondFacings) return true;
        if (firstFacings == null || secondFacings == null) return false;
        if (firstFacings.size() != secondFacings.size()) return false;

        return firstFacings.containsAll(secondFacings) && secondFacings.containsAll(firstFacings);
    }

    @Nonnull
    private PollingSpan getInterfacePollingSpan(IInterfaceHost interfaceHost) {
        int pollingRate = getInterfaceTooltipPollingRate(interfaceHost);
        if (pollingRate > 0) return PollingSpan.fixed(pollingRate);

        // Interfaces with Pull or Push card throttle adaptive AE2 network I/O to a fixed minimum interval.
        if (hasInterfaceAutoTransferCard(interfaceHost)) {
            return PollingSpan.fixed(InterfaceTickScheduler.getAdaptiveCardNetworkIOMinInterval());
        }

        return getAdaptivePollingSpan();
    }

    private int getInterfaceTooltipPollingRate(IInterfaceHost interfaceHost) {
        if (!(interfaceHost instanceof IInterfaceTooltipView)) return 0;

        return ((IInterfaceTooltipView) interfaceHost).getTooltipPollingRate();
    }

    private long getInterfaceTooltipTransferQuantity(IInterfaceHost interfaceHost) {
        if (!(interfaceHost instanceof IInterfaceTooltipView)) return 0;

        return ((IInterfaceTooltipView) interfaceHost).getTooltipTransferQuantity();
    }

    @Nonnull
    private PollingSpan getAdaptivePollingSpan() {
        return PollingSpan.adaptive(TickRates.Interface.getMin(), TickRates.Interface.getMax());
    }

    private boolean haveSamePollingSpan(PollingSpan firstSpan, PollingSpan secondSpan) {
        return firstSpan.isAdaptive() == secondSpan.isAdaptive()
            && firstSpan.getMinTicks() == secondSpan.getMinTicks()
            && firstSpan.getMaxTicks() == secondSpan.getMaxTicks();
    }

    private boolean hasInterfaceAutoTransferCard(IInterfaceHost interfaceHost) {
        return !findInterfaceAutoTransferCard(interfaceHost).isEmpty();
    }

    @Nonnull
    private LocalizedTooltipText buildInterfaceFilterCountLine(IInterfaceHost interfaceHost) {
        return translate(
            interfaceHost.isExport()
                ? "tooltip.cells.probe.filters.export"
                : "tooltip.cells.probe.filters.import",
            getInterfaceResourceTypeText(interfaceHost),
            literal(Integer.toString(Math.max(countConfiguredFilterSlots(interfaceHost), 0))),
            literal(Integer.toString(Math.max(interfaceHost.getFilterSlots(), 0))));
    }

    @Nonnull
    private LocalizedTooltipText buildFilterCountSummaryLine(int configuredFilters, int totalFilters) {
        return translate(
            "tooltip.cells.probe.filters",
            literal(Integer.toString(Math.max(configuredFilters, 0))),
            literal(Integer.toString(Math.max(totalFilters, 0))));
    }

    @Nullable
    private LocalizedTooltipText buildInterfaceBehaviorLine(IInterfaceHost interfaceHost) {
        List<LocalizedTooltipText> behaviors = new ArrayList<>();

        if (hasInterfaceUpgradeCard(interfaceHost, ItemOverflowCard.class)) {
            behaviors.add(translate("tooltip.cells.probe.interface.behavior.overflow"));
        }

        if (hasInterfaceUpgradeCard(interfaceHost, ItemTrashUnselectedCard.class)) {
            behaviors.add(translate("tooltip.cells.probe.interface.behavior.trash_unselected"));
        }

        if (behaviors.isEmpty()) return null;

        return translate(
            interfaceHost.isExport()
                ? "tooltip.cells.probe.interface.behavior.export"
                : "tooltip.cells.probe.interface.behavior.import",
            joinLocalizedTexts(behaviors, translate("tooltip.cells.probe.state.none")));
    }

    @Nonnull
    private LocalizedTooltipText buildSubnetProxyChannelsLine(PartSubnetProxyFront front) {
        List<LocalizedTooltipText> enabledChannels = new ArrayList<>();

        for (com.cells.network.sync.ResourceType type : com.cells.network.sync.ResourceType.getAvailableTypes()) {
            if (!front.isChannelEnabled(type)) continue;

            enabledChannels.add(translate(getResourceTypeLangKey(toApiResourceType(type))));
        }

        return translate(
            "tooltip.cells.probe.subnet_proxy.channels",
            joinLocalizedTexts(enabledChannels, translate("tooltip.cells.probe.state.none")));
    }

    @Nonnull
    private LocalizedTooltipText buildSubnetProxyBehaviorLine(PartSubnetProxyFront front) {
        return translate(
            "tooltip.cells.probe.subnet_proxy.behavior",
            literal(Integer.toString(front.getPriority())),
            translate(front.hasInsertionCard()
                ? "tooltip.cells.probe.state.installed"
                : "tooltip.cells.probe.state.not_installed"));
    }

    @Nonnull
    private LocalizedTooltipText buildSubnetProxyMatchingLine(PartSubnetProxyFront front) {
        List<LocalizedTooltipText> matchingModes = new ArrayList<>();

        matchingModes.add(
            translate(
                front.getInstalledUpgrades(Upgrades.FUZZY) > 0
                    ? "tooltip.cells.probe.matching.fuzzy"
                    : "tooltip.cells.probe.matching.exact"));
        matchingModes.add(
            translate(
                front.getInstalledUpgrades(Upgrades.INVERTER) > 0
                    ? "tooltip.cells.probe.matching.blacklist"
                    : "tooltip.cells.probe.matching.whitelist"));

        return translate(
            "tooltip.cells.probe.subnet_proxy.matching",
            joinLocalizedTexts(matchingModes, translate("tooltip.cells.probe.state.none")));
    }

    @Nonnull
    private LocalizedTooltipText buildInterfaceCardLine(IInterfaceHost interfaceHost,
                                                        ItemStack cardStack,
                                                        Collection<EnumFacing> targetFacings) {
        int interval = getCardInterval(cardStack);
        if (interval <= 0) interval = ContainerPullPushCard.DEFAULT_INTERVAL;

        int quantity = getCardQuantity(cardStack);
        if (quantity <= 0) quantity = ContainerPullPushCard.MINIMUM_QUANTITY;

        String key = "tooltip.cells.probe.card." + (interfaceHost.isExport() ? "push" : "pull");
        return translate(
            key,
            getInterfaceResourceTypeText(interfaceHost),
            literal(PollingRateUtils.format(interval)),
            buildSingleTransferQuantityText(quantity));
    }

    @Nonnull
    private LocalizedTooltipText buildInterfaceCardSideLine(List<IInterfaceHost> interfaceHosts,
                                                            @Nullable Collection<EnumFacing> targetFacings) {
        IInterfaceHost interfaceHost = interfaceHosts.get(0);

        return translate(
            getInterfaceCardSideKey(interfaceHosts),
            getInterfaceResourceTypeText(interfaceHost),
            buildInterfaceCardIoSideList(targetFacings));
    }

    @Nonnull
    private LocalizedTooltipText buildSingleTransferQuantityText(long quantity) {
        return translate(
            "tooltip.cells.probe.quantity.single",
            literal(formatTransferQuantity(quantity)));
    }

    @Nonnull
    private LocalizedTooltipText buildSharedTransferQuantityText(long importQuantity, long exportQuantity) {
        return translate(
            "tooltip.cells.probe.quantity.io",
            literal(formatTransferQuantity(importQuantity)),
            literal(formatTransferQuantity(exportQuantity)));
    }

    @Nonnull
    private String formatTransferQuantity(long quantity) {
        return ReadableNumberConverter.INSTANCE.toWideReadableForm(Math.max(quantity, 0));
    }

    @Nonnull
    private String getInterfaceCardSideKey(List<IInterfaceHost> interfaceHosts) {
        boolean hasPull = false;
        boolean hasPush = false;

        for (IInterfaceHost interfaceHost : interfaceHosts) {
            if (interfaceHost == null) continue;

            if (interfaceHost.isExport()) {
                hasPush = true;
            } else {
                hasPull = true;
            }
        }

        if (hasPull && hasPush) return "tooltip.cells.probe.card.sides.io";

        return hasPush ? "tooltip.cells.probe.card.sides.push" : "tooltip.cells.probe.card.sides.pull";
    }

    @Nonnull
    private LocalizedTooltipText getInterfaceResourceTypeText(IInterfaceHost interfaceHost) {
        return translate(getTooltipResourceTypeLangKey(interfaceHost.getResourceType()));
    }

    @Nonnull
    private Collection<EnumFacing> getInterfaceCardTargetFacings(IInterfaceHost interfaceHost) {
        if (interfaceHost instanceof IInterfaceTooltipView) {
            return ((IInterfaceTooltipView) interfaceHost).getTooltipAutoTransferFacings();
        }

        Collection<EnumFacing> targetFacings = interfaceHost.getTargetFacings();
        return targetFacings != null ? targetFacings : Collections.emptyList();
    }

    @Nonnull
    public static String getResourceTypeLangKey(@Nullable ResourceType type) {
        if (type == null) return "cells.type.unknown";

        switch (type) {
            case ITEM:
                return "cells.type.item";
            case FLUID:
                return "cells.type.fluid";
            case GAS:
                return "cells.type.gas";
            case ESSENTIA:
                return "cells.type.essentia";
            default:
                return "cells.type.unknown";
        }
    }

    @Nonnull
    private static String getTooltipResourceTypeLangKey(@Nullable ResourceType type) {
        if (type == null) return "cells.unit_name.unknown";

        switch (type) {
            case ITEM:
                return "cells.unit_name.item";
            case FLUID:
                return "cells.unit_name.fluid";
            case GAS:
                return "cells.unit_name.gas";
            case ESSENTIA:
                return "cells.unit_name.essentia";
            default:
                return "cells.unit_name.unknown";
        }
    }

    @Nonnull
    private Scope getInterfaceCardTransferScope(@Nullable Collection<?> targetFacings) {
        if (targetFacings == null || targetFacings.isEmpty()) return Scope.NONE;
        if (targetFacings != null && targetFacings.size() == 1) return Scope.FACING;

        return Scope.ADJACENT;
    }

    @Nonnull
    private ItemStack findInterfaceAutoTransferCard(IInterfaceHost interfaceHost) {
        IItemHandler upgradeInventory = interfaceHost.getUpgradeInventory();
        if (upgradeInventory == null) return ItemStack.EMPTY;

        ItemStack fallback = ItemStack.EMPTY;
        for (int slot = 0; slot < upgradeInventory.getSlots(); slot++) {
            ItemStack stack = upgradeInventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            boolean isPush = stack.getItem() instanceof ItemAutoPushCard;
            boolean isPull = stack.getItem() instanceof ItemAutoPullCard;

            if (interfaceHost.isExport() && isPush) return stack;
            if (!interfaceHost.isExport() && isPull) return stack;

            if (fallback.isEmpty() && (isPush || isPull)) fallback = stack;
        }

        return fallback;
    }

    private boolean hasInterfaceUpgradeCard(IInterfaceHost interfaceHost, Class<?> cardType) {
        IItemHandler upgradeInventory = interfaceHost.getUpgradeInventory();
        if (upgradeInventory == null) return false;

        for (int slot = 0; slot < upgradeInventory.getSlots(); slot++) {
            ItemStack stack = upgradeInventory.getStackInSlot(slot);
            if (!stack.isEmpty() && cardType.isInstance(stack.getItem())) return true;
        }

        return false;
    }

    private int countConfiguredFilterSlots(IFilterHost filterHost) {
        int configuredFilters = 0;
        int totalFilters = Math.max(filterHost.getFilterSlots(), 0);

        for (int slot = 0; slot < totalFilters; slot++) {
            if (!filterHost.getFilter(slot).isEmpty()) configuredFilters++;
        }

        return configuredFilters;
    }

    @Nonnull
    private LocalizedTooltipText buildInterfaceCardIoSideList(@Nullable Collection<EnumFacing> targetFacings) {
        List<LocalizedTooltipText> sideTexts = new ArrayList<>();

        for (EnumFacing facing : getOrderedFacings(targetFacings)) {
            sideTexts.add(translate("tooltip.cells.probe.side." + facing.getName()));
        }

        return joinLocalizedTexts(sideTexts, translate("tooltip.cells.probe.state.none"));
    }

    @Nonnull
    private List<EnumFacing> getOrderedFacings(@Nullable Collection<EnumFacing> targetFacings) {
        List<EnumFacing> orderedFacings = new ArrayList<>();
        if (targetFacings == null || targetFacings.isEmpty()) return orderedFacings;

        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.NORTH);
        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.SOUTH);
        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.WEST);
        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.EAST);
        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.UP);
        appendFacingIfPresent(orderedFacings, targetFacings, EnumFacing.DOWN);

        for (EnumFacing facing : targetFacings) {
            if (facing != null && !orderedFacings.contains(facing)) orderedFacings.add(facing);
        }

        return orderedFacings;
    }

    private void appendFacingIfPresent(List<EnumFacing> orderedFacings,
                                       Collection<EnumFacing> targetFacings,
                                       EnumFacing facing) {
        if (targetFacings.contains(facing)) orderedFacings.add(facing);
    }

    @Nonnull
    private LocalizedTooltipText joinLocalizedTexts(List<LocalizedTooltipText> values, LocalizedTooltipText emptyValue) {
        if (values.isEmpty()) return emptyValue;

        LocalizedTooltipText joined = values.get(0);
        for (int index = 1; index < values.size(); index++) {
            joined = translate("tooltip.cells.probe.list_separator", joined, values.get(index));
        }

        return joined;
    }

    @Nonnull
    private ResourceType toApiResourceType(@Nullable com.cells.network.sync.ResourceType type) {
        if (type == null) return ResourceType.ITEM;

        switch (type) {
            case ITEM:
                return ResourceType.ITEM;
            case FLUID:
                return ResourceType.FLUID;
            case GAS:
                return ResourceType.GAS;
            case ESSENTIA:
                return ResourceType.ESSENTIA;
            default:
                return ResourceType.ITEM;
        }
    }

    private int getCardInterval(ItemStack stack) {
        if (stack.isEmpty()) return ContainerPullPushCard.DEFAULT_INTERVAL;

        if (stack.getItem() instanceof ItemAutoPullCard) {
            return ItemAutoPullCard.getInterval(stack);
        }

        if (stack.getItem() instanceof ItemAutoPushCard) {
            return ItemAutoPushCard.getInterval(stack);
        }

        return ContainerPullPushCard.DEFAULT_INTERVAL;
    }

    private int getCardQuantity(ItemStack stack) {
        if (stack.isEmpty()) return ContainerPullPushCard.MINIMUM_QUANTITY;

        if (stack.getItem() instanceof ItemAutoPullCard) {
            return ItemAutoPullCard.getQuantity(stack);
        }

        if (stack.getItem() instanceof ItemAutoPushCard) {
            return ItemAutoPushCard.getQuantity(stack);
        }

        return ContainerPullPushCard.MINIMUM_QUANTITY;
    }

    private static final class InterfaceCardLine {

        private final IInterfaceHost interfaceHost;
        private final List<EnumFacing> targetFacings;

        private InterfaceCardLine(IInterfaceHost interfaceHost, @Nullable Collection<EnumFacing> targetFacings) {
            this.interfaceHost = interfaceHost;
            this.targetFacings = freezeTargetFacings(targetFacings);
        }

        @Nonnull
        private static List<EnumFacing> freezeTargetFacings(@Nullable Collection<EnumFacing> targetFacings) {
            List<EnumFacing> frozenFacings = new ArrayList<>();
            if (targetFacings == null) return frozenFacings;

            for (EnumFacing facing : targetFacings) {
                if (facing != null && !frozenFacings.contains(facing)) frozenFacings.add(facing);
            }

            return Collections.unmodifiableList(frozenFacings);
        }
    }

    private static final class PollingSpan {

        private final boolean adaptive;
        private final int minTicks;
        private final int maxTicks;

        private PollingSpan(boolean adaptive, int minTicks, int maxTicks) {
            this.adaptive = adaptive;
            this.minTicks = minTicks;
            this.maxTicks = maxTicks;
        }

        @Nonnull
        public static PollingSpan adaptive(int minTicks, int maxTicks) {
            return new PollingSpan(true, minTicks, maxTicks);
        }

        @Nonnull
        public static PollingSpan fixed(int ticks) {
            return new PollingSpan(false, ticks, ticks);
        }

        public boolean isAdaptive() {
            return this.adaptive;
        }

        public int getMinTicks() {
            return this.minTicks;
        }

        public int getMaxTicks() {
            return this.maxTicks;
        }
    }
}