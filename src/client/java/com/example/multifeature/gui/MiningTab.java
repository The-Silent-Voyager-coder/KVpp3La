// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.gui.entry.QuickActionRowEntry;
import com.example.multifeature.gui.entry.StatusPanelEntry;
import com.example.multifeature.mine.BlockScanner;
import com.example.multifeature.mine.MineConfig;
import com.example.multifeature.mine.MineController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Tab 3: mining master toggle, mode, auto-equip, radius, live status panel,
 * test scan, quick actions, honest footer. Configuration + status only — the
 * tab never pauses the client; mining keeps running while open so state
 * transitions are watchable live. All state Screen-scoped, released on close.
 */
public class MiningTab {
    private static class StatusList extends ObjectSelectionList<StatusPanelEntry> {
        StatusList(Minecraft client, int width, int height, int y0, int y1) {
            super(client, width, height, y0, y1);
        }

        public void addRow(StatusPanelEntry entry) {
            addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }
    }

    private static class ActionList extends ObjectSelectionList<QuickActionRowEntry> {
        ActionList(Minecraft client, int width, int height, int y0, int y1) {
            super(client, width, height, y0, y1);
        }

        public void addRow(QuickActionRowEntry entry) {
            addEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }
    }

    private final MultiFeatureScreen screen;
    private Button enableButton;
    private Button caveButton;
    private Button xrayButton;
    private Button autoEquipButton;
    private EditBox radiusField;
    private EditBox testField;
    private Button testButton;
    private StatusList statusList;
    private StatusPanelEntry statusEntry;
    private ActionList actionList;
    private boolean radiusWasFocused;
    private String pickaxeLine = "";
    private String chunksLine = "";
    private String testReport = "";
    private boolean testReportOk;

    public MiningTab(MultiFeatureScreen screen) {
        this.screen = screen;
    }

    public void build(Minecraft client, int width) {
        int listW = Math.min(460, width - 20);
        int listX = (width - listW) / 2;

        enableButton = Button.builder(enableLabel(), b -> {
            ModConfig.INSTANCE.mineEnabled = !ModConfig.INSTANCE.mineEnabled;
            if (!ModConfig.INSTANCE.mineEnabled) {
                MineController.cancel(client);
            }
            ModConfig.save();
            enableButton.setMessage(enableLabel());
        }).bounds(listX, 36, listW, 20).build();
        enableButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Allows *mine, *c, *p, *up. Turn off to disable the command entirely without restarting.")));
        screen.addWidgetRaw(enableButton);

        int hw = (listW - 6) / 2;
        caveButton = Button.builder(modeLabel(MineConfig.Mode.CAVE), b -> setMode(MineConfig.Mode.CAVE))
                .bounds(listX, 60, hw, 20).build();
        xrayButton = Button.builder(modeLabel(MineConfig.Mode.XRAY), b -> setMode(MineConfig.Mode.XRAY))
                .bounds(listX + hw + 6, 60, hw, 20).build();
        screen.addWidgetRaw(caveButton);
        screen.addWidgetRaw(xrayButton);

        autoEquipButton = Button.builder(autoEquipLabel(), b -> {
            ModConfig.INSTANCE.mineAutoEquipPickaxe = !ModConfig.INSTANCE.mineAutoEquipPickaxe;
            ModConfig.save();
            autoEquipButton.setMessage(autoEquipLabel());
        }).bounds(listX, 96, listW, 20).build();
        autoEquipButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                "Switches your held slot when mining starts. Hotbar only — does not touch main inventory.")));
        screen.addWidgetRaw(autoEquipButton);

        radiusField = new EditBox(client.font, listX, 120, 80, 20, Component.literal("Radius"));
        radiusField.setHint(Component.literal("Radius 8-64"));
        radiusField.setMaxLength(3);
        radiusField.setValue(String.valueOf(ModConfig.INSTANCE.mineScanRadius));
        radiusWasFocused = false;
        screen.addWidgetRaw(radiusField);

        testField = new EditBox(client.font, listX, 170, listW - 110, 20, Component.literal("Block id"));
        testField.setHint(Component.literal("minecraft:diamond_ore"));
        testField.setValue("minecraft:diamond_ore");
        screen.addWidgetRaw(testField);
        testButton = Button.builder(Component.literal("Test scan"), b -> runTestScan())
                .bounds(listX + listW - 104, 170, 104, 20).build();
        testButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Cancel current operation first.")));
        screen.addWidgetRaw(testButton);

        statusList = new StatusList(client, listW, 110, 194, 304);
        statusList.setX(listX);
        statusEntry = new StatusPanelEntry();
        statusEntry.refresh();
        statusList.addRow(statusEntry);
        screen.addWidgetRaw(statusList);

        actionList = new ActionList(client, listW, 26, 308, 334);
        actionList.setX(listX);
        actionList.addRow(new QuickActionRowEntry(() -> parseBlockId(testField.getValue())));
        screen.addWidgetRaw(actionList);

        refreshLines(client);
    }

    private Component enableLabel() {
        return Component.literal((ModConfig.INSTANCE.mineEnabled ? "[x] " : "[ ] ") + "Enable mining command");
    }

    private Component modeLabel(MineConfig.Mode mode) {
        boolean selected = ModConfig.INSTANCE.mineMode == mode;
        return Component.literal((selected ? "(•) " : "( ) ") + (mode == MineConfig.Mode.CAVE ? "Cave" : "Xray"));
    }

    private void setMode(MineConfig.Mode mode) {
        ModConfig.INSTANCE.mineMode = mode;
        ModConfig.save();
        caveButton.setMessage(modeLabel(MineConfig.Mode.CAVE));
        xrayButton.setMessage(modeLabel(MineConfig.Mode.XRAY));
    }

    private Component autoEquipLabel() {
        return Component.literal((ModConfig.INSTANCE.mineAutoEquipPickaxe ? "[x] " : "[ ] ")
                + "Auto-equip best pickaxe from hotbar");
    }

    private void runTestScan() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) {
            return;
        }
        MineController.State state = MineController.getState();
        if (state != MineController.State.IDLE && state != MineController.State.DONE) {
            testReport = "Cancel current operation first.";
            testReportOk = false;
            return;
        }
        Block block = parseBlockId(testField.getValue().trim());
        if (block == null) {
            testReport = "Unknown block: " + testField.getValue().trim() + ".";
            testReportOk = false;
            return;
        }
        MineController.TestReport report = MineController.testScan(client, block);
        testReport = report.text;
        testReportOk = report.ok;
    }

    private Block parseBlockId(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String id = input.contains(":") ? input : "minecraft:" + input;
        net.minecraft.resources.Identifier parsed;
        try {
            parsed = net.minecraft.resources.Identifier.parse(id);
        } catch (Exception e) {
            return null;
        }
        if (!net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet().contains(parsed)) {
            return null;
        }
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(parsed);
        if (block == Blocks.AIR && !parsed.getPath().equals("air")) {
            return null;
        }
        return block;
    }

    private void refreshLines(Minecraft client) {
        // Pickaxe in hotbar (polled — covers tab-open + inventory changes).
        String pick = "No — mining will fail.";
        if (client.player != null) {
            for (int slot = 0; slot <= 8; slot++) {
                ItemStack stack = client.player.getInventory().getItem(slot);
                if (!stack.isEmpty() && stack.is(ItemTags.PICKAXES)) {
                    String name = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem()).toString();
                    pick = "Yes (" + name + ", slot " + (slot + 1) + ")";
                    break;
                }
            }
        }
        pickaxeLine = "Pickaxe in hotbar: " + pick;
        // Loaded vs total chunks in radius.
        if (client.player != null && client.level != null) {
            int radius = ModConfig.INSTANCE.mineScanRadius;
            int cr = (radius >> 4) + 2;
            BlockPos center = client.player.blockPosition();
            int total = (2 * cr + 1) * (2 * cr + 1);
            int loaded = 0;
            for (int cx = (center.getX() >> 4) - cr; cx <= (center.getX() >> 4) + cr; cx++) {
                for (int cz = (center.getZ() >> 4) - cr; cz <= (center.getZ() >> 4) + cr; cz++) {
                    if (client.level.getChunk(cx, cz,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) {
                        loaded++;
                    }
                }
            }
            chunksLine = "Loaded chunks: " + loaded + " / " + total
                    + (loaded == total ? " (all loaded)" : " (some unloaded)");
        } else {
            chunksLine = "Loaded chunks: —";
        }
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        // Radius blur validation: committed when the field loses focus.
        if (radiusField.isFocused()) {
            radiusWasFocused = true;
        } else if (radiusWasFocused) {
            radiusWasFocused = false;
            try {
                int n = Integer.parseInt(radiusField.getValue().trim());
                if (n < 8) {
                    n = 8;
                }
                if (n > 64) {
                    n = 64;
                }
                ModConfig.INSTANCE.mineScanRadius = n;
                ModConfig.save();
            } catch (NumberFormatException e) {
                // Non-numeric ignored, field keeps showing the bad input.
            }
            radiusField.setValue(String.valueOf(ModConfig.INSTANCE.mineScanRadius));
        }
        statusEntry.refresh();
        if (client != null) {
            refreshLines(client);
        }
    }

    public void renderHeader(GuiGraphics graphics, Font font, int width) {
        int listX = Math.max(10, (width - Math.min(460, width - 20)) / 2);
        String sub = ModConfig.INSTANCE.mineMode == MineConfig.Mode.CAVE
                ? "Only mines blocks adjacent to air. Safer on anti-xray servers."
                : "Mines any matching block. Requires the block to already be loaded.";
        graphics.drawString(font, sub, listX, 82, 0xA0A0A0, false);
        graphics.drawString(font, "Radius (blocks, 8-64):", listX, 110, 0xE0E0E0, false);
        graphics.drawString(font, pickaxeLine, listX, 142, 0xE0E0E0, false);
        graphics.drawString(font, chunksLine, listX, 153, 0xE0E0E0, false);
        if (!testReport.isEmpty()) {
            graphics.drawString(font, testReport, listX, 336,
                    testReportOk ? 0x60FF60 : 0xFF6060, false);
        }
        // Footer: condensed honest block (full verbatim text lives in README —
        // the complete 20-line statement does not fit a 480px tab).
        int fy = 352;
        graphics.drawString(font, "Walks straight lines; jumps 1 block; mines blockers. No A* — unreachable ores get blacklisted, not solved.", listX, fy, 0x808080, false);
        graphics.drawString(font, "Singleplayer / LAN / own servers only. Movement anti-cheats (Grim, Hawk, Matrix, Spartan) will flag it.", listX, fy + 11, 0x808080, false);
        graphics.drawString(font, "Full statement in README (Mining tab section). This mod is not Baritone.", listX, fy + 22, 0x808080, false);
    }

    public void setVisible(boolean visible) {
        enableButton.visible = visible;
        caveButton.visible = visible;
        xrayButton.visible = visible;
        autoEquipButton.visible = visible;
        radiusField.visible = visible;
        testField.visible = visible;
        testButton.visible = visible;
        statusList.visible = visible;
        actionList.visible = visible;
    }

    public void release() {
        statusList = null;
        statusEntry = null;
        actionList = null;
    }
}
