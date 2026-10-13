// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Fire: lower first-person fire overlay.
 *
 * Retargeted 2026-10-06: Gui has NO renderFire method (verified against Mojang
 * 1.21.11 official mappings — old inject was a silent no-op). The fire overlay
 * lives in ScreenEffectRenderer#renderFire(PoseStack, MultiBufferSource,
 * TextureAtlasSprite), a private static method — injector MUST be static.
 *
 * Semantics: percent == 0 cancels the overlay. 1-99% needs a pose scale inside
 * renderFire itself (version-sensitive locals) — see comment below.
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). defaultRequire=1: drift fails fast at load.
 */
@Mixin(ScreenEffectRenderer.class)
public class FireOverlayMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.renderer.ScreenEffectRenderer.
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void multifeature$lowFire(PoseStack pose, MultiBufferSource buffers,
            TextureAtlasSprite sprite, CallbackInfo ci) {
        if (!ModConfig.INSTANCE.lowFireEnabled) {
            return;
        }
        if (ModConfig.INSTANCE.lowFirePercent <= 0) {
            ci.cancel();
        }
        // For 1-99%: full shrink needs a pose-scale inside renderFire, e.g.
        //   pose.pushPose();
        //   pose.scale(1.0f, ModConfig.INSTANCE.lowFirePercent / 100.0f, 1.0f);
        //   ... vanilla blit ...
        //   pose.popPose();
        // Apply only after confirming 26.x locals.
    }
}
