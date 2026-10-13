// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.render;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.feature.KillAura;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Health hearts + TTK overlay above the active KillAura target.
 * Hook: HudRenderCallback (screen space — no mixins, no world-space text).
 *
 * Per-frame RAM discipline: projection math uses reused static JOML scratch
 * (render thread only), primitives throughout. The ONLY per-frame allocation
 * pressure is zero by construction: estimate strings are cached and rebuilt
 * solely on target/health/weapon change; sprite Identifiers are class-load
 * constants; the measured-map key is rebuilt only alongside the estimate.
 *
 * Health is the client's view (exact in singleplayer, lags a tick or two on
 * servers). The estimate assumes every attack lands — see the '*' rule.
 */
public final class HealthOverlayRenderer {
    private HealthOverlayRenderer() {
    }

    // Sprite paths verified present in the 1.21.11 client jar assets
    // (textures/gui/sprites/hud/heart/*.png). Hardcoded to dodge
    // HeartType.getSprite arg-order guessing; re-verify on 26.x.
    private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
    private static final Identifier HEART_EMPTY = Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_ABSORB_FULL = Identifier.withDefaultNamespace("hud/heart/absorbing_full");
    private static final Identifier HEART_ABSORB_HALF = Identifier.withDefaultNamespace("hud/heart/absorbing_half");

    // Reused projection scratch. Render thread only — never held across frames.
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Vector4f V = new Vector4f();
    private static final Quaternionf CONJ = new Quaternionf();
    private static final StringBuilder SB = new StringBuilder(32);

    // Estimate cache: rebuilt only when target, health or weapon changes.
    private static Entity lastTarget;
    private static int lastHealthBits;
    private static Item lastWeapon;
    private static int lastWeaponDamage;
    private static String cachedEst = "";
    private static String cachedExtra = "";
    private static String cachedKey = "";
    private static Float cachedMeasured;

    public static void onHudRender(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickDelta) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.showHealthOverlay || !c.killAuraEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getWindow() == null) {
            return;
        }
        Entity target = KillAura.getCurrentTarget();
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target == mc.player) {
            return;
        }
        // Render-distance cull first, before any math (64 blocks squared).
        Vec3 tp = target.position();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 cp = cam.position();
        double dx = tp.x - cp.x;
        double dy = tp.y - cp.y;
        double dz = tp.z - cp.z;
        if (dx * dx + dy * dy + dz * dz > 4096.0) {
            return;
        }

        AABB box = target.getBoundingBox();
        double ax = (box.minX + box.maxX) * 0.5;
        double ay = box.maxY + 0.3 + (target.hasCustomName() ? 0.35 : 0.0);
        double az = (box.minZ + box.maxZ) * 0.5;

        cam.rotation().conjugate(CONJ);
        VIEW.identity().rotate(CONJ).translate((float) -cp.x, (float) -cp.y, (float) -cp.z);
        V.set((float) ax, (float) ay, (float) az, 1.0f).mul(VIEW);
        if (V.z > -0.05f) {
            return; // behind camera
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        PROJ.setPerspective((float) Math.toRadians(mc.options.fov().get()), (float) sw / sh, 0.05f, 512.0f);
        V.mul(PROJ);
        if (V.w <= 0.0001f) {
            return;
        }
        float nx = V.x / V.w;
        float ny = V.y / V.w;
        if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) {
            return; // off-screen
        }
        float sx = (nx * 0.5f + 0.5f) * sw;
        float sy = (-ny * 0.5f + 0.5f) * sh;

        LocalPlayer player = mc.player;
        ItemStack weapon = player.getMainHandItem();
        float health = living.getHealth();
        Item weaponItem = weapon.getItem();
        int weaponDamage = weapon.getDamageValue();
        if (target != lastTarget || Float.floatToIntBits(health) != lastHealthBits
                || weaponItem != lastWeapon || weaponDamage != lastWeaponDamage) {
            lastTarget = target;
            lastHealthBits = Float.floatToIntBits(health);
            lastWeapon = weaponItem;
            lastWeaponDamage = weaponDamage;
            recompute(player, weapon, living, health, c);
        }

        drawHearts(graphics, living, health, sx, sy);
        Font font = mc.font;
        int tw = font.width(cachedEst);
        graphics.drawString(font, cachedEst, (int) (sx - tw * 0.5f), (int) sy + 12, 0xFFFFFF, true);
        if (c.showMeasuredTTK && cachedMeasured != null) {
            SB.setLength(0);
            SB.append("Last: ");
            appendTenths(SB, cachedMeasured);
            SB.append('s');
            String last = SB.toString();
            int lw = font.width(last);
            graphics.drawString(font, last, (int) (sx - lw * 0.5f), (int) sy + 22, 0xA0A0A0, true);
        }
    }

    private static void recompute(LocalPlayer player, ItemStack weapon, LivingEntity living,
            float health, ModConfig c) {
        double dph = DamageEstimator.estimateDamagePerHit(player, weapon, living);
        double interval = DamageEstimator.attackIntervalSeconds(player);
        double secs = DamageEstimator.estimateSeconds(health, dph, interval);

        SB.setLength(0);
        SB.append("Est. ");
        if (Double.isInfinite(secs) || Double.isNaN(secs)) {
            SB.append("--");
        } else {
            appendTenths(SB, secs);
            SB.append('s');
        }
        // '*' = the estimate assumes continuous hits that are NOT happening:
        // out of reach, or walled while checkLOS is on. LOS raycast runs here
        // (recompute-only, never per frame).
        double reachSq = (double) c.reach * (double) c.reach;
        Vec3 tp = living.position();
        Vec3 pp = player.position();
        double ddx = tp.x - pp.x;
        double ddy = tp.y - pp.y;
        double ddz = tp.z - pp.z;
        boolean outOfReach = ddx * ddx + ddy * ddy + ddz * ddz > reachSq;
        boolean walled = c.killAuraCheckLOS
                && !KillAura.hasLineOfSight(Minecraft.getInstance(), player.getEyePosition(1.0f), living);
        if (outOfReach || walled) {
            SB.append('*');
        }
        cachedEst = SB.toString();

        if (health > 20.0f) {
            SB.setLength(0);
            SB.append('+').append((int) (health - 20.0f));
            cachedExtra = SB.toString();
        } else {
            cachedExtra = "";
        }

        String weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem()).toString();
        String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
        cachedKey = weaponId + "|" + typeId;
        cachedMeasured = TTKTracker.getLast(weaponId, typeId);
    }

    private static void appendTenths(StringBuilder sb, double seconds) {
        long tenths = Math.round(seconds * 10.0);
        sb.append(tenths / 10).append('.').append(Math.abs(tenths % 10));
    }

    private static void drawHearts(GuiGraphics graphics, LivingEntity living, float health, float sx, float sy) {
        int slots = Math.min(10, (int) Math.ceil(living.getMaxHealth() / 2.0));
        int totalW = slots * 9;
        int x0 = (int) sx - totalW / 2;
        int y0 = (int) sy;
        for (int i = 0; i < slots; i++) {
            double remaining = health - i * 2.0;
            Identifier sprite;
            if (remaining >= 2.0) {
                sprite = HEART_FULL;
            } else if (remaining > 0.0) {
                sprite = HEART_HALF;
            } else {
                sprite = HEART_EMPTY;
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x0 + i * 9, y0, 9, 9);
        }
        float absorb = living.getAbsorptionAmount();
        if (absorb > 0.0f) {
            int acount = Math.min(10, (int) Math.ceil(absorb / 2.0));
            int ax0 = (int) sx - acount * 7 / 2;
            for (int i = 0; i < acount; i++) {
                double remaining = absorb - i * 2.0;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        remaining >= 2.0 ? HEART_ABSORB_FULL : HEART_ABSORB_HALF,
                        ax0 + i * 7, y0 - 8, 7, 7);
            }
        }
        if (!cachedExtra.isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font, cachedExtra, x0 + totalW + 2, y0 + 1, 0xFF5555, true);
        }
    }
}
