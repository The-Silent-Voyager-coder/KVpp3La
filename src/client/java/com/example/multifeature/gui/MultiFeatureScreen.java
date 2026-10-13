// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Mod GUI: three tabs (Targeting, Mining, Keybinds). Client-only, zero server
 * packets from any path here. All tab state is Screen-scoped and released on
 * close. Tabs are config + status surfaces; nothing here pauses the client.
 */
public class MultiFeatureScreen extends Screen {
    public enum Tab {
        TARGETING,
        MINING,
        KEYBINDS
    }

    private final Tab initialTab;
    private Tab activeTab;
    private TargetingTab targetingTab;
    private MiningTab miningTab;
    private KeybindTab keybindTab;
    private Button targetingButton;
    private Button miningButton;
    private Button keybindsButton;

    public MultiFeatureScreen() {
        this(Tab.TARGETING);
    }

    public MultiFeatureScreen(Tab initialTab) {
        super(Component.literal("MultiFeature"));
        this.initialTab = initialTab;
        this.activeTab = initialTab;
    }

    /** Raw widget registration for tab-owned widgets. Mirrors Screen's bound. */
    public <T extends GuiEventListener & Renderable
            & net.minecraft.client.gui.narration.NarratableEntry> T addWidgetRaw(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        targetingTab = new TargetingTab(this);
        miningTab = new MiningTab(this);
        keybindTab = new KeybindTab(this);
        targetingButton = Button.builder(Component.literal("Targeting"),
                b -> switchTab(Tab.TARGETING)).bounds(width / 2 - 195, 8, 120, 20).build();
        miningButton = Button.builder(Component.literal("Mining"),
                b -> switchTab(Tab.MINING)).bounds(width / 2 - 60, 8, 120, 20).build();
        keybindsButton = Button.builder(Component.literal("Keybinds"),
                b -> switchTab(Tab.KEYBINDS)).bounds(width / 2 + 75, 8, 120, 20).build();
        addRenderableWidget(targetingButton);
        addRenderableWidget(miningButton);
        addRenderableWidget(keybindsButton);
        targetingTab.build(client, width);
        miningTab.build(client, width);
        keybindTab.build(client, width);
        applyTabVisibility();
    }

    private void switchTab(Tab tab) {
        activeTab = tab;
        applyTabVisibility();
    }

    private void applyTabVisibility() {
        targetingTab.setVisible(activeTab == Tab.TARGETING);
        miningTab.setVisible(activeTab == Tab.MINING);
        keybindTab.setVisible(activeTab == Tab.KEYBINDS);
    }

    @Override
    public void tick() {
        super.tick();
        targetingTab.tick();
        miningTab.tick();
        keybindTab.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        super.render(graphics, mouseX, mouseY, tickDelta);
        if (activeTab == Tab.TARGETING) {
            targetingTab.renderHeader(graphics, font, width);
        } else if (activeTab == Tab.MINING) {
            miningTab.renderHeader(graphics, font, width);
        } else {
            keybindTab.renderHeader(graphics, font, width);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeTab == Tab.KEYBINDS) {
            InputConstants.Key key = InputConstants.getKey(event);
            if (keybindTab.handleKeyPress(event.key(), key)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Any click outside the armed confirm resets it (no modal).
        targetingTab.onAnyClick(event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        targetingTab.release();
        miningTab.release();
        keybindTab.release();
        targetingTab = null;
        miningTab = null;
        keybindTab = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
