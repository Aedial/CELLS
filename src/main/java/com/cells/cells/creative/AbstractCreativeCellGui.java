package com.cells.cells.creative;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.Optional;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.client.KeyBindings;
import com.cells.gui.GuiClearFiltersButton;
import com.cells.gui.slots.AbstractResourceFilterSlot;


/**
 * Abstract base GUI for Creative Cell screens (item, fluid, gas, essentia).
 * <p>
 * Provides common functionality:
 * - Standard texture and dimensions (210x241)
 * - Clear filters button
 * - Title and inventory label rendering
 * - JEI ghost ingredient setup
 * - Quick-add keybind handling
 * - Grid-based filter slot creation
 *
 * @param <C> Type of the container used by this GUI
 */
@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public abstract class AbstractCreativeCellGui<C extends AbstractCreativeCellContainer<?>>
        extends AEBaseGui implements IJEIGhostIngredients {

    protected static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/creative_cell.png");

    protected final C container;
    protected GuiClearFiltersButton clearButton;
    protected final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    protected AbstractCreativeCellGui(C container) {
        super(container);
        this.container = container;
        this.xSize = 210;
        this.ySize = 241;
    }

    /**
     * Get the localization key for the GUI title.
     */
    protected abstract String getTitleKey();

    /**
     * Called during initGui to create type-specific slots (items or fluids).
     * Default implementation creates a 9x7 grid using createSlotForIndex.
     * Override if special handling is needed.
     */
    protected void createFilterSlots() {
        // Uses the template method pattern - subclass provides the slot factory
        for (int row = 0; row < AbstractCreativeCellContainer.GRID_ROWS; row++) {
            for (int col = 0; col < AbstractCreativeCellContainer.GRID_COLS; col++) {
                int slotIndex = row * AbstractCreativeCellContainer.GRID_COLS + col;
                int x = AbstractCreativeCellContainer.FILTER_START_X + col * 18;
                int y = AbstractCreativeCellContainer.FILTER_START_Y + row * 18;
                GuiCustomSlot slot = createSlotForIndex(slotIndex, x, y);
                if (slot != null) this.guiSlots.add(slot);
            }
        }
    }

    /**
     * Factory method to create a filter slot at the given index and position.
     * Override in subclasses to provide the appropriate slot type.
     *
     * @param slotIndex Index in the filter grid (0-62)
     * @param x         X position in GUI
     * @param y         Y position in GUI
     * @return A GuiCustomSlot or null if using container-based slots (item cells)
     */
    protected GuiCustomSlot createSlotForIndex(int slotIndex, int x, int y) {
        return null; // Default: item cell uses container slots
    }

    /**
     * Clear all filters and sync to server if needed.
     */
    protected abstract void doClearFilters();

    /**
     * Handle quick-add keybind for the hovered slot.
     * Returns true if the keybind was handled.
     */
    protected abstract boolean handleQuickAdd(Slot hoveredSlot);

    @Override
    public void initGui() {
        super.initGui();

        // Create type-specific filter slots
        createFilterSlots();

        // Clear filters button - positioned right of the hotbar
        // Player inventory starts at y=159, hotbar is 58px below top of player inventory section
        this.clearButton = new GuiClearFiltersButton(0, this.guiLeft + 176 + 2, this.guiTop + 159 + 58,
            () -> I18n.format("cells.clear_filters") + "\n\n"
                + I18n.format("tooltip.cells.clear_filters.all"));
        this.buttonList.add(this.clearButton);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Title
        String title = I18n.format(getTitleKey());
        this.fontRenderer.drawString(title, 8, 6, 0x404040);

        // "Inventory" label above player inventory slots
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 148, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.checkHotbarKeys(keyCode)) return;

        // Handle quick-add keybind
        if (KeyBindings.QUICK_ADD_TO_FILTER.isActiveAndMatches(keyCode)) {
            Slot hoveredSlot = this.getSlotUnderMouse();
            if (handleQuickAdd(hoveredSlot)) return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(@Nonnull final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.clearButton) doClearFilters();
    }

    // =====================
    // IJEIGhostIngredients implementation
    // =====================

    /**
     * Unified JEI ghost ingredient target creation.
     * <p>
     * Iterates over all filter slots and creates JEI targets for slots that can
     * accept the given ingredient. This eliminates the need for type-specific
     * createTargets() overrides in subclasses.
     * <p>
     * This method is only available when JEI is loaded.
     */
    @Override
    @Optional.Method(modid = "jei")
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        mapTargetSlot.clear();
        List<Target<?>> targets = new ArrayList<>();

        for (GuiCustomSlot slot : this.guiSlots) {
            // Only filter slots support JEI drag-drop
            if (!(slot instanceof AbstractResourceFilterSlot)) continue;

            AbstractResourceFilterSlot<?> filterSlot = (AbstractResourceFilterSlot<?>) slot;

            // Check if this slot can accept the ingredient type
            if (filterSlot.convertToResource(ingredient) == null) continue;

            // Create JEI target using the slot's unified method
            Target<Object> target = filterSlot.createJEITarget(this::getGuiLeft, this::getGuiTop);
            targets.add(target);
            mapTargetSlot.putIfAbsent(target, slot);
        }

        return targets;
    }

    @Override
    @Optional.Method(modid = "jei")
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }

    /**
     * Factory method to create GUI from player inventory and hand.
     * Subclasses should implement a static factory method that calls their constructor.
     */
    public static <G extends AbstractCreativeCellGui<?>> G create(
            Class<G> clazz, InventoryPlayer playerInv, EnumHand hand) {
        throw new UnsupportedOperationException(
            "Subclass must implement a factory method or constructor");
    }
}
