// Done by https://github.com/anomalyco/opencode for https://github.com/anomalyco/opencode
package com.example.multifeature.gui;

import com.example.multifeature.config.ModConfig;
import com.example.multifeature.config.PlayerMode;
import com.example.multifeature.config.TargetMode;
import com.example.multifeature.gui.entry.CategoryHeaderEntry;
import com.example.multifeature.gui.entry.EntityEntry;
import com.example.multifeature.gui.entry.PlayerEntry;
import java.text.Collator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/**
 * Tab 1: player submenu + searchable entity tree + bulk actions.
 * All state is Screen-scoped: built on open, released on close. No tick cost
 * when closed; no persistent GUI state anywhere.
 */
public class TargetingTab {
    /** Shared row base so headers, entities and player rows share one list. */
    public abstract static class Row extends ObjectSelectionList.Entry<Row> {
    }

    private static class TreeList extends ObjectSelectionList<Row> {
        TreeList(Minecraft client, int width, int height, int y0, int y1) {
            super(client, width, height, y0, y1);
        }

        public void addRow(Row row) {
            addEntry(row);
        }

        public void clearRows() {
            clearEntries();
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }
    }

    private static class NamesList extends ObjectSelectionList<Row> {
        NamesList(Minecraft client, int width, int height, int y0, int y1) {
            super(client, width, height, y0, y1);
        }

        public void addRow(Row row) {
            addEntry(row);
        }

        public void clearRows() {
            clearEntries();
        }

        @Override
        public int getRowWidth() {
            return width - 12;
        }
    }

    private static final class EntityDef {
        final String id;
        final String name;
        final boolean angerCapable;
        final TargetMode.Category category;

        EntityDef(String id, String name, boolean angerCapable, TargetMode.Category category) {
            this.id = id;
            this.name = name;
            this.angerCapable = angerCapable;
            this.category = category;
        }
    }

    private final MultiFeatureScreen screen;
    private final List<EntityDef> allDefs = new ArrayList<>();
    private final Map<TargetMode.Category, Boolean> expanded = new EnumMap<>(TargetMode.Category.class);
    private TreeList tree;
    private NamesList names;
    private EditBox searchBox;
    private EditBox nameField;
    private Button playerModeButton;
    private Button bulkVisibleAlways;
    private Button bulkVisibleAngered;
    private Button bulkVisibleOff;
    private Button bulkAllAlways;
    private Button checkLOSButton;
    private Button healthOverlayButton;
    private Button measuredTTKButton;    private String filterText = "";
    private String statusText = "";
    private long statusUntilMs;
    private long lastSearchEditMs;
    private boolean searchDirty;
    private String appliedFilter = "";
    private boolean confirmArmed;
    private long confirmUntilMs;
    private int registeredCount;

    public TargetingTab(MultiFeatureScreen screen) {
        this.screen = screen;
        expanded.put(TargetMode.Category.HOSTILE, true);
        expanded.put(TargetMode.Category.NEUTRAL, true);
        expanded.put(TargetMode.Category.PASSIVE, false);
        expanded.put(TargetMode.Category.UTILITY, false);
        expanded.put(TargetMode.Category.OTHER, false);
    }

    /** Enumerate every registered entity once, at Screen-open time. */
    public void build(Minecraft client, int width) {
        allDefs.clear();
        Collator collator = Collator.getInstance(Locale.getDefault());
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) {
                continue;
            }
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            String name = type.getDescription().getString();
            boolean angerCapable = TargetMode.isAngerCapable(id);
            TargetMode.Category category = TargetMode.categorize(id, type.getCategory());
            allDefs.add(new EntityDef(id, name, angerCapable, category));
        }
        registeredCount = allDefs.size();
        allDefs.sort((a, b) -> {
            int c = a.category.compareTo(b.category);
            if (c != 0) {
                return c;
            }
            return collator.compare(a.name, b.name);
        });

        int listW = Math.min(460, width - 20);
        int listX = (width - listW) / 2;

        playerModeButton = Button.builder(playerModeLabel(),
                b -> cyclePlayerMode()).bounds(listX, 36, 220, 20).build();
        screen.addWidgetRaw(playerModeButton);

        names = new NamesList(client, listW, 66, 62, 128);
        names.setX(listX);
        screen.addWidgetRaw(names);

        nameField = new EditBox(client.font, listX, 132, listW - 64, 20, Component.literal("Player name"));
        nameField.setHint(Component.literal("Player name (1-16 chars)"));
        nameField.setMaxLength(16);
        screen.addWidgetRaw(nameField);
        Button addButton = Button.builder(Component.literal("Add"),
                b -> addPlayerName()).bounds(listX + listW - 60, 132, 60, 20).build();
        addButtonRef = addButton;
        screen.addWidgetRaw(addButton);

        searchBox = new EditBox(client.font, listX, 158, listW, 20, Component.literal("Search entities"));
        searchBox.setHint(Component.literal("Search entities"));
        searchBox.setResponder(s -> {
            filterText = s;
            lastSearchEditMs = System.currentTimeMillis();
            searchDirty = true;
        });
        screen.addWidgetRaw(searchBox);

        tree = new TreeList(client, listW, 116, 184, 300);
        tree.setX(listX);
        screen.addWidgetRaw(tree);

        int bw = (listW - 18) / 4;
        int by = 380;
        // Global overlay toggles above the checkLOS row. Labels read config at
        // build time (Screen-open); toggles save immediately.
        healthOverlayButton = Button.builder(healthOverlayLabel(), b -> toggleHealthOverlay())
                .bounds(listX, by - 76, listW, 20).build();
        healthOverlayButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Render hearts and kill-time estimate above your current Kill Aura target.")));
        screen.addWidgetRaw(healthOverlayButton);
        measuredTTKButton = Button.builder(measuredTTKLabel(), b -> toggleMeasuredTTK())
                .bounds(listX, by - 52, listW, 20).build();
        measuredTTKButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Show the measured \"Last: X.Xs\" line when a kill was recorded.")));
        screen.addWidgetRaw(measuredTTKButton);
        // Global checkLOS toggle (Wurst-style): above the bulk row, not per-entity.
        checkLOSButton = Button.builder(checkLOSLabel(), b -> toggleCheckLOS())
                .bounds(listX, by - 28, listW, 20).build();
        checkLOSButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Ensures that you don't reach through blocks when attacking.")));
        screen.addWidgetRaw(checkLOSButton);
        bulkVisibleAlways = Button.builder(Component.literal("Visible→ALWAYS"),
                b -> bulkSet(false, TargetMode.ALWAYS)).bounds(listX, by, bw, 20).build();
        bulkVisibleAngered = Button.builder(Component.literal("Visible→ANGERED"),
                b -> bulkSet(false, TargetMode.WHEN_ANGERED)).bounds(listX + bw + 6, by, bw, 20).build();
        bulkVisibleOff = Button.builder(Component.literal("Visible→OFF"),
                b -> bulkSet(false, TargetMode.OFF)).bounds(listX + 2 * (bw + 6), by, bw, 20).build();
        bulkAllAlways = Button.builder(Component.literal("ALL→ALWAYS"),
                b -> bulkAllAlwaysClick()).bounds(listX + 3 * (bw + 6), by, bw, 20).build();
        bulkVisibleAlways.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Applies to expanded + filtered rows only")));
        bulkVisibleAngered.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Applies to expanded + filtered rows only")));
        bulkVisibleOff.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Applies to expanded + filtered rows only")));
        screen.addWidgetRaw(bulkVisibleAlways);
        screen.addWidgetRaw(bulkVisibleAngered);
        screen.addWidgetRaw(bulkVisibleOff);
        screen.addWidgetRaw(bulkAllAlways);

        rebuildNames();
        rebuildTree();
        updateVisibility();
    }

    private Component healthOverlayLabel() {
        return Component.literal((ModConfig.INSTANCE.showHealthOverlay ? "[x] " : "[ ] ")
                + "Show health & TTK above target");
    }

    private void toggleHealthOverlay() {
        ModConfig.INSTANCE.showHealthOverlay = !ModConfig.INSTANCE.showHealthOverlay;
        ModConfig.save();
        healthOverlayButton.setMessage(healthOverlayLabel());
    }

    private Component measuredTTKLabel() {
        return Component.literal((ModConfig.INSTANCE.showMeasuredTTK ? "[x] " : "[ ] ")
                + "Show measured \"Last\" TTK line");
    }

    private void toggleMeasuredTTK() {
        ModConfig.INSTANCE.showMeasuredTTK = !ModConfig.INSTANCE.showMeasuredTTK;
        ModConfig.save();
        measuredTTKButton.setMessage(measuredTTKLabel());
    }

    private Component checkLOSLabel() {        return Component.literal((ModConfig.INSTANCE.killAuraCheckLOS ? "[X] " : "[ ] ")
                + "Check line of sight");
    }

    private void toggleCheckLOS() {
        ModConfig.INSTANCE.killAuraCheckLOS = !ModConfig.INSTANCE.killAuraCheckLOS;
        ModConfig.save();
        checkLOSButton.setMessage(checkLOSLabel());
    }

    private Component playerModeLabel() {        PlayerMode mode = ModConfig.INSTANCE.playerMode;
        return Component.literal("Players: " + mode.name());
    }

    private void cyclePlayerMode() {
        ModConfig.INSTANCE.playerMode = ModConfig.INSTANCE.playerMode.next();
        ModConfig.save();
        playerModeButton.setMessage(playerModeLabel());
        updateVisibility();
    }

    private boolean namesVisible() {
        return ModConfig.INSTANCE.playerMode == PlayerMode.BY_NAME;
    }

    private void updateVisibility() {
        boolean show = namesVisible();
        names.visible = show;
        nameField.visible = show;
        updateAddButtonVisibility(show);
    }

    private Button addButtonRef;

    private void updateAddButtonVisibility(boolean show) {
        if (addButtonRef != null) {
            addButtonRef.visible = show;
        }
    }

    private void addPlayerName() {
        String raw = nameField.getValue().trim();
        if (raw.length() < 1 || raw.length() > 16) {
            setStatus("Name must be 1-16 characters");
            return;
        }
        for (PlayerEntry row : currentNameRows()) {
            if (row.name().equalsIgnoreCase(raw)) {
                row.flash();
                setStatus("Already in list: " + row.name());
                return;
            }
        }
        ModConfig.INSTANCE.playerNames.add(raw);
        ModConfig.save();
        nameField.setValue("");
        rebuildNames();
    }

    private List<PlayerEntry> currentNameRows() {
        List<PlayerEntry> out = new ArrayList<>();
        for (Row row : names.children()) {
            if (row instanceof PlayerEntry) {
                out.add((PlayerEntry) row);
            }
        }
        return out;
    }

    private void rebuildNames() {
        names.clearRows();
        for (String name : ModConfig.INSTANCE.playerNames) {
            names.addRow(new PlayerEntry(name, () -> {
                ModConfig.INSTANCE.playerNames.removeIf(s -> s.equalsIgnoreCase(name));
                ModConfig.save();
                rebuildNames();
            }));
        }
    }

    /** Visible set = expanded categories + current filter. */
    private List<EntityEntry> visibleEntries() {
        List<EntityEntry> out = new ArrayList<>();
        for (Row row : tree.children()) {
            if (row instanceof EntityEntry) {
                out.add((EntityEntry) row);
            }
        }
        return out;
    }

    private boolean matchesFilter(EntityDef def) {
        if (appliedFilter.isEmpty()) {
            return true;
        }
        return def.name.toLowerCase(Locale.getDefault())
                .contains(appliedFilter.toLowerCase(Locale.getDefault()));
    }

    private void rebuildTree() {
        tree.clearRows();
        for (TargetMode.Category category : TargetMode.Category.values()) {
            List<EntityDef> inCategory = new ArrayList<>();
            for (EntityDef def : allDefs) {
                if (def.category == category && matchesFilter(def)) {
                    inCategory.add(def);
                }
            }
            boolean isExpanded = expanded.getOrDefault(category, false);
            tree.addRow(new CategoryHeaderEntry(category.name() + " [" + inCategory.size() + "]",
                    inCategory.size(), isExpanded, () -> {
                        expanded.put(category, !expanded.getOrDefault(category, false));
                        rebuildTree();
                    }));
            if (!isExpanded) {
                continue;
            }
            for (EntityDef def : inCategory) {
                tree.addRow(new EntityEntry(def.id, def.name, def.angerCapable,
                        () -> updateCounts()));
            }
        }
        updateCounts();
    }

    private void updateCounts() {
        // Count header is drawn live in renderHeader; nothing cached.
    }

    private void bulkSet(boolean all, TargetMode mode) {
        int count = 0;
        if (all) {
            for (EntityDef def : allDefs) {
                ModConfig.INSTANCE.targeting.put(def.id, mode);
                count++;
            }
        } else {
            for (EntityEntry entry : visibleEntries()) {
                entry.setMode(mode);
                count++;
            }
        }
        ModConfig.save();
        rebuildTree();
        setStatus("Set " + count + " entities to " + mode.name());
    }

    private void bulkAllAlwaysClick() {
        long now = System.currentTimeMillis();
        if (confirmArmed && now < confirmUntilMs) {
            confirmArmed = false;
            bulkAllAlways.setMessage(Component.literal("ALL→ALWAYS"));
            bulkSet(true, TargetMode.ALWAYS);
            return;
        }
        confirmArmed = true;
        confirmUntilMs = now + 3000;
        bulkAllAlways.setMessage(Component.literal("Click again to confirm"));
    }

    private void setStatus(String text) {
        statusText = text;
        statusUntilMs = System.currentTimeMillis() + 2000;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (searchDirty && now - lastSearchEditMs >= 250) {
            searchDirty = false;
            appliedFilter = filterText;
            rebuildTree();
        }
        if (confirmArmed && now >= confirmUntilMs) {
            confirmArmed = false;
            bulkAllAlways.setMessage(Component.literal("ALL→ALWAYS"));
        }
        if (!statusText.isEmpty() && now >= statusUntilMs) {
            statusText = "";
        }
    }

    public void renderHeader(GuiGraphics graphics, net.minecraft.client.gui.Font font, int width) {
        graphics.drawString(font, "Targeting — Task: Kill Aura [ " + registeredCount + " entities registered ]",
                (width - font.width("Targeting — Task: Kill Aura [ 000 entities registered ]")) / 2, 24, 0xFFFFFF, false);
        if (ModConfig.INSTANCE.playerMode == PlayerMode.BY_NAME && ModConfig.INSTANCE.playerNames.isEmpty()) {
            graphics.drawString(font, "Empty names list behaves as OFF", 40, 52, 0xFFA0A0, false);
        }
        if (!statusText.isEmpty()) {
            graphics.drawString(font, statusText, 40, 404, 0xFFFFA0, false);
        }
    }

    public void setVisible(boolean visible) {
        playerModeButton.visible = visible;
        searchBox.visible = visible;
        tree.visible = visible;
        bulkVisibleAlways.visible = visible;
        bulkVisibleAngered.visible = visible;
        bulkVisibleOff.visible = visible;
        bulkAllAlways.visible = visible;
        checkLOSButton.visible = visible;
        healthOverlayButton.visible = visible;
        measuredTTKButton.visible = visible;
        nameField.visible = visible && namesVisible();
        if (addButtonRef != null) {
            addButtonRef.visible = visible && namesVisible();
        }
        names.visible = visible && namesVisible();
    }

    public void release() {
        allDefs.clear();
        tree = null;
        names = null;
    }

    /** Any click outside the armed confirm button reverts it (no modal). */
    public void onAnyClick(double mouseX, double mouseY) {
        if (!confirmArmed || bulkAllAlways == null) {
            return;
        }
        if (mouseX >= bulkAllAlways.getX() && mouseX < bulkAllAlways.getX() + bulkAllAlways.getWidth()
                && mouseY >= bulkAllAlways.getY() && mouseY < bulkAllAlways.getY() + bulkAllAlways.getHeight()) {
            return;
        }
        confirmArmed = false;
        bulkAllAlways.setMessage(Component.literal("ALL→ALWAYS"));
    }
}
