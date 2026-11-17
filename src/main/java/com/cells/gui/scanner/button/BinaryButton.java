package com.cells.gui.scanner.button;

import com.cells.gui.scanner.ScannerGuiConstants.Texture;


/**
 * Shared boolean state for buttons with green true and red false backgrounds.
 */
public abstract class BinaryButton extends GuiAtlasButton {

    private boolean value;

    protected BinaryButton(int id, int x, int y, boolean value) {
        super(id, x, y, value ? Texture.BUTTON_GREEN : Texture.BUTTON_RED);
        this.value = value;
    }

    public boolean get() {
        return this.value;
    }

    public void set(boolean value) {
        setTexture(value ? Texture.BUTTON_GREEN : Texture.BUTTON_RED);
        this.value = value;
    }
}
