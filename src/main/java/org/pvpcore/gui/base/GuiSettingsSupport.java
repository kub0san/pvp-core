package org.pvpcore.gui.base;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.gui.kits.page.SettingsGroupPage;
import org.pvpcore.gui.kits.page.SettingsHubPage;
import org.pvpcore.gui.kits.page.SettingsListPage;
import org.pvpcore.util.kits.LocationRules;
import org.pvpcore.util.kits.RekitKitResolver;
import org.pvpcore.util.kits.SoundManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shared logic for the framed settings pages. The three page classes
 * ({@link SettingsHubPage}, {@link SettingsGroupPage}, {@link SettingsListPage})
 * delegate here for staged values, save/discard, and click handling, so the
 * behaviour matches the old {@code SettingsGui} exactly. Schema/definitions live
 * in {@link GuiSettingsSchema}.
 */
public final class GuiSettingsSupport {
    public static final int GROUPS_PER_SCREEN = GuiSettingsSchema.GROUPS_PER_SCREEN;
    public static final int SETTINGS_PER_SCREEN = GuiSettingsSchema.SETTINGS_PER_SCREEN;
    public static final int ENTRIES_PER_SCREEN = GuiSettingsSchema.ENTRIES_PER_SCREEN;

    public record Staged(GuiSettingsSchema.Def def, Object value) {
    }

    public record Return(GUI gui, String groupKey, int page) {
    }

    private GuiSettingsSupport() {
    }

    public static List<GuiSettingsSchema.Group> groups() {
        return GuiSettingsSchema.groups();
    }

    private static Map<UUID, Map<String, Staged>> store() {
        try {
            return GuiSessions.current().stagedSettingsStore();
        } catch (IllegalStateException e) {
            // No session registered (should not happen for settings flow); fall back to a no-op store.
            return GuiSession.NoOpStagedStore.INSTANCE;
        }
    }

    public static Map<String, Staged> stagedOf(Player player) {
        return store().computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>());
    }

    public static Object displayed(KitsContext kits, Player player, GuiSettingsSchema.Def def) {
        Staged staged = stagedOf(player).get(def.path());
        if (staged != null) return staged.value();
        return switch (def.kind()) {
            case TOGGLE -> kits.config().getBoolean(def.path());
            case NUMBER, CHOICE, TEXT, LIST, MATERIAL, LOCKED -> kits.config().get(def.path());
        };
    }

    public static void stage(Player player, GuiSettingsSchema.Def def, Object value) {
        stagedOf(player).put(def.path(), new Staged(def, value));
    }

    public static void save(GuiScreen screen, Player player) {
        Map<String, Staged> staged = store().get(player.getUniqueId());
        if (staged == null || staged.isEmpty()) return;

        KitsContext kits = screen.kits();
        Path file = kits == null ? null : kits.configFile();
        if (file == null) {
            GuiChatPrompt.say(player, "<red>Settings file is unknown, nothing was saved.");
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration candidate = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            candidate.loadFromString(kits.config().saveToString());
            for (Map.Entry<String, Staged> entry : staged.entrySet()) {
                candidate.set(entry.getKey(), entry.getValue().value());
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            GuiChatPrompt.say(player, "<red>Could not build a valid config: " + e.getMessage() + ". Nothing was saved.");
            return;
        }

        try {
            KitRoomDataManager.configuredPages(candidate);
            LocationRules.parse(candidate);
            RekitKitResolver.validate(candidate);
        } catch (Exception rejected) {
            GuiChatPrompt.say(player, "<red>Rejected: " + rejected.getMessage() + ". Nothing was written.");
            return;
        }

        // A missing config (first run) is not a reason to refuse the save -- there is simply nothing to back up.
        if (Files.exists(file)) {
            try {
                Files.copy(file, file.resolveSibling("config.yml.backup"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                GuiChatPrompt.say(player, "<red>Could not create a backup of the config, nothing was saved.");
                return;
            }
        }

        List<String> reloadLabels = new ArrayList<>();
        List<String> restartLabels = new ArrayList<>();
        for (Staged entry : staged.values()) {
            GuiSettingsSchema.Def def = entry.def();
            kits.config().set(def.path(), entry.value());
            switch (def.impact()) {
                case RELOAD -> reloadLabels.add(def.label());
                case RESTART -> restartLabels.add(def.label());
                case LIVE -> { }
            }
        }
        kits.saveConfig();
        store().remove(player.getUniqueId());
        SoundManager.playSuccess(player);

        if (restartLabels.isEmpty() && reloadLabels.isEmpty()) {
            GuiChatPrompt.say(player, "<green>Saved. All changes are live.");
        } else if (restartLabels.isEmpty()) {
            GuiChatPrompt.say(player, "<green>Saved. <yellow>Needs /pvp-core reload: <white>" + String.join(", ", reloadLabels));
        } else {
            GuiChatPrompt.say(player, "<green>Saved. <yellow>These need a server restart: <white>" + String.join(", ", restartLabels));
        }
        screen.refresh();
    }

    public static void discard(GuiScreen screen, Player player) {
        SoundManager.playClick(player);
        GuiConfirm.open(player, "Discard changes?",
                new String[]{"Your unsaved changes are", "simply thrown away.", "The config is unchanged."}, false, null,
                () -> {
                    store().remove(player.getUniqueId());
                    reopenScreen(screen, player);
                },
                () -> reopenScreen(screen, player));
    }

    /**
     * The kits GUI facade behind a settings screen. Every settings screen is
     * constructed with the shared GUI instance as its session, so the cast is
     * safe for everything this class builds or reopens. The foundation itself
     * never depends on the facade; this file is the one documented exception.
     */
    private static GUI guiOf(GuiScreen screen) {
        return (GUI) screen.session();
    }

    public static void reopenScreen(GuiScreen screen, Player player) {
        if (screen instanceof SettingsGroupPage group) {
            new SettingsGroupPage(guiOf(group), player, group.groupName(), group.page()).show();
        } else if (screen instanceof SettingsHubPage hub) {
            new SettingsHubPage(guiOf(hub), player, hub.page()).show();
        } else if (screen instanceof SettingsListPage list) {
            reopen(list.back(), player);
        }
    }

    public static Return returnOf(GuiScreen screen) {
        if (screen instanceof SettingsGroupPage group) {
            return new Return(guiOf(group), group.groupName(), group.page());
        }
        if (screen instanceof SettingsHubPage hub) {
            return new Return(guiOf(hub), GuiSettingsSchema.groups().get(0).name(), 0);
        }
        return new Return(guiOf(screen), GuiSettingsSchema.groups().get(0).name(), 0);
    }

    public static void reopen(Return back, Player player) {
        new SettingsGroupPage(back.gui(), player, back.groupKey(), back.page()).show();
    }

    public static ItemStack settingItem(KitsContext kits, Player player, GuiSettingsSchema.Def def) {
        Object value = displayed(kits, player, def);
        boolean unsaved = stagedOf(player).containsKey(def.path());
        List<String> lore = new ArrayList<>();
        String color = GuiTheme.TEXT;

        switch (def.kind()) {
            case TOGGLE -> {
                boolean on = Boolean.TRUE.equals(value) || (value instanceof String s && s.equalsIgnoreCase("true"));
                color = on ? GuiTheme.GREEN : GuiTheme.MUTED;
                lore.add(GuiTheme.status(color, on, on ? "Enabled" : "Disabled"));
            }
            case NUMBER -> lore.add(GuiTheme.value("Value", value == null ? "-" : String.valueOf(value)));
            case CHOICE, TEXT, MATERIAL -> lore.add(GuiTheme.value("Value",
                    value == null || String.valueOf(value).isEmpty() ? "-" : String.valueOf(value)));
            case LIST -> {
                int size = value instanceof List<?> list ? list.size() : 0;
                lore.add(GuiTheme.value("Entries", size == 0 ? "none" : String.valueOf(size)));
            }
            case LOCKED -> {
                color = GuiTheme.RED;
                lore.add(GuiTheme.value("Value", String.valueOf(value)));
            }
        }
        if (unsaved) lore.add(GuiTheme.GOLD + "Unsaved");
        if (def.desc() != null) lore.add(GuiTheme.dim(def.desc()));
        switch (def.impact()) {
            case RELOAD -> lore.add(GuiTheme.dim("Applies after /pvpcore reload"));
            case RESTART -> lore.add(GuiTheme.dim("Applies after a restart"));
            case LIVE -> { }
        }

        switch (def.kind()) {
            case TOGGLE -> lore.add(GuiTheme.hint("Click", "toggle"));
            case NUMBER -> lore.add(GuiTheme.hint("Click / Right / Middle", "+1 · -1 · type"));
            case CHOICE -> lore.add(GuiTheme.hint("Click / Right", "next · previous"));
            case TEXT -> lore.add(GuiTheme.hint("Click", "type a value"));
            case MATERIAL -> lore.add(GuiTheme.hint("Click with item", "set"));
            case LIST -> lore.add(GuiTheme.hint("Click", "edit"));
            case LOCKED -> lore.add(GuiTheme.dim(def.lockReason() == null ? "Locked" : def.lockReason()));
        }
        return GuiTheme.named(iconFor(def, value), color + "<bold>", def.label(), lore.toArray(new String[0]));
    }

    private static Material iconFor(GuiSettingsSchema.Def def, Object value) {
        return switch (def.kind()) {
            case TOGGLE -> Material.LEVER;
            case NUMBER -> Material.SUNFLOWER;
            case CHOICE -> Material.OAK_SIGN;
            case TEXT -> Material.NAME_TAG;
            case LIST -> Material.PAPER;
            case MATERIAL -> {
                Material current = value == null ? null : Material.matchMaterial(String.valueOf(value));
                yield current == null ? Material.ITEM_FRAME : current;
            }
            case LOCKED -> Material.BARRIER;
        };
    }

    public static void settingClicked(GuiScreen screen, Player player, GuiSettingsSchema.Def def, InventoryClickEvent event) {
        SoundManager.playClick(player);
        Object current = displayed(screen.kits(), player, def);

        switch (def.kind()) {
            case TOGGLE -> {
                Boolean on = Boolean.TRUE.equals(current) || (current instanceof String s && s.equalsIgnoreCase("true"));
                stage(player, def, !on);
                screen.refresh();
            }
            case NUMBER -> {
                org.bukkit.event.inventory.ClickType click = event.getClick();
                if (click == org.bukkit.event.inventory.ClickType.MIDDLE || click == org.bukkit.event.inventory.ClickType.DROP) {
                    askExactNumber(screen, player, def);
                    return;
                }
                int step = click.isShiftClick() ? 10 : 1;
                adjustNumber(screen, player, def, click.isRightClick() ? -step : step);
            }
            case CHOICE -> {
                int index = def.options().indexOf(String.valueOf(current));
                int step = event.getClick().isRightClick() ? -1 : 1;
                int next = (Math.max(index, 0) + step + def.options().size()) % def.options().size();
                stage(player, def, def.options().get(next));
                screen.refresh();
            }
            case TEXT -> askText(screen, player, def);
            case MATERIAL -> {
                Material icon = GuiTheme.cursorMaterial(event);
                if (icon == null) {
                    GuiChatPrompt.say(player, "<yellow>Pick up an item first, then click " + def.label() + ".");
                    return;
                }
                stage(player, def, icon);
                SoundManager.playSuccess(player);
                screen.refresh();
            }
            case LIST -> new SettingsListPage(guiOf(screen), player, def, returnOf(screen)).show();
            case LOCKED -> { }
        }
    }

    private static void adjustNumber(GuiScreen screen, Player player, GuiSettingsSchema.Def def, int delta) {
        int target = currentNumber(screen.kits(), player, def) + delta;
        if (target < def.min() || target > def.max()) {
            GuiChatPrompt.say(player, "<red>" + def.label() + " must be between " + def.min() + " and " + def.max() + ".");
            return;
        }
        stage(player, def, target);
        screen.refresh();
    }

    private static int currentNumber(KitsContext kits, Player player, GuiSettingsSchema.Def def) {
        Object value = displayed(kits, player, def);
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? def.min() : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def.min();
        }
    }

    private static void askExactNumber(GuiScreen screen, Player player, GuiSettingsSchema.Def def) {
        Return back = returnOf(screen);
        GuiChatPrompt.ask(guiOf(screen).plugin(), player, "Exact value for " + def.label() + ":", text -> {
            String value = text.trim();
            try {
                int number = Integer.parseInt(value);
                if (number < def.min() || number > def.max()) {
                    GuiChatPrompt.say(player, "<red>" + def.label() + " must be between " + def.min() + " and " + def.max()
                            + ". Nothing was changed.");
                } else {
                    stage(player, def, number);
                }
            } catch (NumberFormatException e) {
                GuiChatPrompt.say(player, "<red>" + def.label() + " needs a whole number, not \"" + value + "\". Nothing was changed.");
            }
            reopen(back, player);
        }, () -> reopen(back, player));
    }

    private static void askText(GuiScreen screen, Player player, GuiSettingsSchema.Def def) {
        Return back = returnOf(screen);
        GuiChatPrompt.ask(guiOf(screen).plugin(), player, "New value for " + def.label() + ":", text -> {
            String value = text.trim();
            String problem = value.isEmpty() ? def.label() + " cannot be empty. Nothing was changed."
                    : textProblem(def, value);
            if (problem == null) stage(player, def, value);
            else GuiChatPrompt.say(player, "<red>" + problem);
            reopen(back, player);
        }, () -> reopen(back, player));
    }

    private static String textProblem(GuiSettingsSchema.Def def, String value) {
        if (def.path().startsWith("sounds.")) {
            return validSound(value) ? null : def.label() + " is not a known sound (e.g. UI_BUTTON_CLICK). Nothing was changed.";
        }
        return null;
    }

    private static boolean validSound(String name) {
        try {
            String clean = name.toLowerCase(Locale.ROOT).replace('_', '.');
            return org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.fromString(clean)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
