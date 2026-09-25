package com.cells.integration.jei.cellprocessing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.storage.ICellWorkbenchItem;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;

import com.cells.Tags;


/**
 * JEI category layout shared by cell actions that return several items.
 */
@SideOnly(Side.CLIENT)
public class CellOperationCategory implements IRecipeCategory<CellOperationRecipe> {

    static final ResourceLocation SHIFT_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/shift.png");
    static final ResourceLocation RIGHT_CLICK_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/guis/rightclick.png");
    static final ResourceLocation CELL_PREVIEW_TEXTURE = new ResourceLocation(Tags.MODID,
        "textures/items/cells/cell_preview.png");

    public static enum Hint {
        SHIFT(SHIFT_TEXTURE, "tooltip.cells.shift"),
        RIGHT_CLICK(RIGHT_CLICK_TEXTURE, "tooltip.cells.right_click");

        private final ResourceLocation texture;
        private final String translationKey;

        Hint(ResourceLocation texture, String translationKey) {
            this.texture = texture;
            this.translationKey = translationKey;
        }

        public ResourceLocation getTexture() {
            return texture;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    public static enum OperationType {
        DISASSEMBLY(
            CELL_PREVIEW_TEXTURE,
            Arrays.asList(Hint.SHIFT, Hint.RIGHT_CLICK),
            CellOperationCategory::getDisassemblyFooter),
        UPGRADE(
            new ItemStack(Blocks.CRAFTING_TABLE),
            Collections.emptyList(),
            null),
        WORKBENCH(
            ItemStack.EMPTY,
            Collections.emptyList(),
            (CellOperationRecipe recipe) -> I18n.format("jei.cells.workbench.footer")),
        MEMORY_CARD(
            ItemStack.EMPTY,
            Arrays.asList(Hint.SHIFT, Hint.RIGHT_CLICK),
            CellOperationCategory::getMemoryCardFooter);

        @Nullable
        private final ResourceLocation iconTexture;
        @Nullable
        private final ItemStack iconStack;

        private final List<Hint> hints;
        @Nullable
        private final Function<CellOperationRecipe, String> footerDispatch;

        private OperationType(@Nullable ResourceLocation iconTexture, @Nullable ItemStack iconStack,
                              List<Hint> hints, @Nullable Function<CellOperationRecipe, String> footerDispatch) {
            this.iconTexture = iconTexture;
            this.iconStack = iconStack;
            this.hints = hints;
            this.footerDispatch = footerDispatch;
        }

        OperationType(ResourceLocation iconTexture, List<Hint> hints,
                      Function<CellOperationRecipe, String> footerDispatch) {
            this(iconTexture, null, hints, footerDispatch);
        }

        OperationType(ItemStack iconStack, List<Hint> hints,
                Function<CellOperationRecipe, String> footerDispatch) {
            this(null, iconStack, hints, footerDispatch);
        }

        OperationType(List<Hint> hints, Function<CellOperationRecipe, String> footerDispatch) {
            this(null, null, hints, footerDispatch);
        }

        public List<Hint> getHints() {
            return hints;
        }

        public boolean showsHints() {
            return !hints.isEmpty();
        }

        public String getFooter(CellOperationRecipe recipe) {
            if (footerDispatch != null) return footerDispatch.apply(recipe);

            return "";
        }

        public boolean hasFooter() {
            return footerDispatch != null;
        }

        public int getFooterColor() {
            return this == DISASSEMBLY || this == WORKBENCH
                ? CellOperationRecipe.WARNING_FOOTER_COLOR
                : CellOperationRecipe.DEFAULT_FOOTER_COLOR;
        }

        public IDrawable getIcon(IGuiHelper guiHelper) {
            ItemStack iconStack = this.iconStack;
            if (this == WORKBENCH) {
                iconStack = AEApi.instance().definitions().blocks().cellWorkbench()
                    .maybeStack(1).orElse(ItemStack.EMPTY);
            } else if (this == MEMORY_CARD) {
                iconStack = AEApi.instance().definitions().items().memoryCard()
                    .maybeStack(1).orElse(ItemStack.EMPTY);
            }

            if (iconTexture != null) {
                return guiHelper.drawableBuilder(iconTexture, 0, 0, 16, 16)
                                .setTextureSize(16, 16)
                                .build();
            } else if (iconStack != null && !iconStack.isEmpty()) {
                return guiHelper.createDrawableIngredient(iconStack);
            }

            return null;
        }
    }

    public static final String DISASSEMBLY_UID = Tags.MODID + ":cell_disassembly";
    public static final String UPGRADE_UID = Tags.MODID + ":cell_upgrade";
    public static final String WORKBENCH_UID = Tags.MODID + ":cell_workbench_upgrade";
    public static final String MEMORY_CARD_UID = Tags.MODID + ":memory_card";

    static final int SLOT_SIZE = 18;
    static final int MIN_GRID_ROWS_WITH_HINTS = 3;
    static final int GRID_TOP = 4;
    static final int HEADER_GAP = 4;
    static final int GRID_BOTTOM = 4;
    static final int FOOTER_GAP = 2;
    static final int FOOTER_HEIGHT = 9;
    static final int FOOTER_BOTTOM = 3;
    static final int CONTENT_PADDING = 2;
    static final int SIDE_PADDING = 4;
    static final int GRID_GAP = 8;
    static final int ARROW_TEXTURE_X = 24;
    static final int ARROW_TEXTURE_Y = 132;
    static final int ARROW_WIDTH = 24;
    static final int ARROW_HEIGHT = 17;
    static final int HINT_SIZE = 16;
    static final int HINT_TEXTURE_SIZE = 32;
    static final int HINT_GAP = 2;
    static final int HINT_VERTICAL_GAP = 4;
    static final int VANILLA_TEXTURE_SIZE = 256;
    static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/slot.png");

    private final String uid;
    private final String titleKey;
    private final OperationType operationType;
    private final int width;
    private int gridHeight;
    private int height;
    private final int arrowX;
    private final IGuiHelper guiHelper;
    private IDrawable background;
    private final IDrawable icon;

    public CellOperationCategory(IJeiHelpers helpers, String uid, String titleKey, OperationType operationType) {
        this.guiHelper = helpers.getGuiHelper();

        this.uid = uid;
        this.titleKey = titleKey;
        this.operationType = operationType;

        int maxStackCount = getMaxStackCount(operationType);
        int maxColumns = getColumns(maxStackCount);
        int gridRows = getRows(maxStackCount);
        if (operationType.showsHints()) gridRows = Math.max(MIN_GRID_ROWS_WITH_HINTS, gridRows);

        int sideWidth = maxColumns * SLOT_SIZE;
        int gridTop = getGridTop(operationType);
        int maxFooterY = gridTop + gridRows * SLOT_SIZE + FOOTER_GAP;

        this.width = 2 * SIDE_PADDING + 2 * sideWidth + 2 * GRID_GAP + ARROW_WIDTH;
        this.gridHeight = gridRows * SLOT_SIZE;
        this.height = this.operationType.hasFooter()
            ? maxFooterY + FOOTER_HEIGHT + FOOTER_BOTTOM
            : gridTop + gridHeight + GRID_BOTTOM;
        this.arrowX = SIDE_PADDING + sideWidth + GRID_GAP;

        this.background = guiHelper.createBlankDrawable(width, height);
        this.icon = operationType.getIcon(guiHelper);
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

    private static String getDisassemblyFooter(CellOperationRecipe recipe) {
        // Do not show warning for upgrades, as they cannot "have contents" (lol)
        if (!recipe.getInputs().isEmpty()
            && recipe.getInputs().get(0).getItem() instanceof ICellWorkbenchItem) {
            return I18n.format("jei.cells.disassembly.footer");
        }

        return ""; 
    }

    private static String getMemoryCardFooter(CellOperationRecipe recipe) {
        if (!(recipe instanceof MemoryCardRecipe)) return "";

        MemoryCardRecipe memoryCardRecipe = (MemoryCardRecipe) recipe;
        return memoryCardRecipe.getFooter();
    }

    public void setMatchingRecipes(List<? extends CellOperationRecipe> recipes) {
        if (operationType == OperationType.UPGRADE || recipes.isEmpty()) return;

        int maxRows = 1;
        for (CellOperationRecipe recipe : recipes) {
            maxRows = Math.max(maxRows, getRows(recipe.getInputs().size()));
            maxRows = Math.max(maxRows, getRows(recipe.getOutputLists().size()));
        }

        this.gridHeight = maxRows * SLOT_SIZE;

        int maxFooterHeight = 0;
        int maxNoFooterHeight = 0;
        for (CellOperationRecipe recipe : recipes) {
            boolean recipeRequiresFooter = !operationType.getFooter(recipe).isEmpty();
            int contentHeight = getContentHeight(recipe, recipeRequiresFooter);
            if (recipeRequiresFooter) {
                maxFooterHeight = Math.max(maxFooterHeight, contentHeight + FOOTER_BOTTOM + CONTENT_PADDING);
            } else {
                maxNoFooterHeight = Math.max(maxNoFooterHeight, contentHeight + GRID_BOTTOM + CONTENT_PADDING);
            }
        }

        this.height = Math.max(maxFooterHeight, maxNoFooterHeight);
        this.background = guiHelper.createBlankDrawable(width, height);
    }

    @Override
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull CellOperationRecipe recipe,
                          @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        CellOperationRecipe.Layout layout = createLayout(recipe);
        recipe.setLayout(layout);

        int verticalOffset = layout.getVerticalOffset();

        int slot = 0;
        Point headerPosition = layout.getHeaderPosition();
        if (headerPosition != null) {
            itemStacks.init(slot, true, headerPosition.x, headerPosition.y + verticalOffset);
            itemStacks.set(slot, recipe.getHeaderInput());
            slot++;
        }

        for (int index = 0; index < layout.getInputPositions().size(); index++) {
            Point position = layout.getInputPositions().get(index);
            itemStacks.init(slot, true, position.x, position.y + verticalOffset);
            itemStacks.set(slot, recipe.getInputLists().get(index));
            slot++;
        }

        for (int index = 0; index < layout.getOutputPositions().size(); index++) {
            Point position = layout.getOutputPositions().get(index);
            itemStacks.init(slot, false, position.x, position.y + verticalOffset);
            itemStacks.set(slot, recipe.getOutputLists().get(index));
            slot++;
        }
    }

    private CellOperationRecipe.Layout createLayout(CellOperationRecipe recipe) {
        int gridTop = getGridTop(operationType);
        List<Point> inputPositions = createGrid(recipe.getInputs().size(), true, gridTop);
        List<Point> outputPositions = createGrid(recipe.getOutputLists().size(), false, gridTop);

        int arrowY = gridTop + (gridHeight - ARROW_HEIGHT) / 2;
        int hintY = arrowY - HINT_SIZE - HINT_VERTICAL_GAP;
        int contentBottom = Math.max(getGridBottom(inputPositions, gridTop),
                                     getGridBottom(outputPositions, gridTop));
        int footerY = contentBottom + FOOTER_GAP;
        int contentTop = Math.min(getGridTop(inputPositions, gridTop), getGridTop(outputPositions, gridTop));
        Point headerPosition = !recipe.getHeaderInput().isEmpty() ? new Point((width - SLOT_SIZE) / 2, GRID_TOP) : null;

        if (operationType.showsHints()) contentTop = Math.min(contentTop, hintY);
        if (headerPosition != null) contentTop = Math.min(contentTop, headerPosition.y);

        String footer = operationType.getFooter(recipe);
        if (!footer.isEmpty()) contentBottom = footerY + FOOTER_HEIGHT;

        List<Hint> hints = operationType.getHints();
        List<Point> hintPositions = new ArrayList<>();
        if (!hints.isEmpty()) {
            int middleX = arrowX + CellOperationCategory.ARROW_WIDTH / 2;
            int hintSize = hints.size() * CellOperationCategory.HINT_SIZE;
            int hintGap = (hints.size() - 1) * CellOperationCategory.HINT_GAP;
            int hintX = middleX - (hintSize + hintGap) / 2;

            for (int i = 0; i < hints.size(); i++) {
                hintPositions.add(new Point(hintX, hintY));
                hintX += CellOperationCategory.HINT_SIZE + CellOperationCategory.HINT_GAP;
            }
        }

        int verticalOffset = (height - (contentBottom - contentTop)) / 2 - contentTop;
        return new CellOperationRecipe.Layout(width, hints, hintPositions,
            verticalOffset, headerPosition, inputPositions, outputPositions,
            arrowX, arrowY, footer, operationType.getFooterColor(), footerY);
    }

    private int getContentHeight(CellOperationRecipe recipe, boolean requiresFooter) {
        int gridTop = getGridTop(operationType);

        List<Point> inputPositions = createGrid(recipe.getInputs().size(), true, gridTop);
        List<Point> outputPositions = createGrid(recipe.getOutputLists().size(), false, gridTop);
        int contentTop = Math.min(getGridTop(inputPositions, gridTop), getGridTop(outputPositions, gridTop));
        int contentBottom = Math.max(getGridBottom(inputPositions, gridTop),
                                     getGridBottom(outputPositions, gridTop));

        if (operationType.showsHints()) {
            int arrowY = gridTop + (gridHeight - ARROW_HEIGHT) / 2;
            contentTop = Math.min(contentTop, arrowY - HINT_SIZE - HINT_VERTICAL_GAP);
        }

        if (!recipe.getHeaderInput().isEmpty()) contentTop = Math.min(contentTop, GRID_TOP);
        if (requiresFooter) contentBottom += FOOTER_GAP + FOOTER_HEIGHT;
        return contentBottom - contentTop;
    }

    private List<Point> createGrid(int count, boolean input, int gridTop) {
        if (count <= 0) return Collections.emptyList();

        int columns = getColumns(count);
        int rows = (count + columns - 1) / columns;
        int gridWidth = columns * SLOT_SIZE;
        int gridHeight = rows * SLOT_SIZE;
        int gridX = input ? arrowX - GRID_GAP - gridWidth : arrowX + ARROW_WIDTH + GRID_GAP;
        int gridY = gridTop + (this.gridHeight - gridHeight) / 2;
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

    private static int getGridBottom(List<Point> positions, int gridTop) {
        int bottom = gridTop;
        for (Point position : positions) bottom = Math.max(bottom, position.y + SLOT_SIZE);
        return bottom;
    }

    private static int getGridTop(List<Point> positions, int gridTop) {
        int top = Integer.MAX_VALUE;
        for (Point position : positions) top = Math.min(top, position.y);
        return top == Integer.MAX_VALUE ? gridTop : top;
    }

    private static int getGridTop(OperationType operationType) {
        if (operationType == OperationType.MEMORY_CARD) return GRID_TOP + SLOT_SIZE + HEADER_GAP;

        return GRID_TOP;
    }

    private static int getRows(int count) {
        int columns = getColumns(count);
        return (count + columns - 1) / columns;
    }

    private static int getMaxStackCount(OperationType operationType) {
        if (operationType == OperationType.WORKBENCH) {
            return CellWorkbenchUpgradeRegistryPlugin.getMaxWorkbenchStackCount();
        }

        if (operationType == OperationType.UPGRADE) return 2;
        if (operationType == OperationType.MEMORY_CARD) return 9;

        // TODO: Refactor it into a separate method for clarity
        int maxStacks = 1;
        for (ItemStack stack : CellJeiHelper.getAllDisassemblyItems()) {
            if (!CellJeiHelper.canDisassemble(stack)) continue;

            maxStacks = Math.max(maxStacks, CellJeiHelper.getDisassemblyOutputs(stack).size());
        }

        return maxStacks;
    }
}
