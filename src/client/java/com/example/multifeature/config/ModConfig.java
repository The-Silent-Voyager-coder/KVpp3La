// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.example.multifeature.mine.MineConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Single config POJO. Primitives + small collections — cached in the static
 * singleton after load. File: config/multifeature.json (client run dir).
 *
 * targeting maps registry id ("minecraft:zombie") to mode. Absent id = OFF.
 * GUI writes explicit OFF entries so the file shows user choices vs defaults.
 * playerNames preserves order and case as typed; matching is case-insensitive.
 */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean critRangeWarned;
    private static boolean legacyFilterWarned;

    // Singleton instance, mutated in place. Tick code reads primitives directly.
    public static final ModConfig INSTANCE = new ModConfig();

    // --- KillAura ---
    public boolean killAuraEnabled = false;
    public float reach = 3.0f;
    public int scanIntervalTicks = 3;
    public boolean silentRotation = true;
    /** Wurst-style checkLOS: when false, KillAura attacks through walls. Default true = vanilla-safe. */
    public boolean killAuraCheckLOS = true;

    // --- Targeting (replaces the old attackPlayers/Hostile/Passive/Invisible booleans) ---
    public Map<String, TargetMode> targeting = new LinkedHashMap<>();
    public PlayerMode playerMode = PlayerMode.OFF;
    public List<String> playerNames = new ArrayList<>();

    // --- Mining (greedy walker; see MiningTab + MineController) ---
    public boolean mineEnabled = true;
    public MineConfig.Mode mineMode = MineConfig.Mode.CAVE;
    public int mineScanRadius = 32;
    public int mineMaxBlocks = 64;
    public boolean mineAutoEquipPickaxe = true;
    public int mineScanIntervalTicks = 20;

    // --- Health & TTK overlay (global on/off only, no per-entity toggles) ---
    public boolean showHealthOverlay = false;
    public boolean showMeasuredTTK = true;

    // --- AutoCrit (four-packet fall spoof; range drawn fresh per attack) ---
    public boolean autoCritEnabled = true;
    public float critFallMin = 0.11f;
    public float critFallMax = 0.17f;

    // --- Fullbright ---
    public boolean fullbrightEnabled = false;
    /** 0.0-10.0 mapped to gamma; vanilla default ~0.5-1.0. */
    public float fullbrightGamma = 10.0f;

    // --- AutoTotem ---
    public boolean autoTotemEnabled = false;
    public int totemDelayMs = 120;

    // --- Visual toggles ---
    public boolean lowFireEnabled = true;
    public int lowFirePercent = 60;
    public boolean noPumpkin = true;
    public boolean noFrost = true;
    public boolean clearWater = true;
    public boolean clearLava = false;
    public boolean lowShield = true;
    public boolean noFog = true;
    public boolean nightVisionCleanup = true;
    public boolean itemScaling = false;
    public float itemScale = 1.0f;

    private ModConfig() {
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("multifeature.json");
    }

    /** Load once at startup. No alloc in tick. */
    public static void load() {
        Path p = file();
        if (!Files.isRegularFile(p)) {
            save();
            return;
        }
        try {
            String raw = Files.readString(p);
            checkLegacyFilters(raw);
            ModConfig disk = GSON.fromJson(raw, ModConfig.class);
            if (disk != null) {
                copyInto(disk, INSTANCE);
            }
            clamp(INSTANCE);
        } catch (Exception e) {
            // Keep defaults on corrupt file. No logging spam; print once.
            System.err.println("[multifeature] config load failed, using defaults: " + e.getMessage());
        }
    }

    /** Removed keys are ignored by Gson and dropped on next save. Warn once
     * so the silent no-op is visible. Covers the old boolean filters and the
     * removed humanDelayMinMs/humanDelayMaxMs (delay feature deleted). */
    private static void checkLegacyFilters(String raw) {
        if (legacyFilterWarned) {
            return;
        }
        if (raw.contains("\"attackPlayers\"") || raw.contains("\"attackHostile\"")
                || raw.contains("\"attackPassive\"") || raw.contains("\"attackInvisible\"")) {
            legacyFilterWarned = true;
            System.err.println("[multifeature] old attackPlayers/Hostile/Passive/Invisible filters are deprecated and ignored; use the Targeting tab (K) — they will be removed on next save.");
        }
        if (raw.contains("\"humanDelayMinMs\"") || raw.contains("\"humanDelayMaxMs\"")) {
            legacyFilterWarned = true;
            System.err.println("[multifeature] humanDelayMinMs/humanDelayMaxMs were removed (KillAura now attacks at cooldown rate); keys ignored and removed on next save.");
        }
    }

    public static void save() {
        clamp(INSTANCE);
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            System.err.println("[multifeature] config save failed: " + e.getMessage());
        }
    }

    private static void copyInto(ModConfig from, ModConfig to) {
        to.killAuraEnabled = from.killAuraEnabled;
        to.reach = from.reach;
        to.scanIntervalTicks = from.scanIntervalTicks;
        to.silentRotation = from.silentRotation;
        to.killAuraCheckLOS = from.killAuraCheckLOS;
        if (from.targeting != null) {
            to.targeting = new LinkedHashMap<>(from.targeting);
        }
        if (from.playerMode != null) {
            to.playerMode = from.playerMode;
        }
        if (from.playerNames != null) {
            to.playerNames = new ArrayList<>(from.playerNames);
        }
        to.mineEnabled = from.mineEnabled;
        if (from.mineMode != null) {
            to.mineMode = from.mineMode;
        }
        to.mineScanRadius = from.mineScanRadius;
        to.mineMaxBlocks = from.mineMaxBlocks;
        to.mineAutoEquipPickaxe = from.mineAutoEquipPickaxe;
        to.mineScanIntervalTicks = from.mineScanIntervalTicks;
        to.showHealthOverlay = from.showHealthOverlay;
        to.showMeasuredTTK = from.showMeasuredTTK;
        to.autoCritEnabled = from.autoCritEnabled;
        to.critFallMin = from.critFallMin;
        to.critFallMax = from.critFallMax;
        to.fullbrightEnabled = from.fullbrightEnabled;
        to.fullbrightGamma = from.fullbrightGamma;
        to.autoTotemEnabled = from.autoTotemEnabled;
        to.totemDelayMs = from.totemDelayMs;
        to.lowFireEnabled = from.lowFireEnabled;
        to.lowFirePercent = from.lowFirePercent;
        to.noPumpkin = from.noPumpkin;
        to.noFrost = from.noFrost;
        to.clearWater = from.clearWater;
        to.clearLava = from.clearLava;
        to.lowShield = from.lowShield;
        to.noFog = from.noFog;
        to.nightVisionCleanup = from.nightVisionCleanup;
        to.itemScaling = from.itemScaling;
        to.itemScale = from.itemScale;
    }

    private static void clamp(ModConfig c) {
        if (c.reach < 2.0f) c.reach = 2.0f;
        if (c.reach > 7.0f) c.reach = 7.0f;
        if (c.scanIntervalTicks < 1) c.scanIntervalTicks = 1;
        if (c.scanIntervalTicks > 20) c.scanIntervalTicks = 20;
        if (c.mineScanRadius < 8) c.mineScanRadius = 8;
        if (c.mineScanRadius > 64) c.mineScanRadius = 64;
        if (c.mineMaxBlocks < 1) c.mineMaxBlocks = 1;
        if (c.mineMaxBlocks > 256) c.mineMaxBlocks = 256;
        if (c.mineScanIntervalTicks < 1) c.mineScanIntervalTicks = 1;
        if (c.critFallMin < 0.0f) c.critFallMin = 0.0f;
        if (c.critFallMax < 0.0f) c.critFallMax = 0.0f;
        if (c.critFallMin > c.critFallMax) {
            float tmp = c.critFallMin;
            c.critFallMin = c.critFallMax;
            c.critFallMax = tmp;
            if (!critRangeWarned) {
                critRangeWarned = true;
                System.err.println("[multifeature] critFallMin > critFallMax, swapped. Fix config/multifeature.json.");
            }
        }
        if (c.fullbrightGamma < 0.0f) c.fullbrightGamma = 0.0f;
        if (c.fullbrightGamma > 10.0f) c.fullbrightGamma = 10.0f;
        if (c.lowFirePercent < 0) c.lowFirePercent = 0;
        if (c.lowFirePercent > 100) c.lowFirePercent = 100;
        if (c.totemDelayMs < 0) c.totemDelayMs = 0;
        if (c.totemDelayMs > 2000) c.totemDelayMs = 2000;
        if (c.itemScale < 0.25f) c.itemScale = 0.25f;
        if (c.itemScale > 3.0f) c.itemScale = 3.0f;
    }
}
