// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.feature;

import com.example.multifeature.config.ModConfig;
import java.util.Random;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.effect.MobEffects;

/**
 * No-jump criticals: vanilla grants crits while falling (not on ground).
 * This spoofs a short fall with movement packets so the server sees a
 * falling attack.
 *
 * Four-packet pre-attack sequence, sent in the same tick (Wurst-style
 * "Packet" crit mode, per-hit randomized):
 *   1. Pos(x, y + fallDelta, z, true)   // reset server fall tracker up high
 *   2. Pos(x, y,             z, false)  // drop back -> fallDistance ~= fallDelta
 *   3. Pos(x, y + 1.1E-5,     z, false)  // tiny nudge keeps the value alive
 *   4. Pos(x, y,             z, false)  // settle at the real position
 * fallDelta is drawn fresh per attack, uniform in [critFallMin, critFallMax]
 * (defaults 0.11-0.17): above the common 0.1 server cancellation threshold
 * (the old 0.001 trick dies there), below a real jump, and never a constant
 * 0.0625 fingerprint value.
 *
 * (x, y, z) is captured ONCE at the top of pre and reused for all four
 * packets — never re-read mid-sequence, and post reuses nothing because it
 * sends nothing. No post-attack restore packet: the sequence already ends at
 * the real position and the next natural movement tick corrects onGround.
 *
 * State is INSTANCE state (fields, never statics): one AutoCrit per caller.
 * armed is cleared at the START of every pre so a stale arm from an exception
 * can never leak into the next attack; callers run post only when armed.
 * The Random is owned (one instance, self-contained); per attack cost is
 * one nextDouble (no alloc) + four packets.
 *
 * Honest caveat: this is a client-side technique. Servers running
 * movement-prediction anti-cheats (GrimAC, Intave, HawkAC, ...) simulate the
 * fall server-side and may reject or flag spoofed sequences. Randomizing the
 * fall value defeats naive constant-value pattern matching only — it does NOT
 * make the technique undetectable. Singleplayer/private servers only.
 *
 * Mojang names: ServerboundMovePlayerPacket.Pos.
 */
public final class AutoCrit {
    // Per-instance state. Single-threaded client tick only — no sync needed.
    // Own RNG (one instance, self-contained; no cross-class coupling).
    private final Random rng = new Random();
    private double preX;
    private double preY;
    private double preZ;
    private boolean armed;

    public AutoCrit() {
    }

    public boolean isArmed() {
        return armed;
    }

    /**
     * Sends the four-packet spoof. Call ONLY when the attack WILL go out this
     * tick — a stray burst with no attack is worse than no crit.
     */
    public void sendPreAttack(LocalPlayer player) {
        armed = false;
        if (player.connection == null) {
            return;
        }
        if (!player.onGround() || player.fallDistance != 0.0f) {
            return;
        }
        if (player.isInWater() || player.isInLava()) {
            return;
        }
        // Blindness disables vanilla crits — four packets for zero benefit.
        if (player.hasEffect(MobEffects.BLINDNESS)) {
            return;
        }
        // Capture once — all four packets reuse THESE values, no re-reads.
        preX = player.getX();
        preY = player.getY();
        preZ = player.getZ();
        float lo = ModConfig.INSTANCE.critFallMin;
        float hi = ModConfig.INSTANCE.critFallMax;
        double span = (double) hi - (double) lo;
        // Clamp guarantees lo <= hi; span <= 0 falls back to lo.
        double fallDelta = span <= 0.0 ? (double) lo : (double) lo + rng.nextDouble() * span;
        player.connection.send(new ServerboundMovePlayerPacket.Pos(preX, preY + fallDelta, preZ, true, false));
        player.connection.send(new ServerboundMovePlayerPacket.Pos(preX, preY, preZ, false, false));
        player.connection.send(new ServerboundMovePlayerPacket.Pos(preX, preY + 1.1E-5, preZ, false, false));
        player.connection.send(new ServerboundMovePlayerPacket.Pos(preX, preY, preZ, false, false));
        armed = true;
    }

    /**
     * Closes the pair. Sends nothing by design. If rubberbanding ever shows
     * up in testing, add back a restore here:
     *   player.connection.send(new ServerboundMovePlayerPacket.Pos(preX, preY, preZ, true));
     * and document why — but try without it first.
     */
    public void sendPostAttack() {
        armed = false;
    }
}
