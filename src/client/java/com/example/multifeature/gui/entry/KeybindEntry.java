// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui.entry;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

/**
 * One rebindable keybind row: name | current key (click arms capture) | Reset.
 * Capture arming lives on the entry; the Screen routes keyPressed into the
 * armed entry. Escape cancels. Reset restores the default key.
 * Armed capture auto-times out after 15 s so a missed keypress never leaves
 * the row waiting forever.
 */
public class KeybindEntry extends ObjectSelectionList.Entry<KeybindEntry> {
    /** Max time an armed row waits for the next keypress. */
    public static final long ARM_TIMEOUT_MS = 15_000;
    private final KeyMapping mapping;
    private final InputConstants.Key defaultKey;
    private final Runnable onChanged;
    private final Button keyButton;
    private final Button resetButton;
    private boolean armed;
    private long armedAtMs;

    public KeybindEntry(KeyMapping mapping, Runnable onChanged) {
        this.mapping = mapping;
        this.defaultKey = mapping.getDefaultKey();
        this.onChanged = onChanged;
        this.keyButton = Button.builder(currentLabel(), b -> arm()).bounds(0, 0, 110, 20).build();
        this.resetButton = Button.builder(Component.literal("Reset"), b -> reset()).bounds(0, 0, 50, 20).build();
    }

    private Component currentLabel() {
        if (armed) {
            return Component.literal("> Press a key... <");
        }
        return mapping.getTranslatedKeyMessage();
    }

    private void arm() {
        armed = true;
        armedAtMs = System.currentTimeMillis();
        keyButton.setMessage(currentLabel());
    }

    public void disarm() {
        if (armed) {
            armed = false;
            keyButton.setMessage(currentLabel());
        }
    }

    public boolean isArmed() {
        return armed;
    }

    /** True when armed longer than ARM_TIMEOUT_MS — caller should disarm. */
    public boolean isTimedOut(long nowMs) {
        return armed && nowMs - armedAtMs >= ARM_TIMEOUT_MS;
    }

    /** Per-tick timeout poll; returns true if it auto-disarmed. */
    public boolean tickTimeout(long nowMs) {
        if (isTimedOut(nowMs)) {
            disarm();
            return true;
        }
        return false;
    }

    /** Returns true when the key was consumed (including Escape-cancel). */
    public boolean capture(InputConstants.Key key) {
        if (!armed) {
            return false;
        }
        if (key == InputConstants.UNKNOWN) {
            disarm();
            return true;
        }
        mapping.setKey(key);
        KeyMapping.resetMapping();
        disarm();
        onChanged.run();
        return true;
    }

    private void reset() {
        disarm();
        mapping.setKey(defaultKey);
        KeyMapping.resetMapping();
        keyButton.setMessage(currentLabel());
        onChanged.run();
    }

    public String mappingName() {
        return mapping.getName();
    }

    @Override
    public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        int x = getContentX();
        int y = getContentY();
        int right = getContentRight();
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                Component.translatable(mapping.getName()), x + 4, y + 6, 0xFFFFFF, false);
        resetButton.setX(right - 54);
        resetButton.setY(y);
        resetButton.render(graphics, mouseX, mouseY, tickDelta);
        keyButton.setX(right - 54 - 114);
        keyButton.setY(y);
        keyButton.render(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) {
        visitor.accept(keyButton);
        visitor.accept(resetButton);
    }

    @Override
    public Component getNarration() {
        return Component.translatable(mapping.getName());
    }
}
