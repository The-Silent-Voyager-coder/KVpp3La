// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mine;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Greedy straight-line walker (v1). No A* — if the target is not reachable by
 * walking, jumping 1 step, or breaking one directly-blocking breakable block,
 * the controller blacklists it and moves on. Pure static helpers; all state
 * lives in MineController.
 */
public final class Pathfinder {
    private Pathfinder() {
    }

    /** Yaw degrees facing from feet toward target (vanilla yaw convention). */
    public static float yawTo(double px, double pz, double tx, double tz) {
        return (float) (Math.atan2(-(tx - px), tz - pz) * 180.0 / Math.PI);
    }

    /** Pitch degrees facing from eyes toward target center. */
    public static float pitchTo(double pyEye, double tx, double ty, double tz, double px, double pz) {
        double dx = tx - px;
        double dz = tz - pz;
        double dy = ty - pyEye;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        return (float) (-(Math.atan2(dy, horiz) * 180.0 / Math.PI));
    }

    /** Dominant-axis face for startDestroyBlock, from player toward block. */
    public static Direction faceTo(double px, double py, double pz, BlockPos target) {
        double dx = target.getX() + 0.5 - px;
        double dy = target.getY() + 0.5 - py;
        double dz = target.getZ() + 0.5 - pz;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= ax && ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** Horizontal distance squared (feet plane). */
    public static double distSq2D(LocalPlayer player, BlockPos target) {
        double dx = target.getX() + 0.5 - player.getX();
        double dz = target.getZ() + 0.5 - player.getZ();
        return dx * dx + dz * dz;
    }
}
