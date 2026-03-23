package com.cells.blocks.interfacebase;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import appeng.client.gui.AEBaseGui;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.IJEIGhostIngredients;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.client.KeyBindings;
import com.cells.gui.DynamicTooltipTabButton;
import com.cells.gui.GuiClearFiltersButton;
import com.cells.gui.GuiPageNavigation;
import com.cells.gui.ImportInterfaceControlsHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenGui;


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
 *   <li>Page navigation (for capacity cards)</li>
 *   <li>Controls help widget</li>
 *   <li>JEI ghost ingredient framework</li>
 * </ul>
 * <p>
 * Subclasses only need to:
 * <ul>
 *   <li>Create their type-specific filter/tank slots in {@link #createResourceSlots()}</li>
 *   <li>Handle JEI ghost ingredients in {@link #createJEITargets(Object)}</li>
 *   <li>Optionally override {@link #handleQuickAdd(Slot)} for quick-add keybind</li>
 * </ul>
 *
 * @param <H> The host interface type (IFluidInterfaceHost, IGasInterfaceHost, IItemInterfaceHost)
 * @param <C> The container type
 */
public abstract class AbstractResourceInterfaceGui<H extends IInterfaceHost, C extends AEBaseContainer>
        extends AEBaseGui implements IJEIGhostIngredients {

    private static final ResourceLocation BACKGROUND_TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/import_interface.png");

    protected static final int SLOTS_PER_PAGE = 36;

    protected final C container;
    protected final H host;

    private DynamicTooltipTabButton configButton;
    private DynamicTooltipTabButton pollingRateButton;
    private GuiClearFiltersButton clearFiltersButton;
    private GuiPageNavigation pageNavigation;

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
     * Create the type-specific resource slots (filter and tank/storage slots).
     * Add them to {@code this.guiSlots} for custom slots or to {@code this.inventorySlots} for standard slots.
     */
    protected abstract void createResourceSlots();

    /**
     * Get the GUI ID for the max slot size configuration screen.
     */
    protected abstract int getMaxSlotSizeGuiId();

    /**
     * Get the GUI ID for the polling rate configuration screen.
     */
    protected abstract int getPollingRateGuiId();

    /**
     * Create JEI ghost ingredient targets for the given ingredient.
     * Return an empty list if the ingredient type is not supported.
     *
     * @param ingredient The ingredient being dragged from JEI
     * @return List of targets that can accept this ingredient
     */
    protected abstract List<Target<?>> createJEITargets(Object ingredient);

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

    // ============================== Common implementation ==============================

    @Override
    public void initGui() {
        super.initGui();

        String unit = I18n.format("cells.unit." + this.host.getTypeName());
        String direction = this.host.isExport() ? "export" : "import";

        // Create type-specific resource slots
        createResourceSlots();

        // Config button to open max slot size configuration screen
        this.configButton = new DynamicTooltipTabButton(
            this.guiLeft + 154,
            this.guiTop,
            2 + 4 * 16,
            () -> I18n.format("cells.max_slot_size.title") + "\n\n"
                + I18n.format("cells.slot_size", (int) this.getMaxSlotSize(), unit) + "\n"
                + I18n.format("cells.max_slot_size.tooltip", unit),
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
                    : I18n.format("cells.polling_rate.custom." + direction, ItemInterfaceLogic.formatPollingRate(rate));
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
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format(this.host.getGuiTitleLangKey()), 8, 6, 0x404040);

        // Draw controls help widget on the left side
        ImportInterfaceControlsHelper.drawControlsHelpWidget(
            this.fontRenderer,
            this.guiLeft,
            this.guiTop,
            this.ySize,
            !this.host.isExport()
        );
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

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
    }

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        return createJEITargets(ingredient);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return (Map<Target<?>, Object>) (Map<?, ?>) mapTargetSlot;
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
}
