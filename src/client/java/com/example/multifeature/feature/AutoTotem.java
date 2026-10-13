// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.feature;

import com.example.multifeature.config.ModConfig;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Off-hand totem restock using normal inventory clicks (same as manual F-press).
 * No packets hand-crafted beyond vanilla handleInventoryMouseClick.
 * Human-like: configurable delay + small randomization.
 */
public final class AutoTotem {
    private AutoTotem() {
    }

    private static final Random RNG = new Random();
    private static long nextMoveAtMs;

    // Mojang: off-hand slot index in player container = 45 (36-44 = inv, 5-8 armor, 45 offhand).
    private static final int OFFHAND_SLOT = 45;

    public static void tick(Minecraft client) {
        if (!ModConfig.INSTANCE.autoTotemEnabled) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextMoveAtMs) {
            return;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.getItem() == Items.TOTEM_OF_UNDYING) {
            return;
        }
        // If popped (empty or other item), find totem. Plain for loop, no streams.
        int found = -1;
        // Main inventory 9-44 in container ids (9 hotbar + body). Scan 9..44.
        for (int slot = 9; slot <= 44; slot++) {
            ItemStack st = player.containerMenu.getSlot(slot).getItem();
            if (!st.isEmpty() && st.getItem() == Items.TOTEM_OF_UNDYING) {
                found = slot;
                break;
            }
        }
        if (found < 0) {
            return;
        }
        // Vanilla-style 2-click move: pickup from found, place to offhand, return remainder.
        // containerId 0 = player inventory when no screen open.
        int syncId = player.containerMenu.containerId;
        client.gameMode.handleInventoryMouseClick(syncId, found, 0, ClickType.PICKUP, player);
        client.gameMode.handleInventoryMouseClick(syncId, OFFHAND_SLOT, 0, ClickType.PICKUP, player);
        // If cursor still holds something (offhand had item), put it back where totem was.
        client.gameMode.handleInventoryMouseClick(syncId, found, 0, ClickType.PICKUP, player);

        int base = ModConfig.INSTANCE.totemDelayMs;
        nextMoveAtMs = now + base + RNG.nextInt(51); // +0..50 ms human jitter
    }
}
