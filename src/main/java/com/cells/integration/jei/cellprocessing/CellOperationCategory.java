package com.cells.integration.jei.cellprocessing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.ICellWorkbenchItem;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;

import com.cells.Tags;
import com.cells.ItemRegistry;
import com.cells.config.CellsConfig;


/**
 * JEI category layout shared by cell actions that return several items.
 */
@SideOnly(Side.CLIENT)
public class CellOperationCategory implements IRecipeCategory<CellOperationRecipe> {

    public static final String DISASSEMBLY_UID = Tags.MODID + ":cell_disassembly";
    public static final String UPGRADE_UID = Tags.MODID + ":cell_upgrade";

    static final int SLOT_SIZE = 18;
    static final int MIN_GRID_ROWS = 3;
    static final int GRID_TOP = 4;
    static final int GRID_BOTTOM = 4;
    static final int FOOTER_GAP = 2;
    static final int FOOTER_HEIGHT = 9;
    static final int FOOTER_BOTTOM = 3;
    static final int SIDE_PADDING = 4;
    static final int GRID_GAP = 8;
    static final int ARROW_TEXTURE_X = 82;
    static final int ARROW_TEXTURE_Y = 128;
    static final int ARROW_WIDTH = 24;
    static final int ARROW_HEIGHT = 17;
    static final int HINT_SIZE = 16;
    static final int HINT_TEXTURE_SIZE = 32;
    static final int HINT_GAP = 2;
    static final int HINT_VERTICAL_GAP = 4;
    static final int VANILLA_TEXTURE_SIZE = 256;
    static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/slot.png");
    static final ResourceLocation SHIFT_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/shift.png");
    static final ResourceLocation RIGHT_CLICK_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/rightclick.png");

    private final String uid;
    private final String titleKey;
    private final boolean showsDisassemblyHints;
    private final int width;
    private final int gridHeight;
    private final int height;
    private final int arrowX;
    private final int hintX;
    private final int rightClickX;
    private final IDrawable background;
    private final IDrawable icon;

    public CellOperationCategory(IJeiHelpers helpers, String uid, String titleKey, boolean showsDisassemblyHints) {
        IGuiHelper guiHelper = helpers.getGuiHelper();

        this.uid = uid;
        this.titleKey = titleKey;
        this.showsDisassemblyHints = showsDisassemblyHints;
        int maxInputCount = getMaxInputCount(showsDisassemblyHints);
        int maxOutputCount = getMaxOutputCount(showsDisassemblyHints);
        int maxColumns = Math.max(getColumns(maxInputCount), getColumns(maxOutputCount));
        int gridRows = showsDisassemblyHints ? Math.max(MIN_GRID_ROWS, getRows(maxOutputCount)) : MIN_GRID_ROWS;
        int sideWidth = maxColumns * SLOT_SIZE;
        int maxFooterY = GRID_TOP + gridRows * SLOT_SIZE + FOOTER_GAP;

        this.width = 2 * SIDE_PADDING + 2 * sideWidth + 2 * GRID_GAP + ARROW_WIDTH;
        this.gridHeight = gridRows * SLOT_SIZE;
        // TODO: We might get the height tighter if separate max height with and without footer
        //       As in max(with footer) + footer < max(without footer) then we can use the smaller height
        this.height = showsDisassemblyHints
            ? maxFooterY + FOOTER_HEIGHT + FOOTER_BOTTOM
            : GRID_TOP + gridHeight + GRID_BOTTOM;
        this.arrowX = SIDE_PADDING + sideWidth + GRID_GAP;
        this.hintX = arrowX - (2 * HINT_SIZE + HINT_GAP - ARROW_WIDTH) / 2;
        this.rightClickX = hintX + HINT_SIZE + HINT_GAP;

        this.background = guiHelper.createBlankDrawable(width, height);
        // TODO: Should we show a crafting table icon instead
        this.icon = guiHelper.drawableBuilder(
            new ResourceLocation(Tags.MODID, "textures/items/cells/cell_preview.png"), 0, 0, 16, 16)
            .setTextureSize(16, 16)
            .build();
    }

    @Override
    @Nonnull
    public String getUid() {
        return uid;
    }

    @Override
    @Nonnull
    public String getTitle() {
        return I18n.format(titleKey);
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
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull CellOperationRecipe recipe,
                          @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        CellOperationRecipe.Layout layout = createLayout(recipe);
        recipe.setLayout(layout);

        int slot = 0;
        for (int index = 0; index < layout.getInputPositions().size(); index++) {
            Point position = layout.getInputPositions().get(index);
            itemStacks.init(slot, true, position.x, position.y);
            itemStacks.set(slot, recipe.getInputs().get(index));
            slot++;
        }

        for (int index = 0; index < layout.getOutputPositions().size(); index++) {
            Point position = layout.getOutputPositions().get(index);
            itemStacks.init(slot, false, position.x, position.y);
            itemStacks.set(slot, recipe.getOutputs().get(index));
            slot++;
        }
    }

    private CellOperationRecipe.Layout createLayout(CellOperationRecipe recipe) {
        List<Point> inputPositions = createGrid(recipe.getInputs().size(), true);
        List<Point> outputPositions = createGrid(recipe.getOutputs().size(), false);

        // Do not show warning for upgrades, as they cannot "have contents" (lol)
        boolean requiresEmptyCell = showsDisassemblyHints && !recipe.getInputs().isEmpty()
            && recipe.getInputs().get(0).getItem() instanceof ICellWorkbenchItem;

        int arrowY = GRID_TOP + (gridHeight - ARROW_HEIGHT) / 2;
        int hintY = arrowY - HINT_SIZE - HINT_VERTICAL_GAP;
        int footerY = Math.max(getGridBottom(inputPositions), getGridBottom(outputPositions)) + FOOTER_GAP;
        int contentTop = Math.min(Math.min(getGridTop(inputPositions), getGridTop(outputPositions)), arrowY);
        int contentBottom = Math.max(Math.max(getGridBottom(inputPositions), getGridBottom(outputPositions)),
            arrowY + ARROW_HEIGHT);

        if (showsDisassemblyHints) contentTop = Math.min(contentTop, hintY);
        if (requiresEmptyCell) contentBottom = footerY + FOOTER_HEIGHT;

        int verticalOffset = (height - (contentBottom - contentTop)) / 2 - contentTop;
        return new CellOperationRecipe.Layout(width, showsDisassemblyHints, requiresEmptyCell,
            shiftPoints(inputPositions, verticalOffset), shiftPoints(outputPositions, verticalOffset),
            arrowX, arrowY + verticalOffset, hintX, hintY + verticalOffset, rightClickX, footerY + verticalOffset);
    }

    private List<Point> createGrid(int count, boolean input) {
        if (count <= 0) return Collections.emptyList();

        int columns = getColumns(count);
        int rows = (count + columns - 1) / columns;
        int gridWidth = columns * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int gridX = input ? arrowX - GRID_GAP - gridWidth : arrowX + ARROW_WIDTH + GRID_GAP;
        int gridY = GRID_TOP + (this.gridHeight - gridHeight) / 2;
        List<Point> positions = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            int remaining = count - row * columns;
            int rowSlots = Math.min(columns, remaining);
            int rowX = gridX + (gridWidth - rowSlots * SLOT_SIZE) / 2;

            for (int column = 0; column < rowSlots; column++) {
                positions.add(new Point(rowX + column * SLOT_SIZE, gridY + row * SLOT_SIZE));
            }
        }

        return positions;
    }

    private static int getColumns(int count) {
        if (count <= 1) return 1;

        int columns = 2;
        while (count > columns * (columns + 1)) columns++;

        return columns;
    }

    private static int getGridBottom(List<Point> positions) {
        int bottom = GRID_TOP;
        for (Point position : positions) bottom = Math.max(bottom, position.y + SLOT_SIZE);
        return bottom;
    }

    private static int getGridTop(List<Point> positions) {
        int top = Integer.MAX_VALUE;
        for (Point position : positions) top = Math.min(top, position.y);
        return top == Integer.MAX_VALUE ? GRID_TOP : top;
    }

    private static List<Point> shiftPoints(List<Point> positions, int verticalOffset) {
        if (verticalOffset == 0) return positions;

        List<Point> shiftedPositions = new ArrayList<>(positions.size());
        for (Point position : positions) shiftedPositions.add(new Point(position.x, position.y + verticalOffset));
        return shiftedPositions;
    }

    private static int getRows(int count) {
        int columns = getColumns(count);
        return (count + columns - 1) / columns;
    }

    private static int getMaxInputCount(boolean showsDisassemblyHints) {
        return showsDisassemblyHints ? 1 : 2;
    }

    private static int getMaxOutputCount(boolean showsDisassemblyHints) {
        if (!showsDisassemblyHints) return 2;

        int maxOutputs = 1;
        for (ItemStack stack : CellJeiHelper.getAllDisassemblyItems()) {
            if (!CellJeiHelper.canDisassemble(stack)) continue;

            int outputCount = CellJeiHelper.getDisassemblyOutputs(stack).size() + getUpgradeSlotCapacity(stack);
            maxOutputs = Math.max(maxOutputs, outputCount);
        }

        return maxOutputs;
    }

    private static int getUpgradeSlotCapacity(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        if (stack.getItem() == ItemRegistry.COMPACTING_CELL) {
            return CellsConfig.general.compactingCellUpgradeSlots;
        }

        if (stack.getItem() == ItemRegistry.HYPER_DENSITY_CELL) {
            return CellsConfig.general.hdItemCellUpgradeSlots;
        }

        if (stack.getItem() == ItemRegistry.FLUID_HYPER_DENSITY_CELL) {
            return CellsConfig.general.hdFluidCellUpgradeSlots;
        }

        if (stack.getItem() == ItemRegistry.HYPER_DENSITY_COMPACTING_CELL) {
            return CellsConfig.general.hdCompactingCellUpgradeSlots;
        }

        if (stack.getItem() == ItemRegistry.CONFIGURABLE_CELL) {
            return CellsConfig.general.configurableCellUpgradeSlots;
        }

        return 0;
    }
}
