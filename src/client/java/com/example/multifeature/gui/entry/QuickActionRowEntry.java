// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import com.example.multifeature.mine.MineController;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * One row with four buttons mirroring the chat commands. No duplicated logic:
 * every button calls the same MineController method the command handler calls
 * (start/pause/resume/cancel). Enable states refresh in renderContent from
 * the same getters the status panel uses.
 */
public class QuickActionRowEntry extends ObjectSelectionList.Entry<QuickActionRowEntry> {
    private final Button startButton;
    private final Button pauseButton;
    private final Button resumeButton;
    private final Button cancelButton;
    private final Supplier<Block> defaultBlock;

    public QuickActionRowEntry(Supplier<Block> defaultBlock) {
        this.defaultBlock = defaultBlock;
        startButton = Button.builder(Component.literal("Start"), b -> onStart())
                .bounds(0, 0, 70, 20).build();
        pauseButton = Button.builder(Component.literal("Pause"), b -> onPause())
                .bounds(0, 0, 60, 20).build();
        resumeButton = Button.builder(Component.literal("Resume"), b -> onResume())
                .bounds(0, 0, 70, 20).build();
        cancelButton = Button.builder(Component.literal("Cancel"), b -> onCancel())
                .bounds(0, 0, 60, 20).build();
    }

    private void onStart() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        MineController.State state = MineController.getState();
        if (state != MineController.State.IDLE && state != MineController.State.DONE) {
            return;
        }
        Block block = defaultBlock.get();
        if (block != null) {
            MineController.startMining(client, block);
        }
    }

    private void onPause() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            MineController.pause(client);
        }
    }

    private void onResume() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            MineController.resume(client);
        }
    }

    private void onCancel() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            MineController.cancel(client);
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        MineController.State state = MineController.getState();
        startButton.active = state == MineController.State.IDLE || state == MineController.State.DONE;
        pauseButton.active = state != MineController.State.IDLE
                && state != MineController.State.DONE && state != MineController.State.PAUSED;
        resumeButton.active = state == MineController.State.PAUSED;
        cancelButton.active = state != MineController.State.IDLE;
        int y = getContentY();
        int x = getContentX() + 4;
        startButton.setX(x);
        startButton.setY(y);
        startButton.render(graphics, mouseX, mouseY, tickDelta);
        pauseButton.setX(x + 74);
        pauseButton.setY(y);
        pauseButton.render(graphics, mouseX, mouseY, tickDelta);
        resumeButton.setX(x + 138);
        resumeButton.setY(y);
        resumeButton.render(graphics, mouseX, mouseY, tickDelta);
        cancelButton.setX(x + 212);
        cancelButton.setY(y);
        cancelButton.render(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitor.accept(startButton);
        visitor.accept(pauseButton);
        visitor.accept(resumeButton);
        visitor.accept(cancelButton);
    }

    @Override
    public Component getNarration() {
        return Component.literal("Mining quick actions");
    }
}
