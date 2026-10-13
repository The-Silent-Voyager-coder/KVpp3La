// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clear Water: remove underwater overlay tint.
 *
 * Retargeted 2026-10-06: Gui has NO renderUnderwater method (verified against
 * Mojang 1.21.11 official mappings — old inject was a silent no-op). The
 * underwater overlay lives in ScreenEffectRenderer#renderWater(Minecraft,
 * PoseStack, MultiBufferSource), a private static method — injector MUST be
 * static. (The old renderVignette half of this class moved to InGameHudMixin
 * with the corrected (GuiGraphics, Entity) descriptor.)
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). defaultRequire=1: drift fails fast at load.
 */
@Mixin(ScreenEffectRenderer.class)
public class WaterVisionMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.renderer.ScreenEffectRenderer.
    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void multifeature$clearWater(Minecraft minecraft, PoseStack pose,
            MultiBufferSource buffers, CallbackInfo ci) {
        if (ModConfig.INSTANCE.clearWater) {
            ci.cancel();
        }
    }
}
