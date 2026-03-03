package com.cells.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;


/**
 * Keybindings for CELLS mod.
 * Used in the Import Interface GUI for quick operations.
 */
public enum KeyBindings {

    /**
     * Save filters along with settings when using memory card.
     * Default: Ctrl (modifier key, used with right-click).
     * Uses UNIVERSAL context so it works both in-game and in GUIs.
     */
    MEMORY_CARD_INCLUDE_FILTERS(new KeyBinding(
        "key.cells.memory_card_include_filters.desc",
        KeyConflictContext.UNIVERSAL,
        KeyModifier.NONE,
        Keyboard.KEY_LCONTROL,
        "key.cells.category"
    )),

    /**
     * Quick-add item under cursor to the first free filter slot.
     */
    QUICK_ADD_TO_FILTER(new KeyBinding(
        "key.cells.quick_add_to_filter.desc",
        KeyConflictContext.GUI,
        Keyboard.KEY_NONE,
        "key.cells.category"
    ));

    private final KeyBinding keyBinding;

    KeyBindings(KeyBinding keyBinding) {
        this.keyBinding = keyBinding;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }

    /**
     * Check if this keybinding is pressed (matches the given keycode).
     */
    public boolean isActiveAndMatches(int keyCode) {
        return keyBinding.isActiveAndMatches(keyCode);
    }

    /**
     * Check if this keybinding's key is currently held down.
     * Uses Keyboard.isKeyDown for real-time key state checking.
     */
    public boolean isKeyDown() {
        int keyCode = keyBinding.getKeyCode();
        if (keyCode == Keyboard.KEY_NONE) return false;

        return Keyboard.isKeyDown(keyCode);
    }

    /**
     * Check if this keybinding is bound (not NONE).
     */
    public boolean isBound() {
        return keyBinding.getKeyCode() != Keyboard.KEY_NONE;
    }

    /**
     * Get the display name for this keybinding.
     */
    public String getDisplayName() {
        return keyBinding.getDisplayName();
    }

    /**
     * Register all keybindings with Forge.
     */
    public static void registerAll() {
        for (KeyBindings kb : values()) {
            ClientRegistry.registerKeyBinding(kb.getKeyBinding());
        }
    }
}
