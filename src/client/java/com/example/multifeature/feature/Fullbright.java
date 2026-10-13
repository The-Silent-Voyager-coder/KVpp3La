// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.feature;

import com.example.multifeature.config.ModConfig;
import net.minecraft.client.Minecraft;

/**
 * Fullbright: flag holder + gamma tick. Mixin (LightmapMixin) covers shader path.
 * Tick runs rarely (every 20 ticks) — gamma set is cheap, avoids per-tick churn.
 */
public final class Fullbright {
    private Fullbright() {
    }

    private static int tickCounter;
    private static double vanillaGamma = -1.0;

    public static boolean isEnabled() {
        return ModConfig.INSTANCE.fullbrightEnabled;
    }

    public static float multiplier() {
        return ModConfig.INSTANCE.fullbrightGamma;
    }

    public static void tick(Minecraft client) {
        if (client.options == null) {
            return;
        }
        tickCounter++;
        if ((tickCounter % 20) != 0) {
            return;
        }
        if (isEnabled()) {
            if (vanillaGamma < 0.0) {
                vanillaGamma = client.options.gamma().get();
            }
            double want = Math.max(0.0, Math.min(10.0, multiplier()));
            if (client.options.gamma().get() != want) {
                client.options.gamma().set(want);
            }
        } else if (vanillaGamma >= 0.0) {
            client.options.gamma().set(vanillaGamma);
            vanillaGamma = -1.0;
        }
    }
}
