// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature;

import com.example.multifeature.command.ClientCommands;
import com.example.multifeature.config.ModConfig;
import com.example.multifeature.gui.MultiFeatureScreen;
import com.example.multifeature.keybind.KeybindManager;
import com.example.multifeature.mine.MineCommand;
import com.example.multifeature.mine.MineController;
import com.example.multifeature.render.HealthOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Client-only entrypoint (see fabric.mod.json environment=client).
 * Registers config, keybinds, and one END_CLIENT_TICK handler.
 */
public class MultiFeatureMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfig.load();
        KeybindManager.register();
        ClientCommands.register();
        MineCommand.register();
        HudRenderCallback.EVENT.register(HealthOverlayRenderer::onHudRender);
        ClientTickEvents.END_CLIENT_TICK.register(TickHandler::onEndTick);
        // Menu-key poll deliberately kept out of TickHandler (combat loop). See KeybindManager for the binding itself.
        ClientTickEvents.END_CLIENT_TICK.register(MultiFeatureMod::pollMenuKey);
        ClientTickEvents.END_CLIENT_TICK.register(MineController::tick);
    }

    private static void pollMenuKey(Minecraft client) {
        if (client.player == null) {
            return;
        }
        while (KeybindManager.openMenuKey.consumeClick()) {
            client.setScreen(new MultiFeatureScreen());
        }
    }
}
