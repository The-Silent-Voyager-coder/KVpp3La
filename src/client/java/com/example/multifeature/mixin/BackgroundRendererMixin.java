// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No fog (+ clear water/lava distance fog).
 *
 * Retargeted 2026-10-06: FogRenderer MOVED to net.minecraft.client.renderer.fog
 * and setupFog is now an INSTANCE method (Camera, int, DeltaTracker, float,
 * ClientLevel) returning Vector4f (verified against Mojang 1.21.11 official
 * mappings + mapped source — the old 8-arg static descriptor matched nothing
 * and the old import would not even compile). Injector is instance-side to
 * match; non-void target needs CallbackInfoReturnable.
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). Behavior note: skipping setupFog leaves the fog
 * UBO untouched (stale values), so the in-game no-fog check is mandatory — if
 * distance fog persists, retarget the per-environment classes
 * (WaterFogEnvironment / LavaFogEnvironment / AtmosphericFogEnvironment) or a
 * fog toggle instead. defaultRequire=1: drift fails fast at load.
 */
@Mixin(FogRenderer.class)
public class BackgroundRendererMixin {
    // One-time alloc, reused on every no-fog frame — no per-frame garbage.
    private static final Vector4f NO_FOG_FALLBACK = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    // TBC on 26.x — verify via javap net.minecraft.client.renderer.fog.FogRenderer.
    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void multifeature$noFog(Camera camera, int renderDistanceInChunks,
            DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
            CallbackInfoReturnable<Vector4f> cir) {
        if (ModConfig.INSTANCE.noFog) {
            cir.setReturnValue(NO_FOG_FALLBACK);
        }
    }
}
