// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Click-to-expand category header. Whole row is clickable (no child widgets).
 * Chevron: ▼ expanded, ▶ collapsed.
 */
public class CategoryHeaderEntry extends com.example.multifeature.gui.TargetingTab.Row {
    private final String title;
    private final int count;
    private boolean expanded;
    private final Runnable onToggle;

    public CategoryHeaderEntry(String title, int count, boolean expanded, Runnable onToggle) {
        this.title = title;
        this.count = count;
        this.expanded = expanded;
        this.onToggle = onToggle;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();
        String chevron = expanded ? "▼ " : "▶ ";
        int color = hovered ? 0xFFFFA0 : 0xE0E0E0;
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                chevron + title + " (" + count + ")", x + 4, y + 6, color, false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isMouseOver(event.x(), event.y())) {
            onToggle.run();
            return true;
        }
        return false;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
    }

    @Override
    public Component getNarration() {
        return Component.literal(title);
    }
}
