// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Palette-gated block scanner. Per chunk section it first runs
 * {@code maybeHas} over the PALETTE (O(palette), not O(4096)) and only scans
 * positions inside sections known to contain the target — presence comes from
 * the palette, positions still need the walk since palettes don't index
 * coordinates. Never force-loads: missing chunks are skipped via the
 * create=false getter.
 *
 * Exposure uses {@code isAir()}, which is true for AIR, CAVE_AIR and
 * VOID_AIR alike — the cave_air miss is exactly Baritone's old bug.
 */
public final class BlockScanner {
    private BlockScanner() {
    }

    public static List<BlockPos> findBlocks(ClientLevel level, BlockPos center, Block target,
            int radius, MineConfig.Mode mode, int cap) {
        List<BlockPos> out = new ArrayList<>();
        double radiusSq = (double) radius * (double) radius;
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        int cr = (radius >> 4) + 2;
        for (int cx = ccx - cr; cx <= ccx + cr; cx++) {
            for (int cz = ccz - cr; cz <= ccz + cr; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                scanChunk(level, chunk, center, target, radiusSq, mode, out, cap);
                if (out.size() >= cap) {
                    return nearestFirst(out, center, cap);
                }
            }
        }
        return nearestFirst(out, center, cap);
    }

    private static void scanChunk(ClientLevel level, ChunkAccess chunk, BlockPos center,
            Block target, double radiusSq, MineConfig.Mode mode, List<BlockPos> out, int cap) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            if (out.size() >= cap) {
                return;
            }
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(state -> state.is(target))) {
                continue;
            }
            int yBase = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int ly = 0; ly < 16; ly++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        if (out.size() >= cap) {
                            return;
                        }
                        BlockState state = section.getBlockState(lx, ly, lz);
                        if (!state.is(target)) {
                            continue;
                        }
                        int wx = (chunk.getPos().x << 4) + lx;
                        int wy = yBase + ly;
                        int wz = (chunk.getPos().z << 4) + lz;
                        double ddx = wx + 0.5 - (center.getX() + 0.5);
                        double ddy = wy + 0.5 - (center.getY() + 0.5);
                        double ddz = wz + 0.5 - (center.getZ() + 0.5);
                        if (ddx * ddx + ddy * ddy + ddz * ddz > radiusSq) {
                            continue;
                        }
                        BlockPos pos = new BlockPos(wx, wy, wz);
                        if (mode == MineConfig.Mode.CAVE && !isExposed(level, pos)) {
                            continue;
                        }
                        out.add(pos);
                    }
                }
            }
        }
    }

    /** Exposed = any of the 6 neighbors is air (any variant — see class note). */
    public static boolean isExposed(ClientLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.offset(dir.getStepX(), dir.getStepY(), dir.getStepZ())).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> nearestFirst(List<BlockPos> found, BlockPos center, int cap) {
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        if (found.size() > cap) {
            return new ArrayList<>(found.subList(0, cap));
        }
        return found;
    }
}
