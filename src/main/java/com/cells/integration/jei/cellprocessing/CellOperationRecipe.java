package com.cells.integration.jei.cellprocessing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.config.Constants;


/**
 * JEI data for a cell action with any number of item inputs and outputs.
 * Each recipe is one panel, inside the broader category (hence the layout
 * being stored here instead of in the category).
 */
public class CellOperationRecipe implements IRecipeWrapper {

    static final int DEFAULT_FOOTER_COLOR = 0x000000;
    static final int WARNING_FOOTER_COLOR = 0xCC3C00;

    private final ItemStack headerInput;
    private final List<ItemStack> inputs;
    private final List<List<ItemStack>> inputLists;
    private final List<ItemStack> outputs;
    private final List<List<ItemStack>> outputLists;
    private Layout layout;

    public CellOperationRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {
        this(ItemStack.EMPTY, singleStacks(inputs), singleStacks(outputs));
    }

    protected CellOperationRecipe(ItemStack headerInput, Collection<List<ItemStack>> inputs,
                                  Collection<List<ItemStack>> outputs) {
        this.headerInput = headerInput.isEmpty() ? ItemStack.EMPTY : headerInput.copy();
        this.inputLists = copyStackLists(inputs);
        this.inputs = firstStacks(this.inputLists);
        this.outputLists = copyStackLists(outputs);
        this.outputs = firstStacks(this.outputLists);
    }

    static CellOperationRecipe withAlternatingInputs(List<List<ItemStack>> inputs,
                                                     List<ItemStack> outputs) {
        return new CellOperationRecipe(ItemStack.EMPTY, inputs, singleStacks(outputs));
    }

    static CellOperationRecipe withAlternatingOutputs(List<List<ItemStack>> inputs,
                                                      List<List<ItemStack>> outputs) {
        return new CellOperationRecipe(ItemStack.EMPTY, inputs, outputs);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputLists = new ArrayList<>();
        if (!headerInput.isEmpty()) inputLists.add(Collections.singletonList(headerInput));
        inputLists.addAll(this.inputLists);

        ingredients.setInputLists(VanillaTypes.ITEM, inputLists);
        ingredients.setOutputLists(VanillaTypes.ITEM, outputLists);
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (layout == null) return;

        int verticalOffset = layout.getVerticalOffset();
        Point headerPosition = layout.getHeaderPosition();
        if (headerPosition != null) drawSlot(minecraft, headerPosition, verticalOffset);
        drawSlots(minecraft, layout.getInputPositions(), verticalOffset);
        drawSlots(minecraft, layout.getOutputPositions(), verticalOffset);
        drawArrow(minecraft, layout.getArrowX(), layout.getArrowY(), verticalOffset);

        List<Point> hintPositions = layout.getHintPositions();
        List<CellOperationCategory.Hint> hints = layout.getHints();
        for (int i = 0; i < hintPositions.size(); i++) {
            Point hintPosition = hintPositions.get(i);
            CellOperationCategory.Hint hint = hints.get(i);
            drawHint(minecraft, hint.getTexture(), hintPosition.x, hintPosition.y, verticalOffset);
        }

        String footer = layout.getFooter();
        if (footer != null && !footer.isEmpty()) {
            FontRenderer font = minecraft.fontRenderer;
            int footerY = layout.getFooterY() + verticalOffset;
            List<String> lines = font.listFormattedStringToWidth(footer, layout.getWidth() + 2);
            for (String line : lines) {
                font.drawString(line, (layout.getWidth() - font.getStringWidth(line)) / 2,
                    footerY, layout.getFooterColor());
                footerY += font.FONT_HEIGHT + 1;
            }
        }
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (layout == null) return Collections.emptyList();

        List<Point> hintPositions = layout.getHintPositions();
        List<CellOperationCategory.Hint> hints = layout.getHints();
        for (int i = 0; i < hintPositions.size(); i++) {
            Point hintPosition = hintPositions.get(i);
            CellOperationCategory.Hint hint = hints.get(i);
            int hintY = hintPosition.y + layout.getVerticalOffset();

            if (mouseX >= hintPosition.x && mouseX < hintPosition.x + CellOperationCategory.HINT_SIZE &&
                mouseY >= hintY && mouseY < hintY + CellOperationCategory.HINT_SIZE) {
                return Collections.singletonList(I18n.format(hint.getTranslationKey()));
            }
        }

        return Collections.emptyList();
    }

    public List<ItemStack> getInputs() {
        return inputs;
    }

    public List<List<ItemStack>> getInputLists() {
        return inputLists;
    }

    public ItemStack getHeaderInput() {
        return headerInput;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    public List<List<ItemStack>> getOutputLists() {
        return outputLists;
    }

    private static List<List<ItemStack>> singleStacks(List<ItemStack> stacks) {
        List<List<ItemStack>> stackLists = new ArrayList<>();
        for (ItemStack stack : stacks) stackLists.add(Collections.singletonList(stack));
        return stackLists;
    }

    void setLayout(Layout layout) {
        this.layout = layout;
    }

    private static void drawSlots(Minecraft minecraft, List<Point> positions, int verticalOffset) {
        for (Point position : positions) drawSlot(minecraft, position, verticalOffset);
    }

    private static void drawSlot(Minecraft minecraft, Point position, int verticalOffset) {
        minecraft.getTextureManager().bindTexture(CellOperationCategory.SLOT_TEXTURE);
        Gui.drawScaledCustomSizeModalRect(position.x, position.y + verticalOffset,
            0, 0,
            CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE,
            CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE,
            CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE);
    }

    private static void drawArrow(Minecraft minecraft, int x, int y, int verticalOffset) {
        minecraft.getTextureManager().bindTexture(Constants.RECIPE_GUI_VANILLA);
        Gui.drawScaledCustomSizeModalRect(x, y + verticalOffset,
            CellOperationCategory.ARROW_TEXTURE_X, CellOperationCategory.ARROW_TEXTURE_Y,
            CellOperationCategory.ARROW_WIDTH, CellOperationCategory.ARROW_HEIGHT,
            CellOperationCategory.ARROW_WIDTH, CellOperationCategory.ARROW_HEIGHT,
            CellOperationCategory.VANILLA_TEXTURE_SIZE, CellOperationCategory.VANILLA_TEXTURE_SIZE);
    }

    private static void drawHint(Minecraft minecraft, ResourceLocation texture, int x, int y, int verticalOffset) {
        minecraft.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x, y + verticalOffset,
            0, 0,
            CellOperationCategory.HINT_TEXTURE_SIZE, CellOperationCategory.HINT_TEXTURE_SIZE,
            CellOperationCategory.HINT_SIZE, CellOperationCategory.HINT_SIZE,
            CellOperationCategory.HINT_TEXTURE_SIZE, CellOperationCategory.HINT_TEXTURE_SIZE);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) copies.add(stack.copy());
        }

        return Collections.unmodifiableList(copies);
    }

    private static List<List<ItemStack>> copyStackLists(Collection<List<ItemStack>> stacks) {
        List<List<ItemStack>> copies = new ArrayList<>();
        for (List<ItemStack> stackList : stacks) {
            List<ItemStack> stackCopies = copyStacks(stackList);
            if (!stackCopies.isEmpty()) copies.add(stackCopies);
        }

        return Collections.unmodifiableList(copies);
    }

    private static List<ItemStack> firstStacks(List<List<ItemStack>> stacks) {
        List<ItemStack> firstStacks = new ArrayList<>();
        for (List<ItemStack> stackList : stacks) firstStacks.add(stackList.get(0));

        return Collections.unmodifiableList(firstStacks);
    }

    static final class Layout {

        private final int width;
        private final int verticalOffset;
        private final List<CellOperationCategory.Hint> hints;
        private final List<Point> hintPositions;
        private final Point headerPosition;
        private final List<Point> inputPositions;
        private final List<Point> outputPositions;
        private final int arrowX;
        private final int arrowY;
        private final String footer;
        private final int footerColor;
        private final int footerY;

        Layout(int width, List<CellOperationCategory.Hint> hints, List<Point> hintPositions,
            int verticalOffset, Point headerPosition, List<Point> inputPositions,
            List<Point> outputPositions, int arrowX, int arrowY, String footer,
            int footerColor, int footerY) {

            this.width = width;
            this.verticalOffset = verticalOffset;
            this.hints = Collections.unmodifiableList(new ArrayList<>(hints));
            this.hintPositions = Collections.unmodifiableList(new ArrayList<>(hintPositions));
            this.headerPosition = headerPosition == null ? null : new Point(headerPosition);
            this.inputPositions = Collections.unmodifiableList(new ArrayList<>(inputPositions));
            this.outputPositions = Collections.unmodifiableList(new ArrayList<>(outputPositions));
            this.arrowX = arrowX;
            this.arrowY = arrowY;
            this.footer = footer;
            this.footerColor = footerColor;
            this.footerY = footerY;
        }

        int getWidth() {
            return width;
        }

        int getVerticalOffset() {
            return verticalOffset;
        }

        List<CellOperationCategory.Hint> getHints() {
            return hints;
        }

        List<Point> getHintPositions() {
            return hintPositions;
        }

        Point getHeaderPosition() {
            return headerPosition;
        }

        List<Point> getInputPositions() {
            return inputPositions;
        }

        List<Point> getOutputPositions() {
            return outputPositions;
        }

        int getArrowX() {
            return arrowX;
        }

        int getArrowY() {
            return arrowY;
        }

        String getFooter() {
            return footer;
        }

        int getFooterColor() {
            return footerColor;
        }

        int getFooterY() {
            return footerY;
        }
    }
}
