// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.config.TargetMode;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * One entity row: display name + mode-cycling button.
 * Base type is the tab's Row (see TargetingTab.Row) so headers, entities and
 * player rows share one list. Cycle order comes from TargetMode.next() —
 * WHEN_ANGERED is omitted for non-anger entities (never greyed, just skipped).
 */
public class EntityEntry extends com.example.multifeature.gui.TargetingTab.Row {
    private final String entityId;
    private final String displayName;
    private final boolean angerCapable;
    private final Runnable onChanged;
    private final Button modeButton;

    public EntityEntry(String entityId, String displayName, boolean angerCapable, Runnable onChanged) {
        this.entityId = entityId;
        this.displayName = displayName;
        this.angerCapable = angerCapable;
        this.onChanged = onChanged;
        this.modeButton = Button.builder(modeLabel(current()),
                b -> cycle()).bounds(0, 0, 96, 20).build();
    }

    private TargetMode current() {
        return ModConfig.INSTANCE.targeting.getOrDefault(entityId, TargetMode.OFF);
    }

    private static Component modeLabel(TargetMode mode) {
        if (mode == TargetMode.ALWAYS) {
            return Component.literal("ALWAYS");
        }
        if (mode == TargetMode.WHEN_ANGERED) {
            return Component.literal("WHEN ANGERED");
        }
        return Component.literal("OFF");
    }

    private void cycle() {
        TargetMode next = current().next(angerCapable);
        // Explicit OFF entries are written so the file shows user choices.
        ModConfig.INSTANCE.targeting.put(entityId, next);
        ModConfig.save();
        modeButton.setMessage(modeLabel(next));
        onChanged.run();
    }

    /** Bulk setter. WHEN_ANGERED is applied as-is (means attack whether angered or not). */
    public void setMode(TargetMode mode) {
        ModConfig.INSTANCE.targeting.put(entityId, mode);
        modeButton.setMessage(modeLabel(mode));
    }

    public boolean isAngerCapable() {
        return angerCapable;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();
        graphics.drawString(this.minecraftFont(), displayName, x + 4, y + 6, 0xFFFFFF, false);
        modeButton.setX(getContentRight() - 100);
        modeButton.setY(y);
        modeButton.render(graphics, mouseX, mouseY, tickDelta);
    }

    private net.minecraft.client.gui.Font minecraftFont() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitor.accept(modeButton);
    }

    @Override
    public Component getNarration() {
        return Component.literal(displayName + " " + modeLabel(current()).getString());
    }
}
