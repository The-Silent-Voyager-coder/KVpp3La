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
 * Clear Lava (opt-in, default off): remove lava overlay tint.
 *
 * Retargeted 2026-10-06: Gui has NO renderLava method (verified against Mojang
 * 1.21.11 official mappings — old inject was a silent no-op). 1.21.11
 * ScreenEffectRenderer has no dedicated lava method either; the lava overlay
 * goes through renderTex(TextureAtlasSprite, PoseStack, MultiBufferSource), so
 * this filters on the sprite path ("lava") instead of cancelling blindly —
 * in-wall overlays keep rendering.
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). Lava routing is the least certain of the set:
 * the README in-game check (submerge in lava with clearLava=true) is
 * mandatory — if the overlay persists, re-javap ScreenEffectRenderer on 26.x.
 * defaultRequire=1: drift fails fast at load.
 */
@Mixin(ScreenEffectRenderer.class)
public class LavaVisionMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.renderer.ScreenEffectRenderer.
    // contents()/name()/getPath() return existing objects; contains allocates nothing.
    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void multifeature$clearLava(TextureAtlasSprite sprite, PoseStack pose,
            MultiBufferSource buffers, CallbackInfo ci) {
        if (!ModConfig.INSTANCE.clearLava) {
            return;
        }
        String path = sprite.contents().name().getPath();
        if (path.contains("lava")) {
            ci.cancel();
        }
    }
}
