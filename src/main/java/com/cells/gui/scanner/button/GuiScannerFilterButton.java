package com.cells.gui.scanner.button;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import net.minecraftforge.fml.common.Loader;

import com.cells.Cells;
import com.cells.network.sync.ResourceType;


/**
 * Resource filter with green (show) and red (hide) states.
 */
public class GuiScannerFilterButton extends BinaryButton {

    // 16px (item size) * 0.75 + 2px (padding) = 16px (button size)
    private static final int ICON_PADDING = 2;
    private static final float ICON_SCALE = 0.75F;

    public final ResourceType type;
    private final ItemStack icon;

    public GuiScannerFilterButton(int id, int x, int y, ResourceType type) {
        super(id, x, y, true);
        this.type = type;
        this.icon = getIcon(type);
    }

    @Override
    protected void drawForeground(Minecraft mc) {
        drawItem(mc, this.icon, ICON_PADDING, ICON_PADDING, ICON_SCALE);
    }

    @Override
    public List<String> getTooltip() {
        return Arrays.asList(I18n.format(this.type.getTranslationKey()),
            I18n.format("cells.interface_scanner.filter." + (get() ? "show" : "hide")));
    }

    private static ItemStack getIcon(ResourceType type) {
        switch (type) {
            case ITEM:
                return new ItemStack(Blocks.STONE);
            case FLUID:
                return new ItemStack(Items.BUCKET);
            case GAS:
                return getGasIcon();
            case ESSENTIA:
                return getEssentiaIcon();
            default:
                return ItemStack.EMPTY;
        }
    }

    private static ItemStack getEssentiaIcon() {
        // Try to get Thaumcraft Jar, fall back to glass bottle if not available
        if (!Loader.isModLoaded("thaumcraft")) return new ItemStack(Items.GLASS_BOTTLE);

        try {
            Class<?> thaumcraftItems = Class.forName("thaumcraft.api.items.ItemsTC");
            Object jarItem = thaumcraftItems.getField("phial").get(null);
            if (jarItem instanceof Item) return new ItemStack((Item) jarItem);
        } catch (ReflectiveOperationException e) {
            Cells.LOGGER.warn("Failed to get the Thaumcraft phial for the scanner filter", e);
        }

        return new ItemStack(Items.GLASS_BOTTLE);
    }

    private static ItemStack getGasIcon() {
        // Try to get Mekanism Gas Tank, fall back to bucket if not available
        if (!Loader.isModLoaded("mekanism")) return new ItemStack(Items.BUCKET);

        try {
            Class<?> mekanismBlocks = Class.forName("mekanism.common.MekanismBlocks");
            Object gasTankBlock = mekanismBlocks.getField("GasTank").get(null);
            if (gasTankBlock instanceof Block) return new ItemStack((Block) gasTankBlock);
        } catch (ReflectiveOperationException e) {
            Cells.LOGGER.warn("Failed to get the Mekanism gas tank for the scanner filter", e);
        }

        return new ItemStack(Items.BUCKET);
    }
}
