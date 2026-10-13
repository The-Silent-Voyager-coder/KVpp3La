// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * One player-name row: username + remove button. Flashes red briefly when a
 * duplicate add targets it (see TargetingTab flash support).
 */
public class PlayerEntry extends com.example.multifeature.gui.TargetingTab.Row {
    private final String name;
    private final Runnable onRemove;
    private final Button removeButton;
    private long flashUntilMs;

    public PlayerEntry(String name, Runnable onRemove) {
        this.name = name;
        this.onRemove = onRemove;
        this.removeButton = Button.builder(Component.literal("✕"),
                b -> onRemove.run()).bounds(0, 0, 20, 20).build();
    }

    public String name() {
        return name;
    }

    public void flash() {
        flashUntilMs = System.currentTimeMillis() + 1200;
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();
        boolean flashing = System.currentTimeMillis() < flashUntilMs;
        int color = flashing ? 0xFF6060 : 0xFFFFFF;
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                name, x + 4, y + 6, color, false);
        removeButton.setX(getContentRight() - 24);
        removeButton.setY(y);
        removeButton.render(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitor.accept(removeButton);
    }

    @Override
    public Component getNarration() {
        return Component.literal(name);
    }
}
