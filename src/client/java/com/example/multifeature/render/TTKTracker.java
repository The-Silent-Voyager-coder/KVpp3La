// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/**
 * Session-scoped measured TTK: wall-clock first-hit→death per
 * (weapon item id, entity type id). Written only from KillAura's tick-side
 * death hook, read only when the overlay estimate recomputes. Cleared on
 * world reload (KillAura calls clear on level change). Tiny: one float per
 * (weapon, type) pair actually killed.
 */
public final class TTKTracker {
    private TTKTracker() {
    }

    private static final Map<String, Float> LAST_SECONDS = new HashMap<>();

    public static void recordKill(LocalPlayer player, Entity dead, long elapsedMs) {
        if (elapsedMs <= 0) {
            return;
        }
        String weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType()).toString();
        LAST_SECONDS.put(weaponId + "|" + typeId, elapsedMs / 1000.0f);
    }

    /** Null when this (weapon, type) pair has no recorded kill yet. */
    public static Float getLast(String weaponId, String typeId) {
        return LAST_SECONDS.get(weaponId + "|" + typeId);
    }

    public static void clear() {
        LAST_SECONDS.clear();
    }
}
