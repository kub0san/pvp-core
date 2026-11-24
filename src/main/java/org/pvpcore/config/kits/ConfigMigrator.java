/*
 * Copyright 2022-2026 Noah Ross
 * ...
 */

package org.pvpcore.config.kits;

import org.pvpcore.config.ConfigFiles;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One-time v1/v2-to-v3 migration. Config is committed last; failed writes are rolled back. */

public class ConfigMigrator {
    public static final int CURRENT_VERSION = 3;
    private static final List<String> ACTIONS = List.of("player-repaired", "player-healed", "player-opened-kit-room",
            "player-loaded-private-kit", "player-loaded-public-kit", "player-loaded-enderchest",
            "player-copied-kit", "player-copied-ec", "player-regeared");
    private final Plugin plugin;

    public ConfigMigrator(Plugin plugin) { this.plugin = plugin; }

    public boolean migrate() {
        Path kitsDir = resolveKitsDir();
        Path configFile = kitsDir.resolve("config.yml");
        if (!Files.exists(configFile)) return true;
        Map<Path, Path> backups = new LinkedHashMap<>();
        List<Path> written = new ArrayList<>();
        try {
            YamlConfiguration original = ConfigFiles.read(configFile);
            if (original.contains("config-version") && !original.isInt("config-version"))
                throw new IOException("config-version must be an integer; retain the version supplied by the old plugin");
            int version = original.getInt("config-version", 1);
            if (version < 1 || version > CURRENT_VERSION) throw new IOException("Unsupported config-version " + version);
            if (version == CURRENT_VERSION && !ConfigSchema.hasLegacyLocations(original) && !ConfigSchema.hasLegacyItemFilter(original)) return true;

            YamlConfiguration old = new YamlConfiguration();
            old.loadFromString(original.saveToString());
            Map<Path, YamlConfiguration> changes = new LinkedHashMap<>();
            int messageCount = 0;
            if (version == 1) {
                Map<String, Object> messages = legacyMessages(old);
                messageCount = messages.size();
                if (!messages.isEmpty()) {
                    Path file = kitsDir.resolve("messages.yml");
                    YamlConfiguration language = Files.exists(file) ? ConfigFiles.read(file) : bundledMessages();
                    messages.forEach(language::set);
                    changes.put(file, language);
                }
                old.set("prefix", null);
                old.set("disabled-command-message", null);
                old.set("motd.message", null);
                old.set("scheduled-broadcast.messages", null);
                for (String action : ACTIONS) old.set("messages." + action + ".message", null);
            }
            YamlConfiguration updated;
            if (version == CURRENT_VERSION) {
                ConfigSchema.upgradeLocations(old);
                ConfigSchema.upgradeItemFilter(old);
                updated = old;
            } else updated = ConfigSchema.upgrade(old, bundled("kits/config.yml", "Kits/config.yml"));
            ItemFilterRules.parse(updated);
            org.pvpcore.util.kits.LocationRules.parse(updated);
            org.pvpcore.util.kits.RekitKitResolver.validate(updated);
            changes.put(configFile, updated);
            for (Path file : changes.keySet()) {
                if (Files.exists(file)) backups.put(file, ConfigFiles.backup(file));
            }
            for (Map.Entry<Path, YamlConfiguration> change : changes.entrySet()) {
                written.add(change.getKey());
                ConfigFiles.write(change.getValue(), change.getKey());
            }
            logSummary(version, original, updated, messageCount, backups);
            return true;
        } catch (Exception error) {
            Collections.reverse(written);
            boolean restored = true;
            for (Path file : written) {
                try {
                    Path backup = backups.get(file);
                    if (backup == null) { if (Files.exists(file)) Files.delete(file); }
                    else ConfigFiles.restore(backup, file);
                } catch (IOException rollbackError) {
                    restored = false;
                    plugin.getLogger().severe("Restore " + file + " from " + backups.get(file) + ": " + rollbackError.getMessage());
                }
            }
            plugin.getLogger().severe("Config migration failed. " + (restored ? "Original files retained. " : "Restore the backups listed above. ") + error.getMessage());
            return false;
        }
    }

    private Map<String, Object> legacyMessages(YamlConfiguration old) throws IOException {
        Map<String, Object> messages = new LinkedHashMap<>();
        if (old.contains("prefix")) messages.put("prefix", string(old, "prefix"));
        for (String path : List.of("motd.message", "scheduled-broadcast.messages")) {
            if (!old.contains(path)) continue;
            Object value = old.get(path);
            if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String || item instanceof Number || item instanceof Boolean)))
                throw new IOException(path + " must be a list of message strings (use [] for none)");
            messages.put(path, new ArrayList<>(old.getStringList(path)));
        }
        if (old.contains("disabled-command-message")) {
            String legacy = string(old, "disabled-command-message");
            net.kyori.adventure.text.Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
            messages.put("error.disabled-in-world", MiniMessage.miniMessage().serialize(component));
        }
        for (String action : ACTIONS) {
            String key = "messages." + action + ".message";
            if (old.contains(key)) messages.put("broadcast-messages." + action,
                    string(old, key).replace("%player%", "{player}").replace("%kitname%", "{kitname}"));
        }
        return messages;
    }

    private String string(YamlConfiguration config, String key) throws IOException {
        Object value = config.get(key);
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean))
            throw new IOException(key + " must be text (use \"\" for empty text)");
        return config.getString(key);
    }

    private YamlConfiguration bundledMessages() throws Exception {
        try (var in = plugin.getResource("messages.yml")) {
            if (in == null) throw new IOException("Bundled messages.yml is missing");
            YamlConfiguration full = new YamlConfiguration();
            full.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration messages = new YamlConfiguration();
            if (full.contains("kits")) {
                Object sec = full.get("kits");
                if (sec instanceof org.bukkit.configuration.ConfigurationSection cs) {
                    for (java.util.Map.Entry<String, Object> e : cs.getValues(true).entrySet()) {
                        messages.set(e.getKey(), e.getValue());
                    }
                }
            } else {
                messages.loadFromString(full.saveToString());
            }
            return messages;
        }
    }

    private YamlConfiguration bundled(String resource, String... fallbacks) throws Exception {
        String[] candidates = new String[fallbacks.length + 1];
        candidates[0] = resource;
        System.arraycopy(fallbacks, 0, candidates, 1, fallbacks.length);
        for (String candidate : candidates) {
            try (var in = plugin.getResource(candidate)) {
                if (in != null) {
                    YamlConfiguration config = new YamlConfiguration();
                    config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    return config;
                }
            }
        }
        throw new IOException("Bundled " + resource + " is missing");
    }

    private Path resolveKitsDir() {
        Path current = plugin.getDataFolder().toPath().resolve("kits");
        Path legacy = plugin.getDataFolder().toPath().resolve("Kits");
        if (Files.exists(current)) return current;
        if (Files.exists(legacy)) return legacy;
        return current;
    }

    private void logSummary(int version, YamlConfiguration old, YamlConfiguration updated,
                            int messages, Map<Path, Path> backups) {
        plugin.getLogger().info(version == CURRENT_VERSION
                ? "Development config v3 settings updated. Saved kit data is unchanged."
                : "Config upgraded from v" + version + " to v3. Saved kit data is unchanged.");
        backups.forEach((file, backup) -> plugin.getLogger().info("Backup for " + file.getFileName() + ": " + backup));
        long moved = ConfigSchema.RENAMED.keySet().stream().filter(old::contains).count();
        plugin.getLogger().info("Moved " + moved + " settings into storage, kits, rekit and broadcasts; preserved " + messages + " legacy messages.");
        String previousStorage = old.getString("storage.type");
        String storage = updated.getString("storage.type", "sqlite");
        if (previousStorage != null && storage.equals("sqlite") && !"sqlite".equals(previousStorage))
            plugin.getLogger().warning("The old storage.type did not select a supported backend. Retaining the SQLite database previously used. Use the storage migration command before selecting another backend.");
        else plugin.getLogger().info("Storage remains " + storage + ".");
        plugin.getLogger().info("Action broadcasts " + (updated.getBoolean("broadcasts.enabled", true) ? "remain enabled" : "remain disabled")
                + "; notification permissions and custom public kits are preserved.");
        if (ConfigSchema.hasLegacyItemFilter(old)) plugin.getLogger().info("Item filtering moved to item-filter with existing rules preserved. Enable filter-saves to also filter new saves.");
        plugin.getLogger().info("World lists now use locations.<feature>.mode and entries. A legacy nonempty kill whitelist still takes precedence.");
        plugin.getLogger().info("Review: world names now match without case sensitivity. Region-dependent actions stop when WorldGuard is unavailable.");
        plugin.getLogger().info("Review: menus enforce action permissions; location rules also cover sharing, regear items, healing and automatic rekit.");
        plugin.getLogger().info("Review: public kits now work with rekit/regear; Clear Kit deletes an empty saved slot. Upgrade notes: https://perplayerkit.com/upgrading");
        plugin.getLogger().info("Rollback: stop the server, restore these config/language backups and the previous jar. Keep the database backup for recovery; restore it only to undo item changes, since doing so discards later saves.");
    }
}
