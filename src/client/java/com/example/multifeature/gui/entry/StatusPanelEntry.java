// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import com.example.multifeature.mine.MineController;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * Single-row status panel. Read-only text, refreshed by MiningTab.tick()
 * from MineController getters. Never writes controller state.
 */
public class StatusPanelEntry extends ObjectSelectionList.Entry<StatusPanelEntry> {
    private String[] lines = new String[0];

    public void refresh() {
        MineController.State state = MineController.getState();
        String target = MineController.getCurrentTarget() == null
                ? "(none)" : MineController.getCurrentTarget().toShortString();
        String block = MineController.getTargetBlock() == null ? "—"
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(MineController.getTargetBlock()).toString();
        long agoMs = System.currentTimeMillis() - MineController.getLastScanMs();
        String lastScan = MineController.getLastScanMs() == 0 ? "—"
                : (agoMs / 1000) + "s ago — " + MineController.getLastScanCount() + " blocks found";
        lines = new String[]{
                "Mining Status",
                "State: " + titleOf(state),
                "Current target: " + target,
                "Target block: " + block,
                "Last scan: " + lastScan,
        };
    }

    private static String titleOf(MineController.State state) {
        if (state == MineController.State.IDLE) {
            return "Idle";
        }
        if (state == MineController.State.SCANNING) {
            return "Scanning";
        }
        if (state == MineController.State.PATHING) {
            return "Pathing";
        }
        if (state == MineController.State.MINING) {
            return "Mining";
        }
        if (state == MineController.State.PAUSED) {
            return "Paused";
        }
        return "Done";
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        Font font = Minecraft.getInstance().font;
        int x = getContentX() + 4;
        int y = getContentY() + 2;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x, y + i * 11, i == 0 ? 0xFFFFA0 : 0xE0E0E0, false);
        }
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
    }

    @Override
    public Component getNarration() {
        return Component.literal("Mining status");
    }
}
