package com.cells.gui.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import com.cells.Tags;


/**
 * DiskTerminal layout and atlas coordinates used by the interface scanner.
 */
public final class ScannerGuiConstants {

    public static final int GUI_WIDTH = 208;
    public static final int ROW_HEIGHT = 18;
    public static final int CONTENT_START_Y = 18;
    public static final int CONTENT_RIGHT_EDGE = 180;
    public static final int GUI_INDENT = 22;
    public static final int HEADER_NAME_X = 42;
    public static final int EXPAND_ICON_X = 167;
    public static final int SLOTS_X = 38;
    public static final int SLOTS_PER_ROW = 9;
    public static final int TREE_LINE_X = 29;
    public static final int ATLAS_WIDTH = 64;
    public static final int ATLAS_HEIGHT = 64;
    public static final int COLOR_SEPARATOR = 0xFF606060;
    public static final int COLOR_TREE_LINE = 0xFF808080;
    public static final int COLOR_HOVER_HIGHLIGHT = 0x80FFFFFF;
    public static final int COLOR_STORAGE_HEADER_HOVER = 0x30FFFFFF;
    public static final int COLOR_TEXT_NORMAL = 0x404040;
    public static final int COLOR_TEXT_SECONDARY = 0x808080;

    public static final ResourceLocation ATLAS_TEXTURE = new ResourceLocation(
        Tags.MODID, "textures/guis/atlas.png");

    private ScannerGuiConstants() {}

    public enum Texture {
        BUTTON_GREEN(0, 0, 16, 16),
        BUTTON_GREEN_HOVER(0, 16, 16, 16),
        BUTTON_RED(16, 0, 16, 16),
        BUTTON_RED_HOVER(16, 16, 16, 16),
        MINI_SLOT(0, 32, 16, 16);

        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Texture(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Texture hovered() {
            switch (this) {
                case BUTTON_GREEN: return BUTTON_GREEN_HOVER;
                case BUTTON_RED: return BUTTON_RED_HOVER;
                default: return this;
            }
        }

        public void draw(int x, int y) {
            drawAtlasSprite(x, y, this);
        }
    }

    public static void drawAtlasSprite(int x, int y, Texture texture) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(ATLAS_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(x, y, texture.x, texture.y, texture.width, texture.height,
            texture.width, texture.height, ATLAS_WIDTH, ATLAS_HEIGHT);
    }
}
