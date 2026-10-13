// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright shader/night-vision path.
 *
 * Retargeted 2026-10-06: GameRenderer#getNightVisionScale(LivingEntity, float)
 * confirmed in Mojang 1.21.11 official mappings (static method — injector MUST
 * be static; the previous instance-method injector threw InvalidMixinException).
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). defaultRequire=1: drift fails fast at load.
 */
@Mixin(GameRenderer.class)
public class LightmapMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.renderer.GameRenderer.
    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void multifeature$fullbright(LivingEntity entity, float tickDelta,
            CallbackInfoReturnable<Float> cir) {
        if (ModConfig.INSTANCE.fullbrightEnabled) {
            cir.setReturnValue(1.0f);
        }
    }
}
