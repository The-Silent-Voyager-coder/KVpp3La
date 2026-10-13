// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mixin;

import com.example.multifeature.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Shield + Item Scaling (first-person).
 *
 * Retargeted 2026-10-06: the old 7-arg renderItem descriptor
 * (LivingEntity, ItemStack, ItemDisplayContext, boolean, PoseStack,
 * MultiBufferSource, int) is pre-1.21.2 — current Mojang 1.21.11 signature is
 * (LivingEntity, ItemStack, ItemDisplayContext, PoseStack,
 * SubmitNodeCollector, int), and the first-person arm+item path is
 * renderArmWithItem(...) (verified against official mappings — both old
 * injects were silent no-ops). HEAD pushes, RETURN pops; the flag is
 * recomputed identically in both so push/pop stay balanced with zero cost
 * when both toggles are off (no PoseStack alloc on the disabled path).
 * Shield offset now applies to OFF_HAND only (old code lowered main-hand too).
 *
 * 1.21.11-validated, 26.x UNVERIFIED — TBC on 26.x, verify via javap
 * (see README "Verification"). defaultRequire=1: drift fails fast at load.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemRenderMixin {
    // TBC on 26.x — verify via javap net.minecraft.client.renderer.ItemInHandRenderer.
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void multifeature$preScale(AbstractClientPlayer player, float tickProgress,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack pose, SubmitNodeCollector queue, int light,
            CallbackInfo ci) {
        if (!wantsTransform(hand)) {
            return;
        }
        pose.pushPose();
        if (ModConfig.INSTANCE.itemScaling) {
            float s = ModConfig.INSTANCE.itemScale;
            pose.scale(s, s, s);
        }
        if (ModConfig.INSTANCE.lowShield && hand == InteractionHand.OFF_HAND) {
            // Lower shield/offhand blocker slightly so it blocks less view.
            pose.translate(0.0, -0.08, 0.0);
        }
    }

    // TBC on 26.x — must match the HEAD inject above; pop only if we pushed.
    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void multifeature$postScale(AbstractClientPlayer player, float tickProgress,
            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
            float equipProgress, PoseStack pose, SubmitNodeCollector queue, int light,
            CallbackInfo ci) {
        if (!wantsTransform(hand)) {
            return;
        }
        pose.popPose();
    }

    // Same-tick recompute: config only mutates on keypress in the client tick,
    // never between these two injections, so HEAD/RETURN always agree.
    private static boolean wantsTransform(InteractionHand hand) {
        if (ModConfig.INSTANCE.itemScaling) {
            return true;
        }
        return ModConfig.INSTANCE.lowShield && hand == InteractionHand.OFF_HAND;
    }
}
