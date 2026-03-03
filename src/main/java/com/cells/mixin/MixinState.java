package com.cells.mixin;


/**
 * Tracks whether CELLS mixins have been loaded by a mixin loader.
 * <p>
 * When a mixin loader (e.g., MixinBooter) invokes our mixin plugin,
 * {@link #markMixinsEnabled()} is called. Cell items check
 * {@link #areMixinsEnabled()} to determine their max stack size:
 * <ul>
 *   <li>Mixins enabled: cells can stack to 64 (mixins fix slot limits)</li>
 *   <li>Mixins disabled: cells stack to 1 (prevents duplication exploits)</li>
 * </ul>
 */
public final class MixinState {

    private static boolean mixinsEnabled = false;

    private MixinState() {}

    /**
     * Called by the mixin plugin when mixins are successfully registered.
     */
    public static void markMixinsEnabled() {
        mixinsEnabled = true;
    }

    /**
     * @return true if CELLS mixins have been loaded by a mixin loader
     */
    public static boolean areMixinsEnabled() {
        return mixinsEnabled;
    }
}
