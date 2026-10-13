// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.gui.entry.KeybindEntry;
import com.example.multifeature.keybind.KeybindManager;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;

/**
 * Tab 2: keybind rebinding rows (name | current key | Reset).
 * Click a key button → "Press a key..." → next key press rebinds.
 * Escape cancels. Config is re-saved on every change.
 *
 * Note: vanilla Controls (Options → Controls → Key Binds) also lists these
 * keybinds because they are registered via KeyBindingHelper. That is
 * expected — both screens edit the same KeyMapping objects. Do not remove
 * one thinking it is redundant.
 */
public class KeybindTab {
    private static class KeybindList extends ObjectSelectionList<KeybindEntry> {
        KeybindList(Minecraft client, int width, int height, int y0, int y1) {
            super(client, width, height, y0, y1);
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }

        public void addRow(KeybindEntry entry) {
            addEntry(entry);
        }
    }

    private final MultiFeatureScreen screen;
    private KeybindList list;
    private final List<KeybindEntry> entries = new ArrayList<>();

    public KeybindTab(MultiFeatureScreen screen) {
        this.screen = screen;
    }

    public void build(Minecraft client, int width) {
        int listW = Math.min(460, width - 20);
        int listX = (width - listW) / 2;
        list = new KeybindList(client, listW, 300, 60, 360);
        list.setX(listX);
        entries.clear();
        for (KeyMapping mapping : KeybindManager.all) {
            if (mapping == null) {
                continue;
            }
            KeybindEntry entry = new KeybindEntry(mapping, ModConfig::save);
            entries.add(entry);
            list.addRow(entry);
        }
        screen.addWidgetRaw(list);
    }

    /** Route a key press into the armed rebind row. True = consumed. */
    public boolean handleKeyPress(int keyCode, InputConstants.Key key) {
        for (KeybindEntry entry : entries) {
            if (entry.isArmed()) {
                if (keyCode == InputConstants.KEY_ESCAPE) {
                    entry.disarm();
                    return true;
                }
                return entry.capture(key);
            }
        }
        return false;
    }

    /** Per-screen tick: auto-disarm rows armed longer than 15 s. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (KeybindEntry entry : entries) {
            entry.tickTimeout(now);
        }
    }

    public void renderHeader(GuiGraphics graphics, net.minecraft.client.gui.Font font, int width) {
        String text = "Click a key, then press the new binding. Escape cancels.";
        graphics.drawString(font, text, (width - font.width(text)) / 2, 44, 0xA0A0A0, false);
    }

    public void setVisible(boolean visible) {
        list.visible = visible;
    }

    public void release() {
        entries.clear();
        list = null;
    }
}
