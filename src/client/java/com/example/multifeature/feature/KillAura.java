// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.feature;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.config.PlayerMode;
import com.example.multifeature.config.TargetMode;
import com.example.multifeature.render.TTKTracker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Legit-style KillAura: closest eligible entity within reach, cooldown-gated,
 * single LOS raycast on the winner, silent server-side rotation. No artificial
 * delay: attacks at the maximum rate the vanilla cooldown allows.
 *
 * Targeting (the ONLY combat predicate): ModConfig.targeting map keyed by
 * registry id, absent id = OFF. Players route through playerMode/playerNames,
 * never the map. WHEN_ANGERED means the entity's attack target is the local
 * player (Mob.getTarget(), javap-verified public — no accessor mixin).
 * WHEN_ANGERED on a non-anger entity is treated as OFF with a once-log.
 * Non-living entities (boats, minecarts, armor stands, ...) are eligible
 * with OFF / ALWAYS only.
 *
 * Low-RAM (honest accounting): primitives on the hot path, plain for loop, no
 * streams/lambdas/Optional. Bounded allocs per scan: eye Vec3, scanBox AABB,
 * and the entities list MC hands us. One Vec3 + one ClipContext per ATTACK.
 * Four small packets per hit (Rot + 3x... see AutoCrit) — Rot + AutoCrit Pos
 * burst. No logging in tick (anger-mismatch warn set is load/path rare).
 * Combat logic only — no mixins here.
 */
public final class KillAura {
    private KillAura() {
    }

    private static int tickCounter;
    // KillAura is a static-only utility (private ctor, no instances), so an
    // instance field is impossible here — hence static. Deliberate and safe:
    // exactly one client, one tick thread, one logical owner, so one shared
    // state. State itself lives in AutoCrit's instance fields.
    private static final AutoCrit AUTO_CRIT = new AutoCrit();
    // Ids already warned about WHEN_ANGERED-on-non-anger (defensive configs).
    private static final Set<String> ANGER_WARNED = new HashSet<>();
    // Current target for the health overlay + TTK death hook. Written only
    // here, on the client tick; read from the render thread.
    private static Entity currentTarget;
    private static long trackStartMs;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;

    /** One-line accessor for the overlay. Null when nothing is targeted. */
    public static Entity getCurrentTarget() {
        return currentTarget;
    }

    public static void tick(Minecraft client) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.killAuraEnabled) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) {
            return;
        }
        if (client.level != lastLevel) {
            lastLevel = client.level;
            TTKTracker.clear();
            currentTarget = null;
        }
        // consume() pattern avoids repeated toggling while held — handled in TickHandler;
        // this tick() only attacks.

        tickCounter++;
        if ((tickCounter % Math.max(1, c.scanIntervalTicks)) != 0) {
            return;
        }
        // Only attack when vanilla cooldown is full (legit timing).
        if (player.getAttackStrengthScale(0.0f) < 1.0f) {
            return;
        }
        long now = System.currentTimeMillis();

        float reach = c.reach;
        double reachSq = (double) reach * (double) reach;

        Vec3 eye = player.getEyePosition(1.0f);
        // One AABB per scan, not per entity.
        AABB scanBox = player.getBoundingBox().inflate(reach, reach * 0.6, reach);

        List<Entity> nearby = client.level.getEntities(player, scanBox);
        Entity best = null;
        double bestDistSq = reachSq;

        for (int i = 0, n = nearby.size(); i < n; i++) {
            Entity e = nearby.get(i);
            if (e == player || e.isSpectator() || !e.isAlive()) {
                continue;
            }
            if (!shouldAttack(e, player, c)) {
                continue;
            }
            double dx = e.getX() - player.getX();
            double dy = (e.getY() + e.getBbHeight() * 0.5) - (player.getY() + player.getEyeHeight());
            double dz = e.getZ() - player.getZ();
            double dSq = dx * dx + dy * dy + dz * dz;
            if (dSq > bestDistSq) {
                continue;
            }
            // NOTE: no LOS check here on purpose — one raycast on the winner
            // below (N->1 per scan). Closest eligible in reach wins; if it is
            // walled the attack is skipped entirely, no fall-through.
            best = e;
            bestDistSq = dSq;
        }

        if (best == null) {
            noteTarget(null, player, now, client);
            return;
        }
        // Wurst-style checkLOS: when off, the raycast is skipped entirely and
        // walled targets are attacked (server validates distance, not LOS).
        if (c.killAuraCheckLOS && !hasLineOfSight(client, eye, best)) {
            noteTarget(null, player, now, client);
            return;
        }
        noteTarget(best, player, now, client);

        // Silent rotation: rotate server-side packet only, do not move camera.
        if (c.silentRotation && player.connection != null) {
            double dx = best.getX() - player.getX();
            double dz = best.getZ() - player.getZ();
            double dy = (best.getY() + best.getBbHeight() * 0.5)
                    - (player.getY() + player.getEyeHeight());
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float pitch = (float) (-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
            player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    yaw, pitch, player.onGround(), false));
        }

        // Four-packet crit spoof before the swing. Pre runs only when this
        // attack WILL go out (winner found, LOS passed) — never a stray burst.
        // Post only closes the pair (sends nothing); run it only when armed.
        if (c.autoCritEnabled) {
            AUTO_CRIT.sendPreAttack(player);
        }
        client.gameMode.attack(player, best);
        player.swing(InteractionHand.MAIN_HAND);
        if (c.autoCritEnabled && AUTO_CRIT.isArmed()) {
            AUTO_CRIT.sendPostAttack();
        }
    }

    /**
     * The one combat predicate. Players route through playerMode; everything
     * else through the targeting map (absent = OFF).
     */
    private static boolean shouldAttack(Entity e, LocalPlayer player, ModConfig c) {
        if (e instanceof Player) {
            return shouldAttackPlayer((Player) e, c);
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        TargetMode mode = c.targeting.getOrDefault(id, TargetMode.OFF);
        if (mode == TargetMode.OFF) {
            return false;
        }
        if (mode == TargetMode.ALWAYS) {
            return true;
        }
        // WHEN_ANGERED: attack target is the local player. Interface check is
        // runtime-robust (covers modded NeutralMobs absent from the id set).
        if (e instanceof Mob mob && mob instanceof NeutralMob) {
            return mob.getTarget() == player;
        }
        // Defensive: hand-edited config put WHEN_ANGERED on a non-anger entity.
        if (ANGER_WARNED.add(id)) {
            System.err.println("[multifeature] targeting: " + id + " is WHEN_ANGERED but cannot be angered; treating as OFF.");
        }
        return false;
    }

    private static boolean shouldAttackPlayer(Player other, ModConfig c) {
        if (c.playerMode == PlayerMode.OFF) {
            return false;
        }
        if (c.playerMode == PlayerMode.ALL) {
            return true;
        }
        // BY_NAME with an empty list behaves as OFF (documented).
        if (c.playerNames.isEmpty()) {
            return false;
        }
        String name = other.getGameProfile().name();
        for (int i = 0, n = c.playerNames.size(); i < n; i++) {
            if (c.playerNames.get(i).equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tick-side target tracking for the overlay + TTK death hook. When the
     * tracked reference changes, a dead predecessor records its measured TTK
     * (same-level only — stale refs across reloads are dropped silently).
     * Never called from the render path.
     */
    private static void noteTarget(Entity best, LocalPlayer player, long now, Minecraft client) {
        if (currentTarget != best) {
            if (currentTarget != null && !currentTarget.isAlive()
                    && currentTarget.level() == client.level) {
                TTKTracker.recordKill(player, currentTarget, now - trackStartMs);
            }
            currentTarget = best;
            trackStartMs = now;
        }
    }

    /** Public for the overlay's '*' check (render package). Single raycast, recompute-only. */
    public static boolean hasLineOfSight(Minecraft client, Vec3 from, Entity target) {
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        ClipContext ctx = new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player);
        HitResult hit = client.level.clip(ctx);
        return hit.getType() == HitResult.Type.MISS;
    }
}
