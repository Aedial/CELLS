package com.cells.integration.jei.cellprocessing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.config.Constants;

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

    private static final int WIDTH = 218;
    private static final int SLOT_SIZE = 18;
    private static final int MIN_GRID_ROWS = 3;
    private static final int GRID_TOP = 4;
    private static final int GRID_BOTTOM = 4;
    private static final int FOOTER_GAP = 2;
    private static final int FOOTER_HEIGHT = 9;
    private static final int FOOTER_BOTTOM = 3;
    private static final int ARROW_X = 98;
    private static final int GRID_GAP = 8;
    private static final int HINT_SIZE = 16;
    private static final int HINT_TEXTURE_SIZE = 32;
    private static final int HINT_GAP = 2;
    private static final int HINT_X = ARROW_X - (2 * HINT_SIZE + HINT_GAP - 22) / 2;
    private static final int RIGHT_CLICK_X = HINT_X + HINT_SIZE + HINT_GAP;
    private static final ResourceLocation SHIFT_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/shift.png");
    private static final ResourceLocation RIGHT_CLICK_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/rightclick.png");

    private final String uid;
    private final String titleKey;
    private final boolean showsDisassemblyHints;
    private final int gridHeight;
    private final int height;
    private final int arrowY;
    private final int hintY;
    private final int footerY;
    private final IDrawable background;
    private final IDrawableStatic slotSprite;
    private final IDrawableStatic arrow;
    private final IDrawable icon;
    private final List<Point> inputPositions = new ArrayList<>();
    private final List<Point> outputPositions = new ArrayList<>();

    public CellOperationCategory(IJeiHelpers helpers, String uid, String titleKey, boolean showsDisassemblyHints) {
        IGuiHelper guiHelper = helpers.getGuiHelper();

        this.uid = uid;
        this.titleKey = titleKey;
        this.showsDisassemblyHints = showsDisassemblyHints;
        int gridRows = showsDisassemblyHints ? getDisassemblyGridRows() : MIN_GRID_ROWS;
        this.gridHeight = gridRows * SLOT_SIZE;
        this.footerY = GRID_TOP + gridHeight + FOOTER_GAP;
        this.height = showsDisassemblyHints
            ? footerY + FOOTER_HEIGHT + FOOTER_BOTTOM
            : GRID_TOP + gridHeight + GRID_BOTTOM;
        this.arrowY = (height - 15) / 2;
        this.hintY = arrowY - HINT_SIZE - 4;
        this.background = guiHelper.createBlankDrawable(WIDTH, height);
        this.slotSprite = guiHelper.drawableBuilder(
            new ResourceLocation(Tags.MODID, "textures/guis/slot.png"), 0, 0, SLOT_SIZE, SLOT_SIZE)
            .setTextureSize(SLOT_SIZE, SLOT_SIZE)
            .build();
        this.arrow = guiHelper.createDrawable(Constants.RECIPE_GUI_VANILLA, 60, 76, 22, 15);
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

        inputPositions.clear();
        inputPositions.addAll(createGrid(recipe.getInputs().size(), true));
        outputPositions.clear();
        outputPositions.addAll(createGrid(recipe.getOutputs().size(), false));

        int slot = 0;
        for (int index = 0; index < inputPositions.size(); index++) {
            Point position = inputPositions.get(index);
            itemStacks.init(slot, true, position.x, position.y);
            itemStacks.set(slot, recipe.getInputs().get(index));
            slot++;
        }

        for (int index = 0; index < outputPositions.size(); index++) {
            Point position = outputPositions.get(index);
            itemStacks.init(slot, false, position.x, position.y);
            itemStacks.set(slot, recipe.getOutputs().get(index));
            slot++;
        }
    }

    @Override
    public void drawExtras(@Nonnull Minecraft minecraft) {
        for (Point position : inputPositions) slotSprite.draw(minecraft, position.x, position.y);
        for (Point position : outputPositions) slotSprite.draw(minecraft, position.x, position.y);

        arrow.draw(minecraft, ARROW_X, arrowY);
        if (!showsDisassemblyHints) return;

        drawHint(minecraft, SHIFT_TEXTURE, HINT_X, hintY);
        drawHint(minecraft, RIGHT_CLICK_TEXTURE, RIGHT_CLICK_X, hintY);

        FontRenderer font = minecraft.fontRenderer;
        String text = I18n.format("jei.cells.disassembly.empty");
        font.drawString(text, (WIDTH - font.getStringWidth(text)) / 2, footerY, 0x000000);
    }

    @Override
    @Nonnull
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (!showsDisassemblyHints) return Collections.emptyList();

        if (isInside(mouseX, mouseY, HINT_X, hintY)) {
            return Collections.singletonList(I18n.format("jei.cells.disassembly.shift"));
        }

        if (isInside(mouseX, mouseY, RIGHT_CLICK_X, hintY)) {
            return Collections.singletonList(I18n.format("jei.cells.disassembly.right_click"));
        }

        return Collections.emptyList();
    }

    private List<Point> createGrid(int count, boolean input) {
        if (count <= 0) return Collections.emptyList();

        int columns = getColumns(count);
        int rows = (count + columns - 1) / columns;
        int gridWidth = columns * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int gridX = input ? ARROW_X - GRID_GAP - gridWidth : ARROW_X + 22 + GRID_GAP;
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
        if (count == 1) return 1;

        int columns = 2;
        while (count > columns * (columns + 1)) columns++;

        return columns;
    }

    private static int getDisassemblyGridRows() {
        int maximumUpgrades = 0;
        if (ItemRegistry.COMPACTING_CELL != null) {
            maximumUpgrades = CellsConfig.general.compactingCellUpgradeSlots;
        }

        if (ItemRegistry.HYPER_DENSITY_CELL != null) {
            maximumUpgrades = Math.max(maximumUpgrades, CellsConfig.general.hdItemCellUpgradeSlots);
        }

        if (ItemRegistry.FLUID_HYPER_DENSITY_CELL != null) {
            maximumUpgrades = Math.max(maximumUpgrades, CellsConfig.general.hdFluidCellUpgradeSlots);
        }

        if (ItemRegistry.HYPER_DENSITY_COMPACTING_CELL != null) {
            maximumUpgrades = Math.max(maximumUpgrades, CellsConfig.general.hdCompactingCellUpgradeSlots);
        }

        if (ItemRegistry.CONFIGURABLE_CELL != null) {
            maximumUpgrades = Math.max(maximumUpgrades, CellsConfig.general.configurableCellUpgradeSlots);
        }

        int outputs = maximumUpgrades + 2;
        int columns = getColumns(outputs);
        return Math.max(MIN_GRID_ROWS, (outputs + columns - 1) / columns);
    }

    private static void drawHint(Minecraft minecraft, ResourceLocation texture, int x, int y) {
        minecraft.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, HINT_TEXTURE_SIZE, HINT_TEXTURE_SIZE,
            HINT_SIZE, HINT_SIZE, HINT_TEXTURE_SIZE, HINT_TEXTURE_SIZE);
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }
}
