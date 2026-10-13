// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Registers 3 toggle keybinds. Polled once per tick (no event spam).
 * Defaults: R = KillAura, G = Fullbright, O = AutoTotem.
 * Uses Mojang mappings: net.minecraft.client.KeyMapping.
 *
 * 1.21.11 ctor is KeyMapping(String, int keycode, Category) — keycode is a
 * plain int (InputConstants.KEY_*), category uses the predefined GAMEPLAY
 * bucket (custom Category.register exists but adds label-resolution risk;
 * GAMEPLAY groups the binds with zero extra API surface).
 */
public final class KeybindManager {
    private KeybindManager() {
    }

    public static KeyMapping killAuraKey;
    public static KeyMapping fullbrightKey;
    public static KeyMapping autoTotemKey;
    public static KeyMapping openMenuKey;

    // Pre-allocated array — avoids alloc in tick.
    public static KeyMapping[] all;

    public static void register() {
        killAuraKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.multifeature.killaura", InputConstants.KEY_R,
                KeyMapping.Category.GAMEPLAY));
        fullbrightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.multifeature.fullbright", InputConstants.KEY_G,
                KeyMapping.Category.GAMEPLAY));
        autoTotemKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.multifeature.autototem", InputConstants.KEY_O,
                KeyMapping.Category.GAMEPLAY));
        // Menu key (K): opens MultiFeatureScreen. /kapgui and /KVpp3La open
        // the same screen — keybind and commands are alternative entry points
        // to one GUI, kept in parallel on purpose. Vanilla Controls also lists
        // all four binds (same KeyMapping objects); that is expected.
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.multifeature.openmenu", InputConstants.KEY_K,
                KeyMapping.Category.GAMEPLAY));
        all = new KeyMapping[]{killAuraKey, fullbrightKey, autoTotemKey, openMenuKey};
    }
}
