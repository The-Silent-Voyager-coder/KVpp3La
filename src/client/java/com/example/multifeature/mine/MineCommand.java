// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.mine;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Star-prefixed chat commands, intercepted before anything reaches the server
 * (ALLOW_CHAT returns false = consumed locally). Messages not starting with
 * the exact prefixes pass through untouched.
 *
 * Feedback goes through player.displayClientMessage — ALLOW_CHAT handlers get
 * no command source, so FabricClientCommandSource is unavailable here; the
 * chat line is the equivalent channel.
 */
public final class MineCommand {
    private MineCommand() {
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(MineCommand::onChat);
    }

    /** False = consumed locally, true = send to server. */
    private static boolean onChat(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return true;
        }
        if (message.equals("*c")) {
            MineController.cancel(client);
            return false;
        }
        if (message.equals("*p")) {
            MineController.pause(client);
            return false;
        }
        if (message.equals("*up")) {
            MineController.resume(client);
            return false;
        }
        if (!message.startsWith("*mine")) {
            return true;
        }
        handleMine(client, client.player, message);
        return false;
    }

    private static void handleMine(Minecraft client, LocalPlayer player, String message) {
        if (!com.example.multifeature.config.ModConfig.INSTANCE.mineEnabled) {
            say(player, "Mining is disabled. Enable it in the Mining tab.", true);
            return;
        }
        String rest = message.length() > 5 ? message.substring(5).trim() : "";
        if (rest.isEmpty()) {
            say(player, "Usage: *mine <block_id> | *mine mode cave|xray | *mine radius <8-64> | *mine stop", false);
            return;
        }
        if (rest.equals("stop")) {
            MineController.cancel(client);
            return;
        }
        if (rest.startsWith("mode ")) {
            String mode = rest.substring(5).trim();
            if (mode.equalsIgnoreCase("cave")) {
                com.example.multifeature.config.ModConfig.INSTANCE.mineMode = MineConfig.Mode.CAVE;
                com.example.multifeature.config.ModConfig.save();
                say(player, "Mine mode: cave (exposed only).", false);
            } else if (mode.equalsIgnoreCase("xray")) {
                com.example.multifeature.config.ModConfig.INSTANCE.mineMode = MineConfig.Mode.XRAY;
                com.example.multifeature.config.ModConfig.save();
                say(player, "Mine mode: xray (all matching blocks).", false);
            } else {
                say(player, "Unknown mode: " + mode + ". Use cave or xray.", true);
            }
            return;
        }
        if (rest.startsWith("radius ")) {
            try {
                int n = Integer.parseInt(rest.substring(7).trim());
                if (n < 8) {
                    n = 8;
                }
                if (n > 64) {
                    n = 64;
                }
                com.example.multifeature.config.ModConfig.INSTANCE.mineScanRadius = n;
                com.example.multifeature.config.ModConfig.save();
                say(player, "Mine radius: " + n + " blocks.", false);
            } catch (NumberFormatException e) {
                say(player, "Bad radius. Use *mine radius <8-64>.", true);
            }
            return;
        }
        Block block = parseBlock(rest);
        if (block == null) {
            say(player, "Unknown block: " + rest + ".", true);
            return;
        }
        MineController.startMining(client, block);
    }

    /** Accepts "diamond_ore" and "minecraft:diamond_ore". Null when unknown. */
    static Block parseBlock(String input) {
        String id = input.contains(":") ? input : "minecraft:" + input;
        Identifier parsed;
        try {
            parsed = Identifier.parse(id);
        } catch (Exception e) {
            return null;
        }
        if (!BuiltInRegistries.BLOCK.keySet().contains(parsed)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(parsed);
        if (block == Blocks.AIR && !parsed.getPath().equals("air")) {
            return null;
        }
        return block;
    }

    private static void say(LocalPlayer player, String text, boolean red) {
        Component msg = Component.literal(text);
        if (red) {
            msg = msg.copy().withStyle(net.minecraft.ChatFormatting.RED);
        }
        player.displayClientMessage(msg, false);
    }
}
