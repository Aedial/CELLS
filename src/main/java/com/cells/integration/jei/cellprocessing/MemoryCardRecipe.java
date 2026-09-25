package com.cells.integration.jei.cellprocessing;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.cells.client.KeyBindings;


/**
 * JEI data for a Memory Card transfer.
 * The card remains separate from the source slots, above the transfer row.
 */
public class MemoryCardRecipe extends CellOperationRecipe {

    static final String INTERFACE_FOOTER = "jei.cells.memory_card.copy_filters_and_upgrades";

    private final String footerKey;

    public MemoryCardRecipe(ItemStack memoryCard, List<List<ItemStack>> sources,
                            List<ItemStack> targets, String footerKey) {
        super(memoryCard, sources, Collections.singletonList(targets));
        this.footerKey = footerKey;
    }

    public String getFooter() {
        if (INTERFACE_FOOTER.equals(footerKey)) return I18n.format(footerKey, getFilterKey());

        return I18n.format(footerKey);
    }

    boolean matchesMemoryCard(ItemStack stack) {
        return sameItem(getHeaderInput(), stack);
    }

    boolean matchesInput(ItemStack stack) {
        if (matchesMemoryCard(stack)) return true;

        for (List<ItemStack> source : getInputLists()) {
            for (ItemStack sourceStack : source) {
                if (sameItem(sourceStack, stack)) return true;
            }
        }

        return false;
    }

    boolean matchesOutput(ItemStack stack) {
        for (List<ItemStack> target : getOutputLists()) {
            for (ItemStack targetStack : target) {
                if (sameItem(targetStack, stack)) return true;
            }
        }

        return false;
    }

    private static String getFilterKey() {
        return KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.isBound()
            ? KeyBindings.MEMORY_CARD_INCLUDE_FILTERS.getDisplayName()
            : I18n.format("cells.controls.key_not_set");
    }

    private static boolean sameItem(ItemStack left, ItemStack right) {
        return !left.isEmpty() && !right.isEmpty() && ItemStack.areItemsEqual(left, right);
    }
}
