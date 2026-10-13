// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No pumpkin / no frost + night-vision vignette cleanup.
 *
 * Retargeted 2026-10-06: Gui has NO renderPumpkin/renderFrost/renderPowderSnow
 * methods (verified against Mojang 1.21.11 official mappings — all three old
 * injects were silent no-ops). Pumpkin blur and powder-snow outline both funnel
 * through the single choke point renderTextureOverlay(GuiGraphics, Identifier,
 * float), so one filter covers both. renderVignette takes an Entity second arg,
 * NOT DeltaTracker — old descriptor never matched either.
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). defaultRequire=1: drift fails fast at load.
 */
@Mixin(Gui.class)
public class InGameHudMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.gui.Gui.
    // Texture paths (stable for years): textures/misc/pumpkinblur.png,
    // textures/misc/powdersnow_outline.png. getPath()/contains allocate nothing.
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void multifeature$overlayFilter(GuiGraphics graphics, Identifier id,
            float opacity, CallbackInfo ci) {
        String path = id.getPath();
        if (path.contains("pumpkin")) {
            if (ModConfig.INSTANCE.noPumpkin) {
                ci.cancel();
            }
        } else if (path.contains("powdersnow")) {
            if (ModConfig.INSTANCE.noFrost) {
                ci.cancel();
            }
        }
    }

    // TBC on 26.x — verify descriptor is still (GuiGraphics, Entity).
    // Cancels the blue night-vision vignette tint while fullbright is on.
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void multifeature$nightVisionCleanup(GuiGraphics graphics, Entity entity,
            CallbackInfo ci) {
        if (ModConfig.INSTANCE.fullbrightEnabled && ModConfig.INSTANCE.nightVisionCleanup) {
            ci.cancel();
        }
    }
}
