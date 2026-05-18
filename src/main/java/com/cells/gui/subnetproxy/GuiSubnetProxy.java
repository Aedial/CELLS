package com.cells.gui.subnetproxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.awt.Rectangle;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.api.AEApi;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.localization.GuiText;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.fluids.util.AEFluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import com.cells.Tags;
import com.cells.client.KeyBindings;
import com.cells.gui.GuiPageNavigation;
import com.cells.gui.QuickAddHelper;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.integration.mekanismenergistics.IOGuiGasHelper;
import com.cells.integration.thaumicenergistics.ThaumicEnergisticsIntegration;
import com.cells.integration.thaumicenergistics.IOGuiEssentiaHelper;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketChangePage;
import com.cells.network.packets.PacketChangeFilterMode;
import com.cells.network.packets.PacketClearFilters;
import com.cells.network.packets.PacketOpenProxyPriority;
import com.cells.network.packets.PacketToggleProxyChannel;
import com.cells.network.sync.PacketQuickAddFilter;
import com.cells.network.sync.ResourceType;
import com.cells.parts.subnetproxy.PartSubnetProxyFront;


/**
 * GUI for the Subnet Proxy.
 * <p>
 * Layout (storage bus-like with all 7 rows open):
 * <ul>
 *   <li>Title + type button in header</li>
 *   <li>Page navigation arrows (same position as Import/Export Interface)</li>
 *   <li>63 filter slots (9×7) starting at y=29</li>
 *   <li>Upgrade slots (Cell Workbench dynamic columns) at y=26</li>
 *   <li>Player inventory at bottom</li>
 *   <li>Toolbox at y=183 (shifted from standard 161)</li>
 * </ul>
 */
public class GuiSubnetProxy extends AEBaseGui implements IJEIGhostIngredients {

    private static final int TOOLBOX_X = 178;
    private static final int TOOLBOX_Y = 183;
    private static final int TOOLBOX_SIZE = 68;

    private final ContainerSubnetProxy container;

    private GuiImgButton clearBtn;
    private GuiTabButton filterModeBtn;
    private GuiTabButton priorityBtn;
    private GuiPageNavigation pageNavigation;

    /** One toggle button per available channel, stacked vertically below {@link #clearBtn}. */
    private final List<GuiChannelToggleButton> channelButtons = new ArrayList<>();

    /** Tracks the last rendered filter mode ordinal for icon refresh */
    private int lastFilterMode = -1;

    public GuiSubnetProxy(final InventoryPlayer inventoryPlayer, final PartSubnetProxyFront part) {
        super(new ContainerSubnetProxy(inventoryPlayer, part));
        this.container = (ContainerSubnetProxy) this.inventorySlots;
        this.ySize = 251;
        this.xSize = 176;
    }

    private boolean hasToolbox() {
        return this.container.hasToolbox();
    }

    @Override
    public void initGui() {
        super.initGui();

        // Add 63 filter slot widgets (7 rows × 9 columns)
        // These are GUI-only widgets (GuiCustomSlot), not container slots.
        // They are synced via PacketResourceSlot, bypassing vanilla slot sync.
        final int xo = 8;
        final int yo = 29;
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 9; col++) {
                int displaySlot = row * 9 + col;
                this.guiSlots.add(new SubnetProxyFilterWidget(
                    this.container,
                    displaySlot,
                    xo + col * 18, yo + row * 18,
                    () -> this.container.currentPage * PartSubnetProxyFront.SLOTS_PER_PAGE
                ));
            }
        }

        // Clear filters button (left side buttons)
        this.clearBtn = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.buttonList.add(this.clearBtn);

        // Channel toggle buttons stacked vertically directly under the clear button.
        // We render one per AVAILABLE ResourceType (skipping mods that aren't loaded);
        // the part still tracks state for unavailable channels in NBT, in case the
        // mod is added later. Default state is everything off.
        this.channelButtons.clear();
        ResourceType[] available = ResourceType.getAvailableTypes();
        // Place the first toggle one row below the clear button, with a 1px gap to
        // visually separate the toggle group from the clear action above.
        int channelY = this.guiTop + 8 + 18 + 1;
        for (ResourceType type : available) {
            GuiChannelToggleButton btn = new GuiChannelToggleButton(
                100 + type.ordinal(),
                this.guiLeft - GuiChannelToggleButton.SIZE - 1, channelY,
                type,
                () -> this.container.isChannelEnabled(type)
            );
            this.channelButtons.add(btn);
            this.buttonList.add(btn);
            channelY += GuiChannelToggleButton.SIZE + 2;
        }

        // Type cycling button: positioned top-right of the title
        // Uses GuiTabButton which renders an item icon with a tooltip
        this.filterModeBtn = new GuiTabButton(
            this.guiLeft + this.xSize - 20 - 3, this.guiTop,
            getIconForResourceType(this.container.getFilterMode()),
            I18n.format("gui.cells.subnet_proxy.filter_mode"),
            this.itemRender
        ) {
            @Override
            public String getMessage() {
                // Dynamic tooltip showing the current filter mode name
                ResourceType mode = GuiSubnetProxy.this.container.getFilterMode();
                String type = I18n.format("cells.type." + mode.name().toLowerCase());
                return I18n.format("gui.cells.subnet_proxy.filter_mode", type) + "\n\n" + 
                    I18n.format("gui.cells.subnet_proxy.filter_mode.description");
            }
        };
        this.buttonList.add(this.filterModeBtn);

        // Page navigation arrows (same position as Import/Export Interface)
        this.pageNavigation = new GuiPageNavigation(
            3,
            this.guiLeft + this.xSize + 6, this.guiTop + 3,
            () -> this.container.currentPage,
            () -> this.container.totalPages,
            () -> {
                this.container.prevPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.container.currentPage));
            },
            () -> {
                this.container.nextPage();
                CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangePage(this.container.currentPage));
            }
        );
        this.buttonList.add(this.pageNavigation);

        // Priority button: opens AE2's priority GUI for this proxy
        this.priorityBtn = new GuiTabButton(
            this.guiLeft + this.xSize - 20 - 4 - 20 - 3, this.guiTop,
            2 + 4 * 16, GuiText.Priority.getLocal(), this.itemRender
        );
        this.buttonList.add(this.priorityBtn);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.cells.subnet_proxy.title"), 8, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, this.ySize - 96 + 3, 0x404040);
    }

    /**
     * Used by {@link NoChannelsWarningRenderer} to decide whether to paint
     * the warning. Exposed as a public accessor so the post-render hook can
     * query the container without grabbing protected fields.
     */
    public boolean shouldShowNoChannelsWarning() {
        return this.container.hasNoChannelsEnabled();
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Update filter mode button icon when the synced mode changes
        if (this.lastFilterMode != this.container.filterMode) {
            this.lastFilterMode = this.container.filterMode;
            // Recreate the button with the updated icon (GuiTabButton.myIcon is final)
            this.buttonList.remove(this.filterModeBtn);
            this.filterModeBtn = new GuiTabButton(
                this.guiLeft + this.xSize - 20 - 3, this.guiTop,
                getIconForResourceType(this.container.getFilterMode()),
                I18n.format("gui.cells.subnet_proxy.filter_mode"),
                this.itemRender
            ) {
                @Override
                public String getMessage() {
                    ResourceType mode = GuiSubnetProxy.this.container.getFilterMode();
                    String type = I18n.format("cells.type." + mode.name().toLowerCase());
                    return I18n.format("gui.cells.subnet_proxy.filter_mode", type) + "\n\n" +
                        I18n.format("gui.cells.subnet_proxy.filter_mode.description");
                }
            };
            this.buttonList.add(this.filterModeBtn);
        }

        this.mc.getTextureManager().bindTexture(
            new ResourceLocation(Tags.MODID, "textures/guis/subnet_proxy.png"));

        // Main GUI background (left portion without upgrade area)
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, this.ySize);

        // Navigation header area (right panel, above upgrade slots)
        this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 34, 19);

        // Upgrade area (Cell Workbench dynamic columns)
        int upgradeCount = this.container.availableUpgrades;
        if (upgradeCount > 0) drawUpgradeColumns(offsetX, offsetY, upgradeCount);

        // Toolbox
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + TOOLBOX_X, offsetY + TOOLBOX_Y,
                                       TOOLBOX_X, TOOLBOX_Y, TOOLBOX_SIZE, TOOLBOX_SIZE);
        }
    }

    /**
     * Draw upgrade slot background columns in the Cell Workbench style.
     * The number of columns (1-3) depends on the total upgrade slot count.
     * <p>
     * Cell Workbench layout: first column at x=177 (width 35), second at x=177+27 (width 27, texture origin 186),
     * third at x=177+27+18 (width 27, texture origin 186). Each column has a 7px top padding, 18px per slot,
     * and a 7px bottom cap. The cap texture is at y=170 in subnet_proxy.png (= y=151 in cellworkbench + 19px header offset).
     */
    private void drawUpgradeColumns(int offsetX, int offsetY, int upgradeCount) {
        // Y offset for the column start (after the navigation header)
        final int colY = 18;
        // Bottom cap texture Y position in subnet_proxy.png
        final int capTexY = 169;

        // TODO: refactor and make more flexible

        if (upgradeCount <= 8) {
            // Single column
            int rows = Math.min(upgradeCount, 8);
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY, 177, colY, 35, 7 + rows * 18 - 1);
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY + 7 + rows * 18 - 1, 177, capTexY, 35, 7);
        } else if (upgradeCount <= 16) {
            // First column (full 8 slots)
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY, 177, colY, 35, 7 + 8 * 18);
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY + 7 + 8 * 18, 177, capTexY, 35, 7);

            // Second column (narrower, texture origin 186)
            int secondRows = upgradeCount - 8;
            this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + colY, 186, colY, 27, 7 + secondRows * 18 - 1);
            this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + colY + 7 + secondRows * 18 - 1, 186, capTexY, 27, 7);
        } else {
            // First column (full 8 slots)
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY, 177, colY, 35, 7 + 8 * 18);
            this.drawTexturedModalRect(offsetX + 177, offsetY + colY + 7 + 8 * 18, 177, capTexY, 35, 7);

            // Second column (full 8 slots, narrower)
            this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + colY, 186, colY, 27, 7 + 8 * 18);
            this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + colY + 7 + 8 * 18, 186, capTexY, 27, 7);

            // Third column
            int thirdRows = upgradeCount - 16;
            this.drawTexturedModalRect(offsetX + 177 + 27 + 18, offsetY + colY, 186, colY, 27, 7 + thirdRows * 18 - 1);
            this.drawTexturedModalRect(offsetX + 177 + 27 + 18, offsetY + colY + 7 + thirdRows * 18 - 1, 186, capTexY, 27, 7);
        }
    }



    private static ItemStack getIconForResourceType(ResourceType type) {
        switch (type) {
            case ITEM:
                return new ItemStack(Blocks.CHEST);
            case FLUID:
                return new ItemStack(Items.WATER_BUCKET);
            case GAS:
                if (Loader.isModLoaded("mekanism")) return getGasTabIcon();
                return new ItemStack(Items.GLASS_BOTTLE);
            case ESSENTIA:
                if (Loader.isModLoaded("thaumcraft")) return getEssentiaTabIcon();
                return new ItemStack(Items.GLASS_BOTTLE);
            default:
                return new ItemStack(Blocks.BARRIER);
        }
    }

    @Optional.Method(modid = "mekanism")
    private static ItemStack getGasTabIcon() {
        return new ItemStack(mekanism.common.MekanismBlocks.GasTank);
    }

    @Optional.Method(modid = "thaumcraft")
    private static ItemStack getEssentiaTabIcon() {
        Block jar = thaumcraft.api.blocks.BlocksTC.jarNormal;
        if (jar != null) return new ItemStack(jar);
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.clearBtn) {
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketClearFilters());
        } else if (btn == this.filterModeBtn) {
            // Cycle filter mode
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketChangeFilterMode());
        } else if (btn == this.priorityBtn) {
            // Switch to AE2's priority GUI for this proxy
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketOpenProxyPriority());
        } else if (btn instanceof GuiChannelToggleButton) {
            // Toggle the channel; the new state will arrive back via @GuiSync,
            // so we don't need to optimistically update the local supplier.
            CellsNetworkHandler.INSTANCE.sendToServer(
                new PacketToggleProxyChannel(((GuiChannelToggleButton) btn).getType()));
        }
    }

    // ========================= Quick-Add Keybind =========================

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>();

        // Left-side buttons (clear button and any other buttons)
        int visibleButtons = (int) this.buttonList.stream()
            .filter(v -> v.enabled && v.x < guiLeft).count();
        if (visibleButtons > 0) {
            areas.add(new Rectangle(guiLeft - 18, guiTop + 8, 18,
                visibleButtons * 18 + visibleButtons - 2));
        }

        // Right-side upgrade area (always present)
        int upgradeCount = this.container.availableUpgrades;
        if (upgradeCount > 0) {
            int cols = upgradeCount <= 8 ? 1 : upgradeCount <= 16 ? 2 : 3;
            int rows = Math.min(upgradeCount, 8);
            areas.add(new Rectangle(guiLeft + 177, guiTop + 19,
                cols * 18 + 17, 7 + rows * 18));
        }

        // Right-side page navigation
        areas.add(new Rectangle(guiLeft + 177, guiTop, 34, 19));

        // Toolbox (if present)
        if (this.hasToolbox()) {
            areas.add(new Rectangle(guiLeft + TOOLBOX_X, guiTop + TOOLBOX_Y, TOOLBOX_SIZE, TOOLBOX_SIZE));
        }

        return areas;
    }

    // ========================= JEI Ghost Ingredient (drag from JEI to filter slots) =========================

    /**
     * Creates drop targets for each filter widget using the unified
     * AbstractResourceFilterSlot.createJEITarget() method.
     * <p>
     * Each widget handles its own conversion through
     * {@link SubnetProxyFilterWidget#convertToResource}, which uses the
     * current filter mode for ItemStack ingredients and direct conversion
     * for FluidStack ingredients (JEI already knows the type).
     */
    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        List<Target<?>> targets = new ArrayList<>();

        for (Object slotObj : this.guiSlots) {
            if (!(slotObj instanceof SubnetProxyFilterWidget)) continue;

            SubnetProxyFilterWidget widget = (SubnetProxyFilterWidget) slotObj;

            // Only create targets for widgets that can actually accept this ingredient.
            // This prevents all slots from lighting up green for incompatible types
            // (e.g. regular items showing as valid targets in Fluid mode).
            if (widget.convertToResource(ingredient) == null) continue;

            targets.add(widget.createJEITarget(this::getGuiLeft, this::getGuiTop));
        }

        return targets;
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

    /**
     * Handle quick-add: extract the resource under the cursor and send it
     * to the server for addition to the first available filter slot.
     * The resource type extracted depends on the current filter mode.
     *
     * @param hoveredSlot The slot under the mouse cursor, or null
     * @return true if the keybind was handled
     */
    private boolean handleQuickAdd(Slot hoveredSlot) {
        ResourceType mode = this.container.getFilterMode();

        switch (mode) {
            case ITEM: {
                ItemStack item = QuickAddHelper.getItemUnderCursor(hoveredSlot);
                if (!item.isEmpty()) {
                    IAEItemStack iaeItem = AEApi.instance().storage()
                        .getStorageChannel(IItemStorageChannel.class).createStack(item);
                    CellsNetworkHandler.INSTANCE.sendToServer(
                        new PacketQuickAddFilter(ResourceType.ITEM, iaeItem));
                    return true;
                }
                break;
            }

            case FLUID: {
                FluidStack fluid = QuickAddHelper.getFluidUnderCursor(hoveredSlot);
                if (fluid != null) {
                    CellsNetworkHandler.INSTANCE.sendToServer(
                        new PacketQuickAddFilter(ResourceType.FLUID, AEFluidStack.fromFluidStack(fluid)));
                    return true;
                }
                if (QuickAddHelper.hasAnythingUnderCursor(hoveredSlot)) {
                    QuickAddHelper.sendNoValidError("fluid");
                }
                return true;
            }

            case GAS: {
                if (MekanismEnergisticsIntegration.isModLoaded()) {
                    return IOGuiGasHelper.handleGasQuickAdd(hoveredSlot);
                }
                break;
            }

            case ESSENTIA: {
                if (ThaumicEnergisticsIntegration.isModLoaded()) {
                    return IOGuiEssentiaHelper.handleEssentiaQuickAdd(hoveredSlot);
                }
                break;
            }

            default:
                break;
        }

        return false;
    }
}
