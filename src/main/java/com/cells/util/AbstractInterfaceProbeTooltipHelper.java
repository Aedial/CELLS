package com.cells.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import net.minecraftforge.items.IItemHandler;

import appeng.core.settings.TickRates;
import appeng.util.ReadableNumberConverter;

import com.cells.api.IInterfaceHost;
import com.cells.api.IInterfaceProvider;
import com.cells.api.ResourceType;
import com.cells.blocks.interfacebase.managers.InterfaceTickScheduler;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;
import com.cells.items.pullpush.ContainerPullPushCard;


public abstract class AbstractInterfaceProbeTooltipHelper<T> {

    public enum Scope {
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
        if (!(target instanceof IInterfaceProvider) || sink == null) return false;

        appendLines((IInterfaceProvider) target, sink);
        return true;
    }

    protected final void appendLines(@Nullable IInterfaceProvider provider, Consumer<T> sink) {
        if (provider == null || sink == null) return;

        List<IInterfaceHost> views = provider.getInterfaceHosts();
        if (views == null || views.isEmpty()) return;

        List<IInterfaceHost> sharedCardViews = new ArrayList<>();
        Map<IItemHandler, Boolean> seenCardInventories = new IdentityHashMap<>();

        appendSeparator(sink);
        appendNetworkLines(views, sink);

        for (IInterfaceHost view : views) {
            if (view == null) continue;

            addUniqueCardView(sharedCardViews, seenCardInventories, view);
        }

        appendCardLines(sharedCardViews, sink);
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

    private void appendNetworkLines(List<IInterfaceHost> views, Consumer<T> sink) {
        boolean[] merged = new boolean[views.size()];

        for (int index = 0; index < views.size(); index++) {
            if (merged[index]) continue;

            IInterfaceHost view = views.get(index);
            if (view == null) continue;

            int pairedIndex = findSharedNetworkPairIndex(views, index, merged);
            if (pairedIndex >= 0) {
                merged[pairedIndex] = true;
                acceptLine(sink, buildSharedNetworkLine(view, views.get(pairedIndex)));
                continue;
            }

            acceptLine(sink, buildNetworkLine(view));
        }
    }

    private void addUniqueCardView(List<IInterfaceHost> sharedCardViews,
                                   Map<IItemHandler, Boolean> seenCardInventories,
                                   IInterfaceHost view) {
        IItemHandler upgradeInventory = view.getUpgradeInventory();
        if (upgradeInventory == null) return;
        if (seenCardInventories.containsKey(upgradeInventory)) return;

        seenCardInventories.put(upgradeInventory, Boolean.TRUE);
        sharedCardViews.add(view);
    }

    private void appendCardLines(List<IInterfaceHost> sharedCardViews, Consumer<T> sink) {
        for (IInterfaceHost view : sharedCardViews) {
            if (view == null) continue;

            ItemStack cardStack = findAutoTransferCard(view);
            if (cardStack.isEmpty()) continue;

            acceptLine(sink, buildCardLine(view, cardStack, shouldTypeLabelCardLine(sharedCardViews, view)));
        }
    }

    private void acceptLine(Consumer<T> sink, @Nullable LocalizedTooltipText line) {
        if (line == null || line.isEmpty()) return;

        sink.accept(renderLine(line));
    }

    private int findSharedNetworkPairIndex(List<IInterfaceHost> views, int index, boolean[] merged) {
        for (int pairedIndex = index + 1; pairedIndex < views.size(); pairedIndex++) {
            if (merged[pairedIndex]) continue;
            if (shouldMergeSharedNetworkLines(views.get(index), views.get(pairedIndex))) return pairedIndex;
        }

        return -1;
    }

    // IO interface API views are directional, but the host exposes a single shared AE2 polling control.
    @Nonnull
    private LocalizedTooltipText buildNetworkLine(IInterfaceHost view) {
        PollingSpan pollingSpan = getPollingSpan(view);
        String key = "tooltip.cells.probe.network." + (view.isExport() ? "export" : "import");
        LocalizedTooltipText quantityText = buildSingleTransferQuantityText(getTooltipTransferQuantity(view));

        if (pollingSpan.isAdaptive()) {
            return prefixTypeLabel(
                view,
                translate(
                    key + ".adaptive",
                    literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
                    literal(PollingRateUtils.format(pollingSpan.getMaxTicks())),
                    quantityText));
        }

        return prefixTypeLabel(
            view,
            translate(
                key + ".fixed",
                literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
                quantityText));
    }

    @Nonnull
    private LocalizedTooltipText buildSharedNetworkLine(IInterfaceHost firstView, IInterfaceHost secondView) {
        PollingSpan pollingSpan = getPollingSpan(firstView);
        long firstQuantity = getTooltipTransferQuantity(firstView);
        long secondQuantity = getTooltipTransferQuantity(secondView);
        long importQuantity = firstView.isExport() ? secondQuantity : firstQuantity;
        long exportQuantity = firstView.isExport() ? firstQuantity : secondQuantity;
        LocalizedTooltipText quantityText = buildSharedTransferQuantityText(importQuantity, exportQuantity);

        if (pollingSpan.isAdaptive()) {
            return translate(
                "tooltip.cells.probe.network.io.adaptive",
                literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
                literal(PollingRateUtils.format(pollingSpan.getMaxTicks())),
                quantityText);
        }

        return translate(
            "tooltip.cells.probe.network.io.fixed",
            literal(PollingRateUtils.format(pollingSpan.getMinTicks())),
            quantityText);
    }

    private boolean shouldMergeSharedNetworkLines(@Nullable IInterfaceHost firstView,
                                                  @Nullable IInterfaceHost secondView) {
        if (firstView == null || secondView == null) return false;
        if (!firstView.isDirectionalView() || !secondView.isDirectionalView()) return false;
        if (firstView.isTypeLabeled() || secondView.isTypeLabeled()) return false;
        if (firstView.isExport() == secondView.isExport()) return false;
        if (firstView.getResourceType() != secondView.getResourceType()) return false;
        if (!haveSamePollingSpan(getPollingSpan(firstView), getPollingSpan(secondView))) return false;

        return haveSameTargetFacings(firstView.getTargetFacings(), secondView.getTargetFacings());
    }

    private boolean haveSameTargetFacings(@Nullable Collection<?> firstFacings,
                                          @Nullable Collection<?> secondFacings) {
        if (firstFacings == secondFacings) return true;
        if (firstFacings == null || secondFacings == null) return false;
        if (firstFacings.size() != secondFacings.size()) return false;

        return firstFacings.containsAll(secondFacings) && secondFacings.containsAll(firstFacings);
    }

    @Nonnull
    private PollingSpan getPollingSpan(IInterfaceHost view) {
        int pollingRate = getTooltipPollingRate(view);
        if (pollingRate > 0) return PollingSpan.fixed(pollingRate);

        // Interfaces with Pull or Push card throttle adaptive AE2 network I/O to a fixed minimum interval.
        if (hasAutoTransferCard(view)) {
            return PollingSpan.fixed(InterfaceTickScheduler.getAdaptiveCardNetworkIOMinInterval());
        }

        return getAdaptivePollingSpan();
    }

    private int getTooltipPollingRate(IInterfaceHost view) {
        if (!(view instanceof IInterfaceTooltipView)) return 0;

        return ((IInterfaceTooltipView) view).getTooltipPollingRate();
    }

    private long getTooltipTransferQuantity(IInterfaceHost view) {
        if (!(view instanceof IInterfaceTooltipView)) return 0;

        return ((IInterfaceTooltipView) view).getTooltipTransferQuantity();
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

    private boolean hasAutoTransferCard(IInterfaceHost view) {
        return !findAutoTransferCard(view).isEmpty();
    }

    @Nonnull
    private LocalizedTooltipText buildCardLine(IInterfaceHost view, ItemStack cardStack, boolean typeLabel) {
        int interval = getCardInterval(cardStack);
        if (interval <= 0) interval = ContainerPullPushCard.DEFAULT_INTERVAL;

        int quantity = getCardQuantity(cardStack);
        if (quantity <= 0) quantity = ContainerPullPushCard.MINIMUM_QUANTITY;

        String key = "tooltip.cells.probe.card." + (view.isExport() ? "push" : "pull");
        LocalizedTooltipText line = translate(
            key,
            translate(getTransferScope(view.getTargetFacings()).getLangKey()),
            literal(PollingRateUtils.format(interval)),
            buildSingleTransferQuantityText(quantity));

        return prefixTypeLabel(view, line, typeLabel);
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
    private LocalizedTooltipText prefixTypeLabel(IInterfaceHost view, LocalizedTooltipText line) {
        return prefixTypeLabel(view, line, true);
    }

    @Nonnull
    private LocalizedTooltipText prefixTypeLabel(IInterfaceHost view, LocalizedTooltipText line, boolean typeLabel) {
        if (!typeLabel) return line;
        if (!view.isTypeLabeled()) return line;

        return translate(
            "tooltip.cells.probe.type_prefix",
            translate(getResourceTypeLangKey(view.getResourceType())),
            line);
    }

    private boolean shouldTypeLabelCardLine(List<IInterfaceHost> sharedCardViews, IInterfaceHost view) {
        if (!view.isTypeLabeled()) return false;
        if (view.isDirectionalView()) return true;

        return sharedCardViews.size() > 1;
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
    private Scope getTransferScope(@Nullable Collection<?> facings) {
        if (facings != null && facings.size() == 1) return Scope.FACING;

        return Scope.ADJACENT;
    }

    @Nonnull
    private ItemStack findAutoTransferCard(IInterfaceHost view) {
        IItemHandler upgradeInventory = view.getUpgradeInventory();
        if (upgradeInventory == null) return ItemStack.EMPTY;

        ItemStack fallback = ItemStack.EMPTY;
        for (int slot = 0; slot < upgradeInventory.getSlots(); slot++) {
            ItemStack stack = upgradeInventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            boolean isPush = stack.getItem() instanceof ItemAutoPushCard;
            boolean isPull = stack.getItem() instanceof ItemAutoPullCard;

            if (view.isExport() && isPush) return stack;
            if (!view.isExport() && isPull) return stack;

            if (fallback.isEmpty() && (isPush || isPull)) fallback = stack;
        }

        return fallback;
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