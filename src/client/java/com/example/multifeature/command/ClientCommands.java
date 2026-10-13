// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.command;

import com.example.multifeature.gui.MultiFeatureScreen;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client commands. All open the same GUI, Targeting tab default, in silence
 * (no chat feedback on success). /kapgui is the plain entry point; /KVpp3La
 * (+ lowercase alias /kvpp3la — Brigadier literals are case-sensitive) is the
 * signature/brand form. No other case variants are registered.
 *
 * Client-side only: the handlers only schedule setScreen on the main thread.
 * If a server also defines /kapgui, the client registration wins for locally
 * typed text (see README "Commands").
 */
public final class ClientCommands {
    private ClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("kapgui")
                    .executes(ClientCommands::openScreen));
            dispatcher.register(ClientCommandManager.literal("KVpp3La")
                    .executes(ClientCommands::openScreen));
            dispatcher.register(ClientCommandManager.literal("kvpp3la")
                    .executes(ClientCommands::openScreen));
        });
    }

    private static int openScreen(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            context.getSource().sendError(Component.literal("Could not open MultiFeature screen."));
            return 0;
        }
        minecraft.execute(() -> minecraft.setScreen(new MultiFeatureScreen()));
        return Command.SINGLE_SUCCESS;
    }
}
