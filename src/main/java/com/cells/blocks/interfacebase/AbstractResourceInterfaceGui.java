package com.cells.blocks.interfacebase;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.items.IItemHandler;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.tile.inventory.AppEngInternalInventory;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.iointerface.ContainerIOInterface;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.client.KeyBindings;
import com.cells.config.CellsConfig;
import com.cells.gui.DynamicTooltipTabButton;
import com.cells.gui.GuiClearFiltersButton;
import com.cells.gui.GuiControlsHelpToggleButton;
import com.cells.gui.GuiPageNavigation;
import com.cells.gui.GuiPullPushUpgradeButton;
import com.cells.gui.GuiRecipeTransferDirectionButton;
import com.cells.gui.ImportInterfaceControlsHelper;
import com.cells.gui.IToolboxContainer;
import com.cells.gui.slots.AbstractResourceFilterSlot;
import com.cells.gui.slots.AbstractResourceTankSlot;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;
import com.cells.network.packets.PacketOpenSlotOverrideGui;
import com.cells.util.PollingRateUtils;


/**
 * Abstract base GUI for all resource interface types (item, fluid, gas, essentia).
 * <p>
 * Provides all common functionality:
 * <ul>
 *   <li>Background texture rendering (same for all types)</li>
 *   <li>Title rendering from host's lang key</li>
 *   <li>Config button (max slot size)</li>
 *   <li>Polling rate button</li>
 *   <li>Clear filters button</li>
 *   <li>JEI recipe transfer routing toggle</li>
 *   <li>Page navigation (for capacity cards)</li>
 *   <li>Controls help widget</li>
 *   <li>JEI ghost ingredient framework</li>
 * </ul>
 * <p>
 * Subclasses only need to:
 * <ul>
 *   <li>Provide filter slot factory via {@link #createFilterSlotForIndex(int, int, int)}</li>
 *   <li>Provide tank/storage slot factory via {@link #createTankSlotForIndex(int, int, int)}</li>
 *   <li>Handle JEI ghost ingredients in {@link #getPhantomTargets(Object)}</li>
 *   <li>Optionally override {@link #handleQuickAdd(Slot)} for quick-add keybind</li>
 * </ul>
 *
 * @param <H> The host interface type (IFluidInterfaceHost, IGasInterfaceHost, IItemInterfaceHost)
 * @param <C> The container type
 */
@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public abstract class AbstractResourceInterfaceGui<H extends IInterfaceHost, C extends AEBaseContainer>
        extends AEBaseGui implements IJEIGhostIngredients {

    private static final ResourceLocation BACKGROUND_TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/import_interface.png");

    private static final ResourceLocation BACKGROUND_TEXTURE_WITH_CARDS =
        new ResourceLocation(Tags.MODID, "textures/guis/import_interface_withcards.png");

    /** Design constant - same across all resource types. */
    protected static final int SLOTS_PER_PAGE = AbstractResourceInterfaceLogic.SLOTS_PER_PAGE;

    protected final C container;
    protected final H host;

    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    private GuiClearFiltersButton clearFiltersButton;
    private GuiPageNavigation pageNavigation;
    private GuiPullPushUpgradeButton pullPushButton;
    private GuiControlsHelpToggleButton controlsToggleButton;
    private GuiRecipeTransferDirectionButton recipeTransferDirectionButton;

    // JEI ghost target mapping
    protected final Map<Object, Object> mapTargetSlot = new HashMap<>();

    /**
     * Constructor for tile entity hosts.
     */
    protected AbstractResourceInterfaceGui(C container, H host) {
        super(container);
        this.container = container;
        this.host = host;
        this.ySize = 256;
        this.xSize = 210;
    }

    // ============================== Abstract methods ==============================

    /**
     * Get the current page from the container.
     */
    protected abstract int getCurrentPage();

    /**
     * Get the total number of pages from the container.
     */
    protected abstract int getTotalPages();

    /**
     * Get the max slot size from the container.
     */
    protected abstract long getMaxSlotSize();

    /**
     * Get the polling rate from the container.
     */
    protected abstract long getPollingRate();

    /**
     * Navigate to the next page.
     */
    protected abstract void nextPage();

    /**
     * Navigate to the previous page.
     */
    protected abstract void prevPage();

    /**
     * Get the effective max slot size for a display slot, accounting for per-slot overrides.
     * Uses the container's ISizeOverrideContainer implementation if available,
     * falling back to the global max slot size.
     *
     * @param displaySlot The display slot index (0-35 within the current page)
     * @return The effective slot size (per-slot override or global)
     */
    protected long getEffectiveMaxSlotSizeForDisplay(int displaySlot) {
        int actualSlot = displaySlot + getCurrentPage() * SLOTS_PER_PAGE;

        if (this.container instanceof ISizeOverrideContainer) {
            return ((ISizeOverrideContainer) this.container).getEffectiveMaxSlotSize(actualSlot);
        }

        return getMaxSlotSize();
    }

    /**
     * Create a filter slot for the given display index.
     * Override in subclasses to return the appropriate filter slot type.
     *
     * @param displaySlot The display slot index (0-35)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @return The filter slot (FluidFilterSlot, GasFilterSlot, ItemFilterSlot, etc.)
     */
    protected abstract GuiCustomSlot createFilterSlotForIndex(int displaySlot, int x, int y);

    /**
     * Create a tank/storage slot for the given display index.
     * Override in subclasses to return the appropriate tank or storage slot type.
     *
     * @param displaySlot The display slot index (0-35)
     * @param x X position in GUI
     * @param y Y position in GUI
     * @return The tank slot (FluidTankSlot, GasTankSlot, ItemStorageSlot, etc.)
     */
    protected abstract GuiCustomSlot createTankSlotForIndex(int displaySlot, int x, int y);

    /**
     * Create the resource slots (filter and tank/storage) using the unified grid layout.
     * This is the sole implementation - subclasses only override the factory methods.
     */
    protected final void createResourceSlots() {
        // Add filter and tank/storage slots in 4x9 grid
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int displaySlot = row * 9 + col;
                if (displaySlot >= SLOTS_PER_PAGE) break;

                int xPos = 8 + col * 18;
                int filterY = 25 + row * 36;
                int tankY = filterY + 18; // 18px below filter slot

                // Create filter slot
                GuiCustomSlot filterSlot = createFilterSlotForIndex(displaySlot, xPos, filterY);

                // Wire up right-click handler for per-slot size override
                if (filterSlot instanceof AbstractResourceFilterSlot) {
                    final int displayIndex = displaySlot;
                    ((AbstractResourceFilterSlot<?>) filterSlot).setRightClickHandler(() -> {
                        // Compute absolute slot from display index + page offset
                        int absoluteSlot = displayIndex + getCurrentPage() * SLOTS_PER_PAGE;
                        BlockPos pos = this.host.getHostPos();
                        CellsNetworkHandler.INSTANCE.sendToServer(
                            new PacketOpenSlotOverrideGui(pos, absoluteSlot, this.host.getPartSide())
                        );
                    });
                }

                this.guiSlots.add(filterSlot);

                // Create tank/storage slot
                GuiCustomSlot tankSlot = createTankSlotForIndex(displaySlot, xPos, tankY);
                if (tankSlot instanceof AbstractResourceTankSlot) {
                    ((AbstractResourceTankSlot<?,?>) tankSlot).setFontRenderer(this.fontRenderer);
                }
                this.guiSlots.add(tankSlot);
            }
        }
    }

    /**
     * Get the GUI ID for the max slot size configuration screen.
     */
    protected abstract int getMaxSlotSizeGuiId();

    /**
     * Get the GUI ID for the polling rate configuration screen.
     */
    protected abstract int getPollingRateGuiId();

    /**
     * Get the type name used for unit localization (e.g. "item", "fluid", "gas", "essentia").
     * <p>
     * Defaults to {@code this.host.getTypeName()}, which works for single-type interfaces.
     */
    protected String getUnitTypeName() {
        return this.host.getTypeName();
    }

    /**
     * Handle quick-add keybind. Override in subclasses to implement type-specific behavior.
     *
     * @param hoveredSlot The slot currently under the mouse (may be null)
     * @return true if handled, false to pass to parent
     */
    protected boolean handleQuickAdd(Slot hoveredSlot) {
        // Default: no quick-add support
        return false;
    }

    /**
     * Build the shared JEI transfer routing tooltip shown in every interface GUI.
     */
    protected String getRecipeTransferButtonTooltip() {
        String importDir = CellsConfig.hidden.jeiTransferInputsToExport ? "outputs" : "inputs";
        String exportDir = CellsConfig.hidden.jeiTransferInputsToExport ? "inputs" : "outputs";
        String importInterfaceTarget = I18n.format("cells.recipe_component." + importDir);
        String exportInterfaceTarget = I18n.format("cells.recipe_component." + exportDir);

        // Indicate which interface is currently active
        String activeCurrent = I18n.format("tooltip.cells.recipe_transfer.current");
        if (this.isActiveTabExport()) {
            exportInterfaceTarget += activeCurrent;
        } else {
            importInterfaceTarget += activeCurrent;
        }

        return I18n.format("tooltip.cells.recipe_transfer.title") + "\n\n"
            + I18n.format("tooltip.cells.recipe_transfer.mapping1", importInterfaceTarget) + "\n"
            + I18n.format("tooltip.cells.recipe_transfer.mapping2", exportInterfaceTarget) + "\n"
            + "\n"
            + I18n.format("tooltip.cells.recipe_transfer.click_to_swap");
    }

    // ============================== Common implementation ==============================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Update pull/push button state (enabled/disabled, card icon)
        if (this.pullPushButton != null) {
            ItemStack card = findPullPushCard();
            this.pullPushButton.setCardStack(card);
            this.pullPushButton.enabled = !card.isEmpty();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Re-render the held item AFTER custom slots.
        // AEBaseGui renders guiSlots after super.drawScreen (which draws the held item),
        // so stack size text (with disableDepth) appears on top of the held item.
        // Re-rendering the held item here ensures it appears on top.
        ItemStack heldStack = this.mc.player.inventory.getItemStack();
        if (!heldStack.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, 200);
            RenderHelper.enableGUIStandardItemLighting();

            int x = mouseX - 8;
            int y = mouseY - 8;
            this.itemRender.renderItemAndEffectIntoGUI(heldStack, x, y);
            this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, heldStack, x, y, null);

            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        // Clear custom AE2 slots to prevent accumulation on GUI rebuild (e.g. window resize, tab switch).
        this.guiSlots.clear();

        String direction = this.host.isExport() ? "export" : "import";

        // Create type-specific resource slots
        createResourceSlots();

        // Toggle button to show/hide the controls help panel (placed before the title)
        this.controlsToggleButton = new GuiControlsHelpToggleButton(
            5,
            this.guiLeft + 6,
            this.guiTop + 6,  // title is at y=6
            () -> CellsConfig.hidden.showControlsHelp
                ? I18n.format("tooltip.cells.controls_help.hide")
                : I18n.format("tooltip.cells.controls_help.show")
        );
        this.buttonList.add(this.controlsToggleButton);

        // Toggle button to swap whether Import or Export interfaces receive recipe inputs.
        this.recipeTransferDirectionButton = new GuiRecipeTransferDirectionButton(
            6,
            this.guiLeft + 182,
            this.guiTop + 135,
            () -> CellsConfig.hidden.jeiTransferInputsToExport,
            this::getRecipeTransferButtonTooltip
        );
        this.buttonList.add(this.recipeTransferDirectionButton);

        // Config button to open max slot size configuration screen
        // Unit is resolved dynamically to allow for dynamic type
        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> {
                String unit = I18n.format("cells.unit." + this.getUnitTypeName());
                return I18n.format("cells.max_slot_size.title") + "\n\n"
                    + I18n.format("cells.slot_size", String.format("%,d", this.getMaxSlotSize()), unit) + "\n"
                    + I18n.format("cells.max_slot_size.tooltip", unit);
            },
            this.itemRender
        );
        this.buttonList.add(this.configButton);

        // Polling rate button
        this.pollingRateButton = new DynamicTooltipTabButton(
            this.guiLeft + 154 - 22,
            this.guiTop,
            2 + 5 * 16,
            () -> {
                int rate = (int) this.getPollingRate();
                String value = rate <= 0
                    ? I18n.format("cells.polling_rate.adaptive")
                    : I18n.format("cells.polling_rate.custom." + direction, PollingRateUtils.format(rate));
                return I18n.format("cells.polling_rate.title") + "\n\n"
                    + value + "\n"
                    + I18n.format("tooltip.cells.polling_rate." + direction);
            },
            this.itemRender
        );
        this.buttonList.add(this.pollingRateButton);

        // Clear filters button
        this.clearFiltersButton = new GuiClearFiltersButton(
            2,
            this.guiLeft + 186,
            this.guiTop + 232,
            () -> I18n.format("cells.clear_filters") + "\n\n"
                + I18n.format("tooltip.cells.clear_filters." + direction)
        );
        this.buttonList.add(this.clearFiltersButton);

        // Page navigation (only visible when capacity cards are installed)
        this.pageNavigation = new GuiPageNavigation(
            3,
            this.guiLeft + 181,
            this.guiTop + 3,
            this::getCurrentPage,
            this::getTotalPages,
            () -> {
                this.prevPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.getCurrentPage()));
            },
            () -> {
                this.nextPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.getCurrentPage()));
            }
        );
        this.buttonList.add(this.pageNavigation);

        // Pull/Push upgrade button (below upgrade slots)
        // When a Pull/Push card is installed in the upgrades, clicking this button
        // opens the card's configuration GUI directly from the interface.
        this.pullPushButton = new GuiPullPushUpgradeButton(
            4,
            this.guiLeft + 184,
            this.guiTop + 104,
            () -> {
                ItemStack card = this.findPullPushCard();
                if (card.isEmpty()) {
                    String cardName = this.isActiveTabExport()
                        ? I18n.format("item.cells.push_card.name")
                        : I18n.format("item.cells.pull_card.name");
                    return I18n.format("cells.pull_push_button.disabled", cardName);
                }

                String title = I18n.format("cells.pull_push_button.enabled.title");
                String desc = "§7" + I18n.format("cells.pull_push_button.enabled.desc");
                return title + "\n\n" + desc;
            },
            this.itemRender
        );
        this.buttonList.add(this.pullPushButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Draw title with truncation to avoid overlapping buttons.
        // The toggle arrow is at x=8 (8px wide), so the title starts at x=18 (8+8+2).
        // Buttons start at x=132, so title max width is 132-18 = 114 pixels.
        final int maxTitleWidth = 114;
        String title = I18n.format(this.host.getGuiTitleLangKey());
        int titleWidth = this.fontRenderer.getStringWidth(title);

        if (titleWidth > maxTitleWidth) {
            // Truncate title and add ellipsis
            String ellipsis = "...";
            int ellipsisWidth = this.fontRenderer.getStringWidth(ellipsis);
            int availableWidth = maxTitleWidth - ellipsisWidth;

            // Trim characters until it fits
            while (titleWidth > availableWidth && !title.isEmpty()) {
                title = title.substring(0, title.length() - 1);
                titleWidth = this.fontRenderer.getStringWidth(title);
            }
            title = title + ellipsis;
        }

        this.fontRenderer.drawString(title, 18, 6, 0x404040);

        // Draw controls help widget on the left side (only when enabled)
        if (CellsConfig.hidden.showControlsHelp) {
            ImportInterfaceControlsHelper.drawControlsHelpWidget(
                this.fontRenderer,
                this.guiLeft,
                this.guiTop,
                !this.host.isExport()
            );
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        boolean hasToolbox = this.hasToolbox();
        ResourceLocation texture = hasToolbox ? BACKGROUND_TEXTURE_WITH_CARDS : BACKGROUND_TEXTURE;

        this.mc.getTextureManager().bindTexture(texture);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        // Draw toolbox extension area (x=210-246, y=149-216)
        // The toolbox slots are at 186+3*18=240 max x, and y=156+3*18=210 max y
        // This extends past the main xSize (210), so we need an additional draw call
        // to render the portion from x=210 to x=246 (36 pixels wide), y=149 to y=216 (67 pixels tall)
        if (hasToolbox) {
            // Draw the extension from the same texture, sampling from x=210 in the texture
            this.drawTexturedModalRect(offsetX + 210, offsetY + 149, 210, 149, 36, 67);
        }
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.recipeTransferDirectionButton) {
            CellsConfig.setJeiTransferInputsToExport(!CellsConfig.hidden.jeiTransferInputsToExport);
            return;
        }

        BlockPos pos = this.host.getHostPos();

        if (btn == this.configButton) {
            if (this.host.isPart()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos,
                    getMaxSlotSizeGuiId(),
                    this.host.getPartSide()
                ));
            } else {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    getMaxSlotSizeGuiId()
                ));
            }
            return;
        }

        if (btn == this.pollingRateButton) {
            if (this.host.isPart()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos,
                    getPollingRateGuiId(),
                    this.host.getPartSide()
                ));
            } else {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    getPollingRateGuiId()
                ));
            }
            return;
        }

        if (btn == this.clearFiltersButton) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketClearFilters());
        }

        if (btn == this.controlsToggleButton) {
            CellsConfig.setShowControlsHelp(!CellsConfig.hidden.showControlsHelp);
            return;
        }

        if (btn == this.pullPushButton && this.pullPushButton.enabled) {
            BlockPos pullPushPos = this.host.getHostPos();
            int guiId = this.host.isPart()
                ? com.cells.gui.CellsGuiHandler.GUI_PART_PULL_PUSH_CARD_INTERFACE
                : com.cells.gui.CellsGuiHandler.GUI_PULL_PUSH_CARD_INTERFACE;

            if (this.host.isPart()) {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pullPushPos, guiId, this.host.getPartSide()));
            } else {
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenGui(
                    pullPushPos.getX(), pullPushPos.getY(), pullPushPos.getZ(), guiId));
            }
        }
    }

    /**
     * Unified JEI ghost ingredient target creation.
     * <p>
     * Iterates over all filter slots and creates JEI targets for slots that can
     * accept the given ingredient. This eliminates the need for type-specific
     * createJEITargets() overrides in subclasses.
     * <p>
     * This method is only available when JEI is loaded.
     */
    @Override
    @Optional.Method(modid = "jei")
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        List<Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            // Only filter slots support JEI drag-drop
            if (!(slot instanceof AbstractResourceFilterSlot)) continue;

            // Use raw type to avoid generic capture issues - we only need null check
            @SuppressWarnings("rawtypes")
            AbstractResourceFilterSlot filterSlot = (AbstractResourceFilterSlot) slot;

            // Check if this slot can accept the ingredient type
            if (filterSlot.convertToResource(ingredient) == null) continue;

            // Create JEI target using the slot's unified method
            @SuppressWarnings("unchecked")
            Target<Object> target = filterSlot.createJEITarget(this::getGuiLeft, this::getGuiTop);
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Optional.Method(modid = "jei")
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return (Map<Target<?>, Object>) (Map<?, ?>) mapTargetSlot;
    }

    /**
     * Provide JEI exclusion areas for the toolbox extension.
     * <p>
     * When the toolbox is present, it extends past the main GUI area (x=210-246, y=149-216).
     * This tells JEI to avoid drawing items in that region.
     */
    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>(super.getJEIExclusionArea());

        // Add controls help widget area on the left side (only when the panel is visible)
        if (CellsConfig.hidden.showControlsHelp) {
            Rectangle controlsBounds = ImportInterfaceControlsHelper.getBounds(
                this.fontRenderer,
                this.guiLeft,
                this.guiTop,
                !this.host.isExport()
            );

            if (controlsBounds.width > 0 && controlsBounds.height > 0) areas.add(controlsBounds);
        }

        // Add toolbox extension area when present
        boolean hasToolbox = this.hasToolbox();

        if (hasToolbox) {
            // Toolbox extension: x=210-246 (36px width), y=149-216 (67px height)
            // Offset by guiLeft and guiTop for screen coordinates
            areas.add(new Rectangle(this.guiLeft + 210, this.guiTop + 149, 36, 67));
        }

        return areas;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Handle quick-add keybind
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            if (handleQuickAdd(hoveredSlot)) return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    // ============================== Pull/Push card helpers ==============================

    /**
     * Find an installed Pull/Push card in the upgrade slots, if any.
     * <p>
     * Handles three host shapes:
     * <ul>
     *   <li>{@link IFilterableInterfaceHost}: single-direction host with a single
     *       upgrade inventory.</li>
     *   <li>{@link ICombinedInterfaceHost}: shares one upgrade inventory across
     *       all logics, accessible via any logic.</li>
     *   <li>{@link IIOInterfaceHost}: has TWO upgrade inventories (one per
     *       direction). For these, we read from the container's switchable
     *       upgrade inventory view rather than going through {@code host.getActiveLogic()}:
     *       the host's active tab can be out of sync between client and server
     *       (especially in singleplayer where the host instance is shared), but
     *       the container's view always reflects the visible slot contents.</li>
     * </ul>
     *
     * @return The card ItemStack, or {@link ItemStack#EMPTY} if none is installed.
     */
    @SuppressWarnings("rawtypes")
    private ItemStack findPullPushCard() {
        // IO interfaces: read from the container's switchable upgrade inventory
        // (which mirrors what the user sees in the upgrade slots, regardless of
        // any client/server desync of host.getActiveLogic()).
        if (this.host instanceof IIOInterfaceHost
            && this.container instanceof ContainerIOInterface) {
            IItemHandler upgradeView =
                ((ContainerIOInterface) this.container).getUpgradeInventoryView();
            return findCardIn(upgradeView);
        }

        AppEngInternalInventory upgradeInv = null;

        if (this.host instanceof IFilterableInterfaceHost) {
            upgradeInv = ((IFilterableInterfaceHost) this.host).getUpgradeInventory();
        } else if (this.host instanceof ICombinedInterfaceHost) {
            // Combined hosts share one upgrade inventory across all logics, accessible via any logic
            upgradeInv = ((ICombinedInterfaceHost) this.host).getItemLogic().getUpgradeInventory();
        }

        if (upgradeInv == null) return ItemStack.EMPTY;

        return findCardIn(upgradeInv);
    }

    /**
     * Scans an item handler for an Auto-Pull or Auto-Push card.
     */
    private static ItemStack findCardIn(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);

            if (stack.getItem() instanceof ItemAutoPullCard
                || stack.getItem() instanceof ItemAutoPushCard) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Returns whether the currently-active tab is the export direction.
     * <p>
     * For {@link IIOInterfaceHost} we trust the container's {@code @GuiSync} tab field
     * (kept in sync with the server and updated optimistically on client-side
     * tab clicks) rather than {@code host.isExport()}, which goes through
     * {@code host.getActiveDirectionTab()} and may be stale on the client.
     */
    private boolean isActiveTabExport() {
        if (this.host instanceof IIOInterfaceHost
            && this.container instanceof ContainerIOInterface) {
            return ((ContainerIOInterface) this.container).activeDirectionTab
                == IIOInterfaceHost.TAB_EXPORT;
        }
        return this.host.isExport();
    }

    private boolean hasToolbox() {
        return this.container instanceof IToolboxContainer
            && ((IToolboxContainer) this.container).hasToolbox();
    }
}
