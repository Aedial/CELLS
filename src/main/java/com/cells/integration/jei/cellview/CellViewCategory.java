package com.cells.integration.jei.cellview;

import java.awt.Point;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag.TooltipFlags;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.StackSizeRenderer;
import appeng.fluids.client.render.FluidStackSizeRenderer;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.AEItemStack;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.ITooltipCallback;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;

import com.cells.Cells;
import com.cells.Tags;
import com.cells.config.CellsConfig;
import com.cells.integration.jei.IRecipeCategoryWithOverlay;
import com.cells.integration.mekanismenergistics.MekanismEnergisticsIntegration;
import com.cells.util.NBTSizeHelper;


/**
 * JEI category for displaying cell contents in a grid view.
 * <p>
 * Shows:
 * - Installed upgrades as smaller icons with tooltips
 * - Size: "current / total" with readable number format
 * - Types: "current / max (overhead)" with equal distribution info
 * - NBT Size with color coding and warnings
 * - Grid of stored items/fluids/essentia/gas with AE2-style stack size rendering
 * - Pagination when more than 63 types are stored
 */
@SideOnly(Side.CLIENT)
public class CellViewCategory implements IRecipeCategory<CellViewRecipe>, IRecipeCategoryWithOverlay {

    public static final String UID = Tags.MODID + ":cell_view";

    // Layout constants
    private static final int WIDTH = 176;
    private static final int HEADER_HEIGHT = 52;
    private static final int GRID_ROWS = 7;
    private static final int GRID_COLS = 9;
    private static final int ITEMS_PER_PAGE = GRID_ROWS * GRID_COLS; // 63
    private static final int SLOT_SIZE = 18;
    private static final int GRID_HEIGHT = GRID_ROWS * SLOT_SIZE;
    private static final int FOOTER_HEIGHT = 14;
    private static final int TOTAL_HEIGHT = HEADER_HEIGHT + GRID_HEIGHT + FOOTER_HEIGHT;

    // Upgrade display
    private static final int UPGRADE_SIZE = 12;
    private static final int UPGRADE_Y = 2;

    private final IDrawableStatic background;
    private final IDrawableStatic slotSprite;
    private final IDrawableStatic warningIcon;
    private IDrawable icon;
    private final StackSizeRenderer stackSizeRenderer = new StackSizeRenderer();
    private final FluidStackSizeRenderer fluidStackSizeRenderer = new FluidStackSizeRenderer();

    // Current recipe state (used during rendering)
    private CellViewRecipe currentRecipe;
    private int currentPage = 0;
    private int totalPages = 1;
    private List<Point> slotPositions = new ArrayList<>();
    private boolean isFluidChannel = false;
    private boolean isGasChannel = false;

    public CellViewCategory(IJeiHelpers helpers) {
        IGuiHelper guiHelper = helpers.getGuiHelper();

        this.background = guiHelper.createBlankDrawable(WIDTH, TOTAL_HEIGHT);

        // Create slot sprite (18x18 texture)
        this.slotSprite = guiHelper.drawableBuilder(
                new ResourceLocation(Tags.MODID, "textures/guis/slot.png"), 0, 0, 18, 18)
            .setTextureSize(18, 18)
            .build();

        // Warning icon for NBT size issues
        this.warningIcon = guiHelper.drawableBuilder(
                new ResourceLocation(Tags.MODID, "textures/guis/warning.png"), 0, 0, 16, 16)
            .setTextureSize(16, 16)
            .build();

        // Get the cell icon for the category
        this.icon = guiHelper.drawableBuilder(
            new ResourceLocation(Tags.MODID, "textures/items/cells/hyper_density/hyper_density_cell_1g.png"), 0, 0, 16, 16)
            .setTextureSize(16, 16)
            .build();
    }

    @Override
    @Nonnull
    public String getUid() {
        return UID;
    }

    @Override
    @Nonnull
    public String getTitle() {
        return I18n.format("jei.cells.cellview.title");
    }

    @Override
    @Nonnull
    public String getModName() {
        return Tags.MODNAME;
    }

    @Override
    @Nonnull
    public IDrawable getBackground() {
        return background;
    }

    @Override
    @Nullable
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull CellViewRecipe recipeWrapper,
                          @Nonnull IIngredients ingredients) {
        this.currentRecipe = recipeWrapper;
        this.currentPage = 0;
        this.slotPositions.clear();

        if (!recipeWrapper.hasValidInventory()) return;

        int totalStacks = recipeWrapper.getStoredStacks().size();
        this.totalPages = Math.max(1, (int) Math.ceil((double) totalStacks / ITEMS_PER_PAGE));

        // Detect storage channel type
        this.isFluidChannel = false;
        this.isGasChannel = false;
        if (recipeWrapper.getChannel() != null) {
            String channelName = recipeWrapper.getChannel().getClass().getSimpleName().toLowerCase();
            this.isFluidChannel = channelName.contains("fluid");
            this.isGasChannel = channelName.contains("gas");
        }

        setupGridSlots(recipeLayout, recipeWrapper);
    }

    private void setupGridSlots(IRecipeLayout recipeLayout, CellViewRecipe recipe) {
        int startIdx = currentPage * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, recipe.getStoredStacks().size());
        int gridStartX = (WIDTH - GRID_COLS * SLOT_SIZE) / 2;
        int gridStartY = HEADER_HEIGHT;

        slotPositions.clear();

        // Build slot positions
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int x = gridStartX + col * SLOT_SIZE;
            int y = gridStartY + row * SLOT_SIZE;

            slotPositions.add(new Point(x, y));
        }

        // For fluid channels, use JEI's fluid stack group for proper rendering
        // For gas channels, we render manually in drawExtras (since JEI doesn't support gases)
        if (isFluidChannel) {
            setupFluidSlots(recipeLayout, recipe, startIdx, endIdx);
        } else if (isGasChannel) {
            // Gas rendering is done manually in drawExtras, but we still need tooltips
            setupGasSlotTooltips(recipeLayout, recipe, startIdx, endIdx);
        } else {
            setupItemSlots(recipeLayout, recipe, startIdx, endIdx);
        }
    }

    private void setupFluidSlots(IRecipeLayout recipeLayout, CellViewRecipe recipe, int startIdx, int endIdx) {
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        final CellViewRecipe recipeRef = recipe;
        final int startIdxFinal = startIdx;

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            Point pos = slotPositions.get(i);
            int stackIdx = startIdx + i;

            if (stackIdx < endIdx) {
                CellViewRecipe.StoredStackInfo info = recipe.getStoredStacks().get(stackIdx);
                IAEStack<?> aeStack = info.stack;

                if (aeStack instanceof IAEFluidStack) {
                    IAEFluidStack fluidAeStack = (IAEFluidStack) aeStack;
                    FluidStack fluidStack = fluidAeStack.getFluidStack();
                    if (fluidStack != null) {
                        // Init with proper capacity for rendering (use 1000 mB as the "full" amount for visual)
                        fluidStacks.init(i, true, pos.x + 1, pos.y + 1, 16, 16, 1000, false, null);
                        fluidStacks.set(i, fluidStack);
                    }
                }
            }
        }

        // Add tooltip callback for fluids
        fluidStacks.addTooltipCallback(new ITooltipCallback<FluidStack>() {
            @Override
            public void onTooltip(int slotIndex, boolean input, FluidStack ingredient, List<String> tooltip) {
                int stackIdx = startIdxFinal + slotIndex;
                if (stackIdx >= recipeRef.getStoredStacks().size()) return;

                CellViewRecipe.StoredStackInfo info = recipeRef.getStoredStacks().get(stackIdx);
                NumberFormat format = NumberFormat.getInstance();

                tooltip.add("");
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.stored_units",
                    format.format(info.count / 1000.0), I18n.format("jei.cells.cellview.unit.fluid")));
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.bytes_used",
                    format.format(info.bytesUsed)));
            }
        });
    }

    private void setupItemSlots(IRecipeLayout recipeLayout, CellViewRecipe recipe, int startIdx, int endIdx) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        final CellViewRecipe recipeRef = recipe;
        final int startIdxFinal = startIdx;

        // Set up item slots
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            Point pos = slotPositions.get(i);
            int stackIdx = startIdx + i;

            if (stackIdx < endIdx) {
                CellViewRecipe.StoredStackInfo info = recipe.getStoredStacks().get(stackIdx);
                ItemStack displayStack = info.stack.asItemStackRepresentation();

                itemStacks.init(i, true, pos.x, pos.y);
                itemStacks.set(i, displayStack);
            }
        }

        // Add tooltip callback for items
        itemStacks.addTooltipCallback(new ITooltipCallback<ItemStack>() {
            @Override
            public void onTooltip(int slotIndex, boolean input, ItemStack ingredient, List<String> tooltip) {
                int stackIdx = startIdxFinal + slotIndex;
                if (stackIdx >= recipeRef.getStoredStacks().size()) return;

                CellViewRecipe.StoredStackInfo info = recipeRef.getStoredStacks().get(stackIdx);
                NumberFormat format = NumberFormat.getInstance();

                tooltip.add("");

                // Show precise count with appropriate units
                String unitKey = getUnitKey(recipeRef);
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.stored_units",
                    format.format(info.count), I18n.format(unitKey)));

                // For compacting cells, show if this is a compressed/decompressed form
                boolean virtualForm = false;
                if (recipeRef.isCompacting() && recipeRef.isChainInitialized()) {
                    ItemStack partitioned = recipeRef.getPartitionedItem();
                    if (!partitioned.isEmpty() && !ItemStack.areItemsEqual(ingredient, partitioned)) {
                        tooltip.add("§7" + I18n.format("jei.cells.cellview.tooltip.virtual_form") + "§r");
                        virtualForm = true;
                    }
                }

                // Show bytes used by this stack (if not virtual form or creative cell)
                // Creative cells don't consume bytes - they're infinite sources
                if (!virtualForm && !recipeRef.isCreative()) {
                    tooltip.add(I18n.format("jei.cells.cellview.tooltip.bytes_used",
                        format.format(info.bytesUsed)));
                }

            }
        });
    }

    /**
     * Set up gas slot tooltips. Gas rendering is done manually, but we need tooltip support via item slots.
     * We use item representation for JEI's tooltip system, but render the actual gas sprite in drawExtras.
     */
    private void setupGasSlotTooltips(IRecipeLayout recipeLayout, CellViewRecipe recipe, int startIdx, int endIdx) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        final CellViewRecipe recipeRef = recipe;
        final int startIdxFinal = startIdx;

        // Set up item slots for tooltips only (actual rendering done in drawExtras)
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            Point pos = slotPositions.get(i);
            int stackIdx = startIdx + i;

            if (stackIdx < endIdx) {
                CellViewRecipe.StoredStackInfo info = recipe.getStoredStacks().get(stackIdx);
                ItemStack displayStack = info.stack.asItemStackRepresentation();

                itemStacks.init(i, true, pos.x, pos.y);
                itemStacks.set(i, displayStack);
            }
        }

        // Add tooltip callback for gas slots
        itemStacks.addTooltipCallback(new ITooltipCallback<ItemStack>() {
            @Override
            public void onTooltip(int slotIndex, boolean input, ItemStack ingredient, List<String> tooltip) {
                int stackIdx = startIdxFinal + slotIndex;
                if (stackIdx >= recipeRef.getStoredStacks().size()) return;

                CellViewRecipe.StoredStackInfo info = recipeRef.getStoredStacks().get(stackIdx);
                NumberFormat format = NumberFormat.getInstance();

                tooltip.add("");
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.stored_units",
                    format.format(info.count / 1000.0), I18n.format("jei.cells.cellview.unit.gas")));
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.bytes_used",
                    format.format(info.bytesUsed)));
            }
        });
    }

    /**
     * Get the appropriate unit localization key based on the storage channel.
     */
    private static String getUnitKey(CellViewRecipe recipe) {
        if (recipe.getChannel() == null) return "jei.cells.cellview.unit.items";

        String channelName = recipe.getChannel().getClass().getSimpleName().toLowerCase();
        if (channelName.contains("fluid")) return "jei.cells.cellview.unit.fluid";
        if (channelName.contains("gas")) return "jei.cells.cellview.unit.gas";
        if (channelName.contains("essentia")) return "jei.cells.cellview.unit.essentia";

        return "jei.cells.cellview.unit.items";
    }

    @Override
    public void drawExtras(@Nonnull Minecraft minecraft) {
        if (currentRecipe == null || !currentRecipe.hasValidInventory()) {
            drawNoDataMessage(minecraft);
            return;
        }

        FontRenderer font = minecraft.fontRenderer;
        int y = 2;
        int leftMargin = 4;

        // Row 1: Cell name + Upgrades
        // Draw cell display name on left (use most of the available width)
        String cellName = currentRecipe.getCellStack().getDisplayName();
        int upgradeAreaWidth = currentRecipe.getInstalledUpgrades().size() * (UPGRADE_SIZE + 1) + 8;
        int maxNameWidth = WIDTH - leftMargin - upgradeAreaWidth;
        if (font.getStringWidth(cellName) > maxNameWidth) {
            cellName = font.trimStringToWidth(cellName, maxNameWidth - 10) + "...";
        }
        font.drawString(cellName, leftMargin, y, 0x000000);

        // Draw upgrades on right
        drawUpgrades(minecraft, y);

        y += font.FONT_HEIGHT + 4;

        // Row 2: Size information (with HD multiplier note if applicable)
        String sizeLabel = I18n.format("jei.cells.cellview.size");
        String sizeValue;
        if (currentRecipe.isHyperDensity()) {
            // Show HD indicator with effective storage info
            sizeValue = ReadableNumberConverter.INSTANCE.toWideReadableForm(currentRecipe.getUsedBytes())
                + " / " + ReadableNumberConverter.INSTANCE.toWideReadableForm(currentRecipe.getTotalBytes())
                + " §5(HD)§r";
        } else {
            sizeValue = ReadableNumberConverter.INSTANCE.toWideReadableForm(currentRecipe.getUsedBytes())
                + " / " + ReadableNumberConverter.INSTANCE.toWideReadableForm(currentRecipe.getTotalBytes());
        }
        font.drawString(sizeLabel + " " + sizeValue, leftMargin, y, 0x000000);

        y += font.FONT_HEIGHT + 2;

        // Row 3: Types information with overhead OR per-type limit for equal distribution
        String typesLine;
        if (currentRecipe.hasEqualDistribution() && currentRecipe.getPerTypeLimit() > 0) {
            // Equal distribution active: show per-type limit instead of overhead
            String perTypeStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(currentRecipe.getPerTypeLimit());
            int effectiveMax = currentRecipe.getEqualDistributionLimit() > 0
                ? currentRecipe.getEqualDistributionLimit()
                : currentRecipe.getMaxTypes();
            typesLine = I18n.format("jei.cells.cellview.types") + " " + currentRecipe.getUsedTypes()
                + " / " + effectiveMax
                + " §8(" + perTypeStr + " " + I18n.format("jei.cells.cellview.per_type") + ")§r";
        } else {
            // Standard cells: show overhead
            String typesLabel = I18n.format("jei.cells.cellview.types");
            long overheadBytes = currentRecipe.getOverheadBytes();
            String overheadStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(overheadBytes);
            typesLine = typesLabel + " " + currentRecipe.getUsedTypes() + " / " + currentRecipe.getMaxTypes()
                + " §8(" + overheadStr + ")§r";
        }
        font.drawString(typesLine, leftMargin, y, 0x000000);

        y += font.FONT_HEIGHT + 2;

        // Row 4: Cell-type specific info OR NBT size
        if (currentRecipe.isCompacting()) {
            // Compacting cells: show partition/compression info
            drawCompactingInfo(font, leftMargin, y);
        } else {
            // Other cells: show NBT size
            drawNbtInfo(font, minecraft, leftMargin, y);
        }

        // Draw slot backgrounds
        drawSlotBackgrounds(minecraft);

        // For gas channels, render gas stacks manually with proper sprite
        if (isGasChannel) drawGasStacks(minecraft);
    }

    /**
     * Draw gas stacks with proper fluid-style rendering (using Mekanism's gas sprites).
     */
    private void drawGasStacks(Minecraft minecraft) {
        if (currentRecipe == null) return;

        int startIdx = currentPage * ITEMS_PER_PAGE;

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();

        for (int i = 0; i < slotPositions.size() && (startIdx + i) < currentRecipe.getStoredStacks().size(); i++) {
            CellViewRecipe.StoredStackInfo info = currentRecipe.getStoredStacks().get(startIdx + i);
            Point pos = slotPositions.get(i);

            // Render gas with proper sprite and color
            // Cells.LOGGER.info("Rendering gas stack: " + info.stack);
            MekanismEnergisticsIntegration.renderGasInGui(info.stack, pos.x + 1, pos.y + 1, 16, 16);
        }

        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    /**
     * Draw compacting cell specific information (partition, compression tiers).
     */
    private void drawCompactingInfo(FontRenderer font, int leftMargin, int y) {
        if (!currentRecipe.hasPartition()) {
            font.drawString("§4" + I18n.format("jei.cells.cellview.not_partitioned") + "§r", leftMargin, y, 0x404040);
            return;
        }

        if (!currentRecipe.isChainInitialized()) {
            font.drawString("§4" + I18n.format("jei.cells.cellview.chain_pending") + "§r", leftMargin, y, 0x404040);
            return;
        }

        // Show compression tiers info
        int tiersUp = currentRecipe.getTiersUp();
        int tiersDown = currentRecipe.getTiersDown();
        String tiersInfo = I18n.format("jei.cells.cellview.compression_tiers", tiersUp, tiersDown);
        if (currentRecipe.hasOreDictCard()) tiersInfo += "§r §6[OD]§r";

        int tierInfoWidth = font.getStringWidth(tiersInfo);

        ItemStack partitioned = currentRecipe.getPartitionedItem();
        String partitionName = partitioned.isEmpty() ? "?" : partitioned.getDisplayName();
        if (font.getStringWidth(partitionName) > 160 - tierInfoWidth) {
            partitionName = font.trimStringToWidth(partitionName, 160 - tierInfoWidth - 3) + "...";
        };

        font.drawString(partitionName + "§r §8" + tiersInfo, leftMargin, y, 0x404040);
    }

    /**
     * Draw NBT size information.
     */
    private void drawNbtInfo(FontRenderer font, Minecraft minecraft, int leftMargin, int y) {
        int nbtSize = currentRecipe.getNbtSize();
        long warningThreshold = NBTSizeHelper.kbToBytes(CellsConfig.nbtSizeWarningThresholdKB);
        String nbtSizeStr = NBTSizeHelper.formatSizeWithColor(nbtSize, warningThreshold);
        String nbtLabel = I18n.format("jei.cells.cellview.nbt_size") + " " + nbtSizeStr;

        // replace colors for dark background -> light background
        nbtLabel = nbtLabel.replace("§c", "§4").replace("§e", "§6").replace("§a", "§2");

        font.drawString(nbtLabel, leftMargin, y, 0x000000);

        // Draw warning icon if NBT size exceeds threshold
        if (NBTSizeHelper.exceedsThreshold(nbtSize, warningThreshold)) {
            int nbtLabelWidth = font.getStringWidth(I18n.format("jei.cells.cellview.nbt_size") + " "
                + NBTSizeHelper.formatSize(nbtSize));
            GlStateManager.color(1f, 1f, 1f, 1f);
            warningIcon.draw(minecraft, leftMargin + nbtLabelWidth + 2, y);
        }
    }

    private void drawNoDataMessage(Minecraft minecraft) {
        FontRenderer font = minecraft.fontRenderer;
        String msg = I18n.format("jei.cells.cellview.no_data");
        int x = (WIDTH - font.getStringWidth(msg)) / 2;
        int y = HEADER_HEIGHT + GRID_HEIGHT / 2 - font.FONT_HEIGHT / 2;
        font.drawString(msg, x, y, 0x808080);
    }

    private void drawUpgrades(Minecraft minecraft, int y) {
        List<ItemStack> upgrades = currentRecipe.getInstalledUpgrades();
        if (upgrades.isEmpty()) return;

        int x = WIDTH - 4 - upgrades.size() * (UPGRADE_SIZE + 1);

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();

        for (ItemStack upgrade : upgrades) {
            // Scale down to UPGRADE_SIZE
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0);
            float scale = 0.5f;
            GlStateManager.scale(scale, scale, 1f);
            minecraft.getRenderItem().renderItemIntoGUI(upgrade, 0, 0);
            GlStateManager.popMatrix();

            x += UPGRADE_SIZE + 1;
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.popMatrix();
    }

    private void drawSlotBackgrounds(Minecraft minecraft) {
        GlStateManager.color(1f, 1f, 1f, 1f);

        for (Point pos : slotPositions) slotSprite.draw(minecraft, pos.x, pos.y);
    }

    private void drawStackSizes(Minecraft minecraft) {
        if (currentRecipe == null) return;

        int startIdx = currentPage * ITEMS_PER_PAGE;

        // Push z-level forward to render on top of items
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 200);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        for (int i = 0; i < slotPositions.size() && (startIdx + i) < currentRecipe.getStoredStacks().size(); i++) {
            CellViewRecipe.StoredStackInfo info = currentRecipe.getStoredStacks().get(startIdx + i);
            Point pos = slotPositions.get(i);

            // Use appropriate renderer based on channel type
            if (isFluidChannel && info.stack instanceof IAEFluidStack) {
                IAEFluidStack fluidStack = (IAEFluidStack) info.stack;
                fluidStackSizeRenderer.renderStackSize(minecraft.fontRenderer, fluidStack, pos.x + 1, pos.y + 1);
            } else {
                // For items and other types (gas, essentia), use item stack representation
                AEItemStack aeStack = AEItemStack.fromItemStack(info.stack.asItemStackRepresentation());
                if (aeStack != null) {
                    aeStack.setStackSize(info.count);
                    stackSizeRenderer.renderStackSize(minecraft.fontRenderer, aeStack, pos.x + 1, pos.y + 1);
                }
            }
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    @Override
    @Nonnull
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (currentRecipe == null || !currentRecipe.hasValidInventory()) {
            return Collections.emptyList();
        }

        List<String> tooltip = new ArrayList<>();

        // Check if hovering over upgrades
        List<ItemStack> upgrades = currentRecipe.getInstalledUpgrades();
        if (!upgrades.isEmpty()) {
            int upgradeX = WIDTH - 4 - upgrades.size() * (UPGRADE_SIZE + 1);
            int upgradeY = UPGRADE_Y;

            if (mouseY >= upgradeY && mouseY < upgradeY + UPGRADE_SIZE) {
                for (int i = 0; i < upgrades.size(); i++) {
                    int x = upgradeX + i * (UPGRADE_SIZE + 1);
                    if (mouseX >= x && mouseX < x + UPGRADE_SIZE) {
                        ItemStack upgrade = upgrades.get(i);
                        List<String> itemTooltip = upgrade.getTooltip(
                            Minecraft.getMinecraft().player, TooltipFlags.NORMAL);
                        tooltip.addAll(itemTooltip);

                        return tooltip;
                    }
                }
            }
        }

        // Check if hovering over size row
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int sizeY = 2 + font.FONT_HEIGHT + 4;
        if (mouseY >= sizeY && mouseY < sizeY + font.FONT_HEIGHT) {
            NumberFormat format = NumberFormat.getInstance();
            tooltip.add(I18n.format("jei.cells.cellview.tooltip.size_full",
                format.format(currentRecipe.getUsedBytes()),
                format.format(currentRecipe.getTotalBytes())));

            // Add HD explanation if applicable
            if (currentRecipe.isHyperDensity()) {
                tooltip.add("");
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.hd_explanation"));
                tooltip.add("§d" + I18n.format("jei.cells.cellview.tooltip.hd_multiplier",
                    format.format(currentRecipe.getByteMultiplier() * 8)));
            }

            return tooltip;
        }

        // Check if hovering over types row
        int typesY = sizeY + font.FONT_HEIGHT + 2;
        if (mouseY >= typesY && mouseY < typesY + font.FONT_HEIGHT) {
            NumberFormat format = NumberFormat.getInstance();

            if (currentRecipe.hasEqualDistribution() && currentRecipe.getPerTypeLimit() > 0) {
                // Equal distribution active: explain how it works
                if (currentRecipe.isConfigurable()) {
                    // Built-in equal distribution for configurable cells
                    tooltip.add("§6" + I18n.format("jei.cells.cellview.tooltip.equal_distribution_builtin"));
                } else {
                    // Equal distribution from upgrade card
                    tooltip.add("§6" + I18n.format("jei.cells.cellview.tooltip.equal_distribution_upgrade"));
                }

                // Explain what equal distribution does
                tooltip.add("§b" + I18n.format("jei.cells.cellview.tooltip.equal_distribution_explain"));
                tooltip.add("");
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.per_type_limit",
                    format.format(currentRecipe.getPerTypeLimit())));

                // Show effective max types
                int effectiveMax = currentRecipe.getEqualDistributionLimit() > 0
                    ? currentRecipe.getEqualDistributionLimit()
                    : currentRecipe.getMaxTypes();
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.max_types_limit",
                    format.format(effectiveMax)));
            } else {
                // Standard cell: explain overhead
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.types_info"));
                tooltip.add("");
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.overhead_info",
                    format.format(currentRecipe.getBytesPerType())));
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.overhead_total",
                    format.format(currentRecipe.getOverheadBytes())));
            }

            return tooltip;
        }

        // Check if hovering over row 4 (cell-type specific or NBT)
        int row4Y = typesY + font.FONT_HEIGHT + 2;
        if (mouseY >= row4Y && mouseY < row4Y + font.FONT_HEIGHT) {
            if (currentRecipe.isCompacting()) {
                // Compacting cell: explain compression
                if (!currentRecipe.hasPartition()) {
                    tooltip.add(I18n.format("jei.cells.cellview.tooltip.partition_required"));
                } else if (!currentRecipe.isChainInitialized()) {
                    tooltip.add(I18n.format("jei.cells.cellview.tooltip.chain_pending_explain"));
                } else {
                    int tiersUp = currentRecipe.getTiersUp();
                    int tiersDown = currentRecipe.getTiersDown();
                    String tierStringUp = I18n.format("jei.cells.cellview." + (tiersUp == 1 ? "tier" : "tiers"));
                    String tierStringDown = I18n.format("jei.cells.cellview." + (tiersDown == 1 ? "tier" : "tiers"));

                    tooltip.add(I18n.format("jei.cells.cellview.tooltip.compression_explain"));
                    tooltip.add("");
                    tooltip.add("§a" + I18n.format("jei.cells.cellview.tooltip.tiers_up", tiersUp, tierStringUp));
                    tooltip.add("§b" + I18n.format("jei.cells.cellview.tooltip.tiers_down", tiersDown, tierStringDown));
                    if (currentRecipe.hasOreDictCard()) {
                        tooltip.add("");
                        tooltip.add(I18n.format("jei.cells.cellview.tooltip.oredict_enabled"));
                    }
                }
            } else {
                // NBT info for non-compacting cells
                tooltip.add(I18n.format("jei.cells.cellview.tooltip.nbt_info"));
                if (NBTSizeHelper.exceedsThreshold(currentRecipe.getNbtSize(),
                        NBTSizeHelper.kbToBytes(CellsConfig.nbtSizeWarningThresholdKB))) {
                    tooltip.add(I18n.format("tooltip.cells.nbt_size.warning"));
                }
            }

            return tooltip;
        }

        return Collections.emptyList();
    }

    /**
     * Called by MixinRecipeLayout AFTER items are rendered.
     * This ensures stack sizes appear on top of items instead of behind them.
     */
    @Override
    public void drawOverlay(Minecraft minecraft, int offsetX, int offsetY, int mouseX, int mouseY) {
        if (currentRecipe == null) return;

        int startIdx = currentPage * ITEMS_PER_PAGE;

        for (int i = 0; i < slotPositions.size() && (startIdx + i) < currentRecipe.getStoredStacks().size(); i++) {
            CellViewRecipe.StoredStackInfo info = currentRecipe.getStoredStacks().get(startIdx + i);
            Point pos = slotPositions.get(i);

            // Use appropriate renderer based on channel type
            if (isFluidChannel && info.stack instanceof IAEFluidStack) {
                IAEFluidStack fluidStack = (IAEFluidStack) info.stack;
                fluidStackSizeRenderer.renderStackSize(minecraft.fontRenderer, fluidStack,
                    offsetX + pos.x + 1, offsetY + pos.y + 1);
            } else if (isGasChannel) {
                // Gas stacks: use fluidStackSizeRenderer with a synthetic fluid stack for consistent formatting
                // The gas count is in mB (already raw units), need to display as "B" (buckets)
                AEItemStack aeStack = AEItemStack.fromItemStack(info.stack.asItemStackRepresentation());
                if (aeStack != null) {
                    // Divide by 1000 for mB to B conversion, consistent with how gases work
                    aeStack.setStackSize(info.count / 1000);
                    stackSizeRenderer.renderStackSize(minecraft.fontRenderer, aeStack,
                        offsetX + pos.x + 1, offsetY + pos.y + 1);
                }
            } else {
                // For items and essentia, use item stack representation
                AEItemStack aeStack = AEItemStack.fromItemStack(info.stack.asItemStackRepresentation());
                if (aeStack != null) {
                    aeStack.setStackSize(info.count);
                    stackSizeRenderer.renderStackSize(minecraft.fontRenderer, aeStack,
                        offsetX + pos.x + 1, offsetY + pos.y + 1);
                }
            }
        }
    }
}
