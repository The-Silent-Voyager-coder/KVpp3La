// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mine;

import com.example.multifeature.config.ModConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining state machine: IDLE → SCANNING → PATHING → MINING → … → DONE,
 * with PAUSED preserving previousState. Single static owner, tick-driven.
 * Independent from KillAura (pausing one never pauses the other).
 *
 * Movement without mixins: KeyMapping.setDown re-asserted every tick while
 * driving (up + sprint, jump on 1-step obstacles) and released on stop —
 * the same channel real key presses use, so other mods see normal input.
 * Deliberately NOT a player.input swap: two mods replacing player.input
 * silently break each other with no error. Packets sent are vanilla:
 * movement from the normal pipeline, block-break via MultiPlayerGameMode,
 * one ServerboundSetCarriedItemPacket per hotbar-slot change on auto-equip.
 */
public final class MineController {
    private MineController() {
    }

    public enum State {
        IDLE,
        SCANNING,
        PATHING,
        MINING,
        PAUSED,
        DONE
    }

    private static State state = State.IDLE;
    private static State previousState = State.IDLE;
    private static Block targetBlock;
    private static BlockPos currentTarget;
    private static final Set<BlockPos> BLACKLIST = new HashSet<>();
    private static final int MAX_BLACKLIST = 256;
    // Timeouts so a runaway op can never wait forever (user asked: never
    // block on input/state that may never arrive). 10 min active, 10 min
    // paused; both tick-driven, no threads, no wall-clock drift.
    private static final int OPERATION_TIMEOUT_TICKS = 20 * 60 * 10;
    private static final int PAUSE_TIMEOUT_TICKS = 20 * 60 * 10;
    private static final int STUCK_TIMEOUT_TICKS = 100;
    private static int operationTicks;
    private static int pausedTicks;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int rescanTimer;
    private static int stuckTimer;
    private static double stuckX;
    private static double stuckZ;
    private static int lastScanCount;
    private static long lastScanMs;
    private static BlockPos destroyingPos;
    private static int lastEquipSlot = -1;

    // Pickaxe tier order for tie-breaks (netherite > diamond > iron > stone > golden > wooden).
    private static final Map<String, Integer> TIER = new HashMap<>();

    static {
        TIER.put("netherite_pickaxe", 5);
        TIER.put("diamond_pickaxe", 4);
        TIER.put("iron_pickaxe", 3);
        TIER.put("stone_pickaxe", 2);
        TIER.put("golden_pickaxe", 1);
        TIER.put("wooden_pickaxe", 0);
    }

    // ---- public surface (commands + GUI call these; no duplicated logic) ----

    public static State getState() {
        return state;
    }

    public static BlockPos getCurrentTarget() {
        return currentTarget;
    }

    public static Block getTargetBlock() {
        return targetBlock;
    }

    public static int getLastScanCount() {
        return lastScanCount;
    }

    public static long getLastScanMs() {
        return lastScanMs;
    }

    public static void startMining(Minecraft client, Block block) {
        ModConfig c = ModConfig.INSTANCE;
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        targetBlock = block;
        BLACKLIST.clear();
        currentTarget = null;
        destroyingPos = null;
        rescanTimer = 0;
        operationTicks = 0;
        pausedTicks = 0;
        lastLevel = client.level;
        state = State.SCANNING;
        say(player, "Mining " + idOf(block) + " (" + c.mineMode.name() + ", r=" + c.mineScanRadius + ").");
    }

    public static void pause(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (state == State.IDLE || state == State.DONE || state == State.PAUSED) {
            say(client.player, "Nothing to pause.");
            return;
        }
        previousState = state;
        state = State.PAUSED;
        pausedTicks = 0;
        releaseDriving(client);
        say(client.player, "Mining paused.");
    }

    public static void resume(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (state != State.PAUSED) {
            say(client.player, "Nothing to resume.");
            return;
        }
        state = previousState;
        pausedTicks = 0;
        say(client.player, "Mining resumed.");
    }

    public static void cancel(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (state == State.IDLE) {
            say(client.player, "Nothing to cancel.");
            return;
        }
        reset(client);
        say(client.player, "Mining cancelled.");
    }

    /** Synchronous probe for the GUI test-scan row. Changes no mining state. */
    public static TestReport testScan(Minecraft client, Block block) {
        ModConfig c = ModConfig.INSTANCE;
        if (client.player == null || client.level == null) {
            return new TestReport(false, "No world open.");
        }
        BlockPos center = client.player.blockPosition();
        List<BlockPos> found = BlockScanner.findBlocks(client.level, center, block,
                c.mineScanRadius, c.mineMode, c.mineMaxBlocks);
        String id = idOf(block);
        if (found.isEmpty()) {
            return new TestReport(false, "No " + id + " in loaded chunks within "
                    + c.mineScanRadius + " blocks. Try Xray mode, a larger radius, or move closer.");
        }
        for (BlockPos pos : found) {
            if (BlockScanner.isExposed(client.level, pos)) {
                double d = Math.sqrt(pos.distSqr(center));
                long dd = Math.round(d * 10.0);
                return new TestReport(true, "Found " + found.size() + ". Nearest exposed at "
                        + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                        + " (" + (dd / 10) + "." + Math.abs(dd % 10) + " blocks). Ready to mine.");
            }
        }
        return new TestReport(false, "Found " + found.size()
                + ", none exposed. Switch to Xray mode to mine them.");
    }

    public static final class TestReport {
        public final boolean ok;
        public final String text;

        TestReport(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }
    }

    // ---- tick ----

    public static void tick(Minecraft client) {
        if (!ModConfig.INSTANCE.mineEnabled) {
            releaseDriving(client);
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) {
            // Disconnect/menu: never leave movement keys asserted into the next session.
            releaseDriving(client);
            return;
        }
        // World reload/disconnect: drop stale targets + blacklist, never wait
        // on positions from a dead level.
        if (client.level != lastLevel) {
            lastLevel = client.level;
            if (state != State.IDLE && state != State.DONE) {
                reset(client);
            } else {
                BLACKLIST.clear();
            }
            return;
        }
        if (state == State.PAUSED) {
            pausedTicks++;
            if (pausedTicks >= PAUSE_TIMEOUT_TICKS) {
                reset(client);
                say(player, "Mining auto-cancelled after 10 min paused.");
            }
            return;
        }
        if (state == State.IDLE || state == State.DONE) {
            return;
        }
        // Overall op timeout: never path/mine forever on an unreachable world.
        operationTicks++;
        if (operationTicks >= OPERATION_TIMEOUT_TICKS) {
            reset(client);
            say(player, "Mining timed out after 10 min — cancelled.");
            return;
        }
        if (targetBlock == null) {
            reset(client);
            return;
        }
        if (state == State.SCANNING) {
            doScan(client, player);
            return;
        }
        // Periodic re-validation (every mineScanIntervalTicks): target gone?
        rescanTimer++;
        if (rescanTimer >= ModConfig.INSTANCE.mineScanIntervalTicks) {
            rescanTimer = 0;
            if (currentTarget != null
                    && !client.level.getBlockState(currentTarget).is(targetBlock)) {
                currentTarget = null;
                destroyingPos = null;
                state = State.SCANNING;
                return;
            }
        }
        if (state == State.PATHING) {
            doPathing(client, player);
            return;
        }
        if (state == State.MINING) {
            doMining(client, player);
        }
    }

    private static void doScan(Minecraft client, LocalPlayer player) {
        ModConfig c = ModConfig.INSTANCE;
        List<BlockPos> found = BlockScanner.findBlocks(client.level, player.blockPosition(),
                targetBlock, c.mineScanRadius, c.mineMode, c.mineMaxBlocks);
        lastScanCount = found.size();
        lastScanMs = System.currentTimeMillis();
        currentTarget = null;
        for (BlockPos pos : found) {
            if (!BLACKLIST.contains(pos)) {
                currentTarget = pos;
                break;
            }
        }
        if (currentTarget == null) {
            state = State.DONE;
            releaseDriving(client);
            return;
        }
        stuckTimer = 0;
        stuckX = player.getX();
        stuckZ = player.getZ();
        state = State.PATHING;
    }

    private static void doPathing(Minecraft client, LocalPlayer player) {
        if (currentTarget == null) {
            state = State.SCANNING;
            return;
        }
        if (!client.level.getBlockState(currentTarget).is(targetBlock)) {
            currentTarget = null;
            state = State.SCANNING;
            return;
        }
        double distSq = Pathfinder.distSq2D(player, currentTarget);
        if (distSq < 9.0) {
            releaseDriving(client);
            if (ensurePickaxe(client, player)) {
                state = State.MINING;
            }
            return;
        }
        ensureDriving(client);
        float yaw = Pathfinder.yawTo(player.getX(), player.getZ(),
                currentTarget.getX() + 0.5, currentTarget.getZ() + 0.5);
        player.setYRot(yaw);
        // 1-step obstacle directly ahead at feet level with headroom -> jump.
        int stepX = (int) Math.round(-Math.sin(yaw * Math.PI / 180.0));
        int stepZ = (int) Math.round(Math.cos(yaw * Math.PI / 180.0));
        BlockPos feet = player.blockPosition();
        BlockPos ahead = feet.offset(stepX, 0, stepZ);
        boolean feetBlocked = !client.level.getBlockState(ahead).isAir();
        boolean headFree = client.level.getBlockState(ahead.above()).isAir()
                && client.level.getBlockState(feet.above().above()).isAir();
        client.options.keyJump.setDown(feetBlocked && headFree);
        // Stuck: 100 ticks with < 0.5 block progress -> blacklist, rescan.
        // Never grows without bound: cap 256 per spec, cleared on cancel/reload.
        double moved = Math.abs(player.getX() - stuckX) + Math.abs(player.getZ() - stuckZ);
        if (moved > 0.5) {
            stuckTimer = 0;
            stuckX = player.getX();
            stuckZ = player.getZ();
        } else {
            stuckTimer++;
            if (stuckTimer >= STUCK_TIMEOUT_TICKS) {
                if (BLACKLIST.size() >= MAX_BLACKLIST) {
                    BLACKLIST.clear();
                }
                BLACKLIST.add(currentTarget.immutable());
                currentTarget = null;
                state = State.SCANNING;
            }
        }
    }

    private static void doMining(Minecraft client, LocalPlayer player) {
        if (currentTarget == null) {
            state = State.SCANNING;
            return;
        }
        BlockState current = client.level.getBlockState(currentTarget);
        if (!current.is(targetBlock)) {
            currentTarget = null;
            destroyingPos = null;
            state = State.SCANNING;
            return;
        }
        destroyTick(client, player, currentTarget);
    }

    private static void destroyTick(Minecraft client, LocalPlayer player, BlockPos pos) {
        float yaw = Pathfinder.yawTo(player.getX(), player.getZ(), pos.getX() + 0.5, pos.getZ() + 0.5);
        float pitch = Pathfinder.pitchTo(player.getY() + player.getEyeHeight(),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, player.getX(), player.getZ());
        player.setYRot(yaw);
        player.setXRot(pitch);
        if (!pos.equals(destroyingPos)) {
            destroyingPos = pos.immutable();
            client.gameMode.startDestroyBlock(pos, Pathfinder.faceTo(player.getX(), player.getY(), player.getZ(), pos));
        } else {
            client.gameMode.continueDestroyBlock(pos, Pathfinder.faceTo(player.getX(), player.getY(), player.getZ(), pos));
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** Auto-equip on MINING entry. Hotbar only — never touches main inventory. */
    private static boolean ensurePickaxe(Minecraft client, LocalPlayer player) {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.mineAutoEquipPickaxe) {
            return true;
        }
        ItemStack held = player.getMainHandItem();
        BlockState targetState = currentTarget == null
                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                : client.level.getBlockState(currentTarget);
        if (held.is(ItemTags.PICKAXES)) {
            lastEquipSlot = player.getInventory().getSelectedSlot();
            return true;
        }
        int bestSlot = -1;
        float bestSpeed = -1.0f;
        int bestTier = -1;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot <= 8; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) {
                continue;
            }
            float speed = stack.getDestroySpeed(targetState);
            int tier = tierOf(stack);
            if (speed > bestSpeed || (speed == bestSpeed && tier > bestTier)) {
                bestSpeed = speed;
                bestTier = tier;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            say(player, "No pickaxe in hotbar — mining cancelled.");
            reset(client);
            return false;
        }
        if (bestSlot != inv.getSelectedSlot()) {
            inv.setSelectedSlot(bestSlot);
            if (player.connection != null && bestSlot != lastEquipSlot) {
                player.connection.send(new ServerboundSetCarriedItemPacket(bestSlot));
            }
        }
        lastEquipSlot = bestSlot;
        return true;
    }

    private static int tierOf(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Integer tier = TIER.get(path);
        return tier != null ? tier : -1;
    }

    private static void ensureDriving(Minecraft client) {
        client.options.keyUp.setDown(true);
        client.options.keySprint.setDown(true);
    }

    private static void releaseDriving(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
        destroyingPos = null;
    }

    private static void reset(Minecraft client) {
        state = State.IDLE;
        previousState = State.IDLE;
        targetBlock = null;
        currentTarget = null;
        BLACKLIST.clear();
        lastEquipSlot = -1;
        operationTicks = 0;
        pausedTicks = 0;
        rescanTimer = 0;
        stuckTimer = 0;
        destroyingPos = null;
        releaseDriving(client);
    }

    private static String idOf(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static void say(LocalPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), false);
    }
}
