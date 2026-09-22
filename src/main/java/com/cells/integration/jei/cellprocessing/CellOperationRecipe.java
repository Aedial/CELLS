package com.cells.integration.jei.cellprocessing;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

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

    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private Layout layout;

    public CellOperationRecipe(List<ItemStack> inputs, List<ItemStack> outputs) {
        this.inputs = copyStacks(inputs);
        this.outputs = copyStacks(outputs);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputLists = new ArrayList<>();
        for (ItemStack input : inputs) inputLists.add(Collections.singletonList(input));

        ingredients.setInputLists(VanillaTypes.ITEM, inputLists);
        ingredients.setOutputs(VanillaTypes.ITEM, outputs);
    }

    @Override
    public void drawInfo(Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (layout == null) return;

        drawSlots(minecraft, layout.getInputPositions());
        drawSlots(minecraft, layout.getOutputPositions());
        drawArrow(minecraft, layout.getArrowX(), layout.getArrowY());
        if (!layout.showsDisassemblyHints()) return;

        drawHint(minecraft, CellOperationCategory.SHIFT_TEXTURE, layout.getHintX(), layout.getHintY());
        drawHint(minecraft, CellOperationCategory.RIGHT_CLICK_TEXTURE, layout.getRightClickX(), layout.getHintY());

        if (layout.requiresEmptyCell()) {
            FontRenderer font = minecraft.fontRenderer;
            String text = I18n.format("jei.cells.disassembly.empty");
            font.drawString(text, (layout.getWidth() - font.getStringWidth(text)) / 2, layout.getFooterY(), 0x000000);
        }
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (layout == null || !layout.showsDisassemblyHints()) return Collections.emptyList();

        if (isInside(mouseX, mouseY, layout.getHintX(), layout.getHintY())) {
            return Collections.singletonList(I18n.format("jei.cells.disassembly.shift"));
        }

        if (isInside(mouseX, mouseY, layout.getRightClickX(), layout.getHintY())) {
            return Collections.singletonList(I18n.format("jei.cells.disassembly.right_click"));
        }

        return Collections.emptyList();
    }

    public List<ItemStack> getInputs() {
        return inputs;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    void setLayout(Layout layout) {
        this.layout = layout;
    }

    private static void drawSlots(Minecraft minecraft, List<Point> positions) {
        minecraft.getTextureManager().bindTexture(CellOperationCategory.SLOT_TEXTURE);
        for (Point position : positions) {
            Gui.drawScaledCustomSizeModalRect(position.x, position.y, 0, 0,
                CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE,
                CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE,
                CellOperationCategory.SLOT_SIZE, CellOperationCategory.SLOT_SIZE);
        }
    }

    private static void drawArrow(Minecraft minecraft, int x, int y) {
        minecraft.getTextureManager().bindTexture(Constants.RECIPE_GUI_VANILLA);
        Gui.drawScaledCustomSizeModalRect(x, y,
            CellOperationCategory.ARROW_TEXTURE_X, CellOperationCategory.ARROW_TEXTURE_Y,
            CellOperationCategory.ARROW_WIDTH, CellOperationCategory.ARROW_HEIGHT,
            CellOperationCategory.ARROW_WIDTH, CellOperationCategory.ARROW_HEIGHT,
            CellOperationCategory.VANILLA_TEXTURE_SIZE, CellOperationCategory.VANILLA_TEXTURE_SIZE);
    }

    private static void drawHint(Minecraft minecraft, net.minecraft.util.ResourceLocation texture, int x, int y) {
        minecraft.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0,
            CellOperationCategory.HINT_TEXTURE_SIZE, CellOperationCategory.HINT_TEXTURE_SIZE,
            CellOperationCategory.HINT_SIZE, CellOperationCategory.HINT_SIZE,
            CellOperationCategory.HINT_TEXTURE_SIZE, CellOperationCategory.HINT_TEXTURE_SIZE);
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + CellOperationCategory.HINT_SIZE
            && mouseY >= y && mouseY < y + CellOperationCategory.HINT_SIZE;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) copies.add(stack.copy());
        }

        return Collections.unmodifiableList(copies);
    }

    static final class Layout {

        private final int width;
        private final boolean showsDisassemblyHints;
        private final boolean requiresEmptyCell;
        private final List<Point> inputPositions;
        private final List<Point> outputPositions;
        private final int arrowX;
        private final int arrowY;
        private final int hintX;
        private final int hintY;
        private final int rightClickX;
        private final int footerY;

        Layout(int width, boolean showsDisassemblyHints, boolean requiresEmptyCell,
               List<Point> inputPositions, List<Point> outputPositions,
               int arrowX, int arrowY, int hintX, int hintY, int rightClickX, int footerY) {
            this.width = width;
            this.showsDisassemblyHints = showsDisassemblyHints;
            this.requiresEmptyCell = requiresEmptyCell;
            this.inputPositions = Collections.unmodifiableList(new ArrayList<>(inputPositions));
            this.outputPositions = Collections.unmodifiableList(new ArrayList<>(outputPositions));
            this.arrowX = arrowX;
            this.arrowY = arrowY;
            this.hintX = hintX;
            this.hintY = hintY;
            this.rightClickX = rightClickX;
            this.footerY = footerY;
        }

        int getWidth() {
            return width;
        }

        boolean showsDisassemblyHints() {
            return showsDisassemblyHints;
        }

        boolean requiresEmptyCell() {
            return requiresEmptyCell;
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

        int getHintX() {
            return hintX;
        }

        int getHintY() {
            return hintY;
        }

        int getRightClickX() {
            return rightClickX;
        }

        int getFooterY() {
            return footerY;
        }
    }
}
