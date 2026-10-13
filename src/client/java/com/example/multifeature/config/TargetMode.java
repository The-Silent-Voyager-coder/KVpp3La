// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.config;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.MobCategory;

/**
 * Per-entity attack mode. Serialized by Gson as name strings ("OFF", ...).
 *
 * WHEN_ANGERED availability (javap-verified on 1.21.11, re-verify on 26.x):
 * only types whose class implements NeutralMob offer it. Verified set below —
 * wolf, iron_golem, bee, polar_bear, enderman, zombified_piglin. Notably NOT
 * neutral despite appearances: llama, panda, goat, piglin, piglin_brute,
 * dolphin, spider (brain/lunge hostility, no NeutralMob interface).
 * GUI gating AND combat both funnel through isAngerCapable — single place to
 * update on 26.x. Runtime detection additionally uses instanceof NeutralMob
 * so modded neutral mobs behave even if absent from the id set.
 */
public enum TargetMode {
    OFF,
    ALWAYS,
    WHEN_ANGERED;

    /** Display categories for the targeting tree. */
    public enum Category {
        HOSTILE,
        NEUTRAL,
        PASSIVE,
        UTILITY,
        OTHER
    }

    // javap-verified NeutralMob implementors on 1.21.11. Re-verify on 26.x.
    private static final Set<String> ANGER_CAPABLE_IDS = new HashSet<>();

    static {
        ANGER_CAPABLE_IDS.add("minecraft:wolf");
        ANGER_CAPABLE_IDS.add("minecraft:iron_golem");
        ANGER_CAPABLE_IDS.add("minecraft:bee");
        ANGER_CAPABLE_IDS.add("minecraft:polar_bear");
        ANGER_CAPABLE_IDS.add("minecraft:enderman");
        ANGER_CAPABLE_IDS.add("minecraft:zombified_piglin");
    }

    /** Single home for WHEN_ANGERED availability. Id string like "minecraft:wolf". */
    public static boolean isAngerCapable(String entityId) {
        return ANGER_CAPABLE_IDS.contains(entityId);
    }

    /** Category precedence: NeutralMob override first, then MobCategory. */
    public static Category categorize(String entityId, MobCategory mobCategory) {
        if (isAngerCapable(entityId)) {
            return Category.NEUTRAL;
        }
        if (mobCategory == MobCategory.MONSTER) {
            return Category.HOSTILE;
        }
        if (mobCategory == MobCategory.CREATURE
                || mobCategory == MobCategory.AMBIENT
                || mobCategory == MobCategory.WATER_CREATURE
                || mobCategory == MobCategory.WATER_AMBIENT
                || mobCategory == MobCategory.UNDERGROUND_WATER_CREATURE
                || mobCategory == MobCategory.AXOLOTLS) {
            return Category.PASSIVE;
        }
        if (mobCategory == MobCategory.MISC) {
            return Category.UTILITY;
        }
        return Category.OTHER;
    }

    /** Cycle order. Skips WHEN_ANGERED when the entity cannot be angered. */
    public TargetMode next(boolean angerCapable) {
        if (this == OFF) {
            return ALWAYS;
        }
        if (this == ALWAYS) {
            return angerCapable ? WHEN_ANGERED : OFF;
        }
        return OFF;
    }
}
