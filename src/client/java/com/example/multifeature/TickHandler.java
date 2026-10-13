// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.feature.AutoTotem;
import com.example.multifeature.feature.Fullbright;
import com.example.multifeature.feature.KillAura;
import com.example.multifeature.keybind.KeybindManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Single tick handler. One entry, plain calls, no alloc, no logging in hot path.
 */
public final class TickHandler {
    private TickHandler() {
    }

    public static void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        // Poll keybinds once. consumeClick() = edge-triggered, no repeat spam.
        while (KeybindManager.killAuraKey.consumeClick()) {
            ModConfig.INSTANCE.killAuraEnabled = !ModConfig.INSTANCE.killAuraEnabled;
            ModConfig.save();
            sendActionBar(client, "KillAura " + onOff(ModConfig.INSTANCE.killAuraEnabled));
        }
        while (KeybindManager.fullbrightKey.consumeClick()) {
            ModConfig.INSTANCE.fullbrightEnabled = !ModConfig.INSTANCE.fullbrightEnabled;
            ModConfig.save();
            sendActionBar(client, "Fullbright " + onOff(ModConfig.INSTANCE.fullbrightEnabled));
        }
        while (KeybindManager.autoTotemKey.consumeClick()) {
            ModConfig.INSTANCE.autoTotemEnabled = !ModConfig.INSTANCE.autoTotemEnabled;
            ModConfig.save();
            sendActionBar(client, "AutoTotem " + onOff(ModConfig.INSTANCE.autoTotemEnabled));
        }

        KillAura.tick(client);
        AutoTotem.tick(client);
        Fullbright.tick(client);
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }

    private static void sendActionBar(Minecraft client, String text) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(text), true);
        }
    }
}
