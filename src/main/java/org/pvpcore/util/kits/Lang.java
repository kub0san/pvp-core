/*
 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.util.kits;
import org.pvpcore.config.Messages;
import org.pvpcore.util.TextUtil;
import org.pvpcore.util.AudienceCompat;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads and serves the unified {@code messages.yml}.
 *
 * <h2>Resolution order</h2>
 * When looking up a key, the following sources are checked in this order
 * (highest priority first):
 * <ol>
 *   <li>Disk file, module section ({@code pvpcore.*} or {@code kits.*})</li>
 *   <li>Disk file, root-level leaf keys</li>
 *   <li>Jar resource, module section</li>
 *   <li>Jar resource, root-level leaf keys</li>
 * </ol>
 * Every value ends up in a single flattened {@link YamlConfiguration} so
 * callers can use short keys like {@code raw("gui.regear-shulker-name")}
 * regardless of where the value physically lives.
 *
 * <h2>Section names</h2>
 * Older builds of Kits used a {@code kits:} section for its messages; newer
 * builds use {@code pvpcore:} to match the plugin rename. Both are accepted,
 * and if both are present {@code pvpcore:} wins on key conflicts.
 *
 * <h2>Thread safety</h2>
 * The instance is created and populated on the main thread during plugin
 * enable, then only read afterwards. Reads from other threads are safe
 * because {@link YamlConfiguration} is never mutated after construction.
 * The static {@link #get()} uses a {@code volatile} field so publication
 * across threads is correct.
 *
 * <h2>Failure modes</h2>
 * <ul>
 *   <li>Disk file missing — the bundled copy is extracted from the jar.</li>
 *   <li>Disk file unparseable — the broken file is backed up and the bundled
 *       copy is restored, with the reason logged at {@code SEVERE}.</li>
 *   <li>Disk file present but predates the module section — the jar's module
 *       section is merged into the disk file (comments are stripped, which
 *       is the standard Bukkit trade-off). A backup of the pre-merge file
 *       is written first.</li>
 *   <li>Individual key missing from every source — the key itself is returned
 *       and a warning is logged exactly once per key.</li>
 * </ul>
 *
 * <p>why retained: kits-scoped message provider with file resolution/migration/section-merge and
 * key-reporting behavior not fully covered by the core {@code Messages} façade.</p>
 */
public class Lang {

    /** why: public messages resource name retained for external/legacy compatibility. */
    public static final String MESSAGES_RESOURCE = "messages.yml";

    /** why: shared module section names retained for message resolution/merge behavior. */
    private static final String[] MODULE_SECTIONS = {"pvpcore", "kits"};

    private static volatile Lang instance; // why: singleton instance pointer retained for legacy static accessor/lifecycle compatibility.

    private final Plugin plugin;
    private final BukkitAudiences audience;
    private final File messagesFile;

    /** Fully resolved flattened config. Never mutated after construction. */
    private final YamlConfiguration messages;

    /** One warning per key, so a missing key does not spam the console. */
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    /**
     * Production constructor. Ensures the disk file exists and is valid, runs
     * any needed migration, and builds the flattened message store.
     */
    public Lang(Plugin plugin) {
        this.plugin = plugin;
        this.messagesFile = new File(plugin.getDataFolder(), MESSAGES_RESOURCE);
        this.audience = BukkitAudiences.create(plugin);

        ensureMessagesFileExists();
        YamlConfiguration disk = readDiskFile(messagesFile, plugin);
        migrateIfNeeded(disk);
        YamlConfiguration jar = readJarFile(plugin);

        this.messages = buildResolved(disk, jar);
        instance = this;

        plugin.getLogger().info("Loaded messages from " + messagesFile
                + " (" + countLeaves(messages) + " keys resolved, "
                + "module sections present: " + describeSections(disk) + ")");

        // Off unless -Dperplayerkit.debug=true. Which path components take is
        // invisible from the outside and silent when it breaks, so the
        // integration test switches this on to assert it, and it stays out of
        // everyone else's logs.
        if (Boolean.getBoolean("perplayerkit.debug")) {
            plugin.getLogger().info("Chat delivery: " + (AudienceCompat.isAvailable()
                    ? "the server's own chat API" : "bundled adventure-platform"));
        }
    }

    /**
     * Test-only constructor: skips file extraction and Bukkit audience setup.
     * Sends fall back to {@link CommandSender#sendMessage(String)} with legacy
     * color codes so tests can verify with simple Mockito matchers.
     */
    Lang(YamlConfiguration langConfig) {
        this.plugin = null;
        this.audience = null;
        this.messagesFile = null;
        this.messages = buildResolved(langConfig, new YamlConfiguration());
    }

    // ================================================================
    // Singleton lifecycle
    // ================================================================

    /** why: legacy singleton shim retained for external/legacy compatibility; no internal call sites. */
    @Deprecated
    public static Lang get() {
        Lang local = instance;
        if (local == null) {
            throw new IllegalStateException("Lang has not been initialized");
        }
        return local;
    }



    /** why: static shutdown retained for module disable/reload lifecycle cleanup of singleton-style state. */
    public static void shutdown() {
        Lang current = instance;
        if (current != null && current.audience != null) {
            try {
                current.audience.close();
            } catch (RuntimeException ignored) {
                // The server may already be tearing the audience down.
            }
        }
        instance = null;
    }

    // ================================================================
    // File I/O
    // ================================================================

    /**
     * Ensures {@code messages.yml} exists and is parseable. On first run
     * the bundled copy is extracted. On an unparseable file, the broken
     * file is backed up and the bundled copy is restored so the plugin
     * starts with a working set of defaults.
     */
    private void ensureMessagesFileExists() {
        if (!messagesFile.exists()) {
            writeBundled();
            return;
        }

        YamlConfiguration probe = new YamlConfiguration();
        boolean broken = false;
        try (InputStream in = Files.newInputStream(messagesFile.toPath());
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            probe.load(reader);
        } catch (IOException | InvalidConfigurationException e) {
            broken = true;
            plugin.getLogger().log(Level.SEVERE,
                    "messages.yml could not be parsed: " + e.getMessage(), e);
        }

        if (broken || probe.getKeys(false).isEmpty()) {
            backupBroken();
            writeBundled();
        }
    }

    private void backupBroken() {
        try {
            File backup = new File(messagesFile.getParentFile(),
                    messagesFile.getName() + ".broken-" + System.currentTimeMillis());
            Files.copy(messagesFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("messages.yml was unusable; the previous file "
                    + "was saved as " + backup.getName() + " and the bundled copy restored.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not back up broken messages.yml before restore", e);
        }
    }

    private void writeBundled() {
        try {
            Files.createDirectories(messagesFile.toPath().getParent());
            try (InputStream in = plugin.getResource(MESSAGES_RESOURCE)) {
                if (in == null) {
                    plugin.getLogger().severe("Bundled " + MESSAGES_RESOURCE
                            + " is missing from the jar; no messages will load");
                    return;
                }
                Files.copy(in, messagesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to extract " + MESSAGES_RESOURCE + " from the jar", e);
        }
    }

    private static YamlConfiguration readDiskFile(File file, Plugin plugin) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (!file.exists()) {
            return cfg;
        }
        try (InputStream in = Files.newInputStream(file.toPath());
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            cfg.load(reader);
        } catch (IOException | InvalidConfigurationException e) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to read " + file.getName() + ": " + e.getMessage(), e);
            }
            // Caller treats empty config as "no values on disk"
        }
        return cfg;
    }

    private static YamlConfiguration readJarFile(Plugin plugin) {
        YamlConfiguration cfg = new YamlConfiguration();
        try (InputStream in = plugin.getResource(MESSAGES_RESOURCE)) {
            if (in == null) {
                plugin.getLogger().severe("Bundled " + MESSAGES_RESOURCE
                        + " is missing from the jar");
                return cfg;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                cfg.load(reader);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Bundled " + MESSAGES_RESOURCE
                    + " in the jar is invalid: " + e.getMessage());
        }
        return cfg;
    }

    // ================================================================
    // Migration
    // ================================================================

    /**
     * One-time migration for files created before the kits module was merged
     * into the unified messages file. Such files have {@code teleportation:}
     * but no module section for kits messages. The jar's module section is
     * merged into the disk file so admins can see and edit the new keys.
     *
     * <p>No-op if the file already has a module section ({@code pvpcore:} or
     * {@code kits:}) — those are served from jar defaults at read time and
     * don't need to be written back.</p>
     */
    private void migrateIfNeeded(YamlConfiguration disk) {
        if (disk.getKeys(false).isEmpty()) {
            return;
        }
        if (findModuleSectionName(disk) != null) {
            return; // Already has a module section; nothing to migrate.
        }
        if (!disk.contains("teleportation")) {
            return; // Not a PvPCore-core file; don't touch it.
        }

        YamlConfiguration jar = readJarFile(plugin);
        String jarSection = findModuleSectionName(jar);
        if (jarSection == null) {
            return;
        }
        ConfigurationSection source = jar.getConfigurationSection(jarSection);
        if (source == null) {
            return;
        }

        // Back up before mutating, since saving strips comments.
        try {
            File backup = new File(messagesFile.getParentFile(),
                    messagesFile.getName() + ".pre-migration-" + System.currentTimeMillis());
            Files.copy(messagesFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not back up messages.yml before migration; continuing anyway", e);
        }

        String targetSection = MODULE_SECTIONS[0]; // "pvpcore"
        int added = 0;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            String targetKey = targetSection + "." + key;
            if (!disk.contains(targetKey)) {
                disk.set(targetKey, source.get(key));
                added++;
            }
        }

        if (added == 0) {
            return;
        }

        try {
            disk.save(messagesFile);
            plugin.getLogger().info("Merged " + added + " key(s) from bundled messages "
                    + "into the '" + targetSection + "' section of " + messagesFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save merged " + messagesFile.getName(), e);
        }
    }

    // ================================================================
    // Resolution
    // ================================================================

    /**
     * Builds the flattened message store by layering sources from lowest to
     * highest priority. Higher priority sources overwrite lower ones, so the
     * last copy of any given key wins.
     */
    private static YamlConfiguration buildResolved(YamlConfiguration disk, YamlConfiguration jar) {
        YamlConfiguration resolved = new YamlConfiguration();
        copyRootLeaves(jar, resolved);       // 4: lowest
        copyModuleSection(jar, resolved);    // 3
        copyRootLeaves(disk, resolved);      // 2
        copyModuleSection(disk, resolved);   // 1: highest
        return resolved;
    }

    /** Copies only root-level leaf values (not sections). */
    private static void copyRootLeaves(YamlConfiguration source, YamlConfiguration target) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(false)) {
            Object value = source.get(key);
            if (value != null && !(value instanceof ConfigurationSection)) {
                target.set(key, value);
            }
        }
    }

    /**
     * Copies all leaf values from the module section(s), applying newer
     * section names last so they win on conflicts. Falls back to a legacy
     * flat-file heuristic when no module section is present.
     */
    private static void copyModuleSection(YamlConfiguration source, YamlConfiguration target) {
        if (source == null) {
            return;
        }

        // Iterate oldest-to-newest so the newest section wins on conflicts.
        for (int i = MODULE_SECTIONS.length - 1; i >= 0; i--) {
            ConfigurationSection section = source.getConfigurationSection(MODULE_SECTIONS[i]);
            if (section == null) {
                continue;
            }
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    target.set(key, section.get(key));
                }
            }
        }

        // Legacy flat format: keys at the root with no module wrapper. The
        // heuristic is the presence of a root-level "error" key, which only
        // makes sense in the pre-section format.
        if (findModuleSectionName(source) == null && source.contains("error")) {
            for (String key : source.getKeys(true)) {
                if (!source.isConfigurationSection(key)) {
                    target.set(key, source.get(key));
                }
            }
        }
    }

    private static String findModuleSectionName(YamlConfiguration cfg) {
        for (String name : MODULE_SECTIONS) {
            if (cfg.isConfigurationSection(name)) {
                return name;
            }
        }
        return null;
    }

    private static int countLeaves(YamlConfiguration cfg) {
        int count = 0;
        for (String key : cfg.getKeys(true)) {
            if (!cfg.isConfigurationSection(key)) {
                count++;
            }
        }
        return count;
    }

    private static String describeSections(YamlConfiguration cfg) {
        StringBuilder sb = new StringBuilder();
        for (String name : MODULE_SECTIONS) {
            if (cfg.isConfigurationSection(name)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(name);
            }
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    // ================================================================
    // Public API
    // ================================================================

    public File getMessagesFile() {
        return messagesFile;
    }

    /** Returns true if the given key resolves to a value. */
    public boolean hasKey(String key) {
        return messages.contains(key);
    }

    public String raw(String key) {
        Object value = messages.get(key);
        if (value == null) {
            warnMissing(key);
            return key;
        }
        return String.valueOf(value);
    }

    public String raw(String key, Map<String, String> placeholders) {
        return applyPlaceholders(raw(key), placeholders);
    }

    public String raw(String key, String... pairs) {
        return applyPlaceholders(raw(key), pairsToMap(pairs));
    }

    public List<String> rawList(String key) {
        if (!messages.contains(key)) {
            warnMissing(key);
            return List.of();
        }
        return messages.getStringList(key);
    }

    private void warnMissing(String key) {
        if (plugin == null) {
            return;
        }
        if (reportedMissing.add(key)) {
            plugin.getLogger().warning("Missing language key: " + key);
        }
    }

    // ----------------------------------------------------------------
    // Rendering
    // ----------------------------------------------------------------

    public Component component(String key) {
        return Messages.parse(raw(key));
    }

    public Component component(String key, Map<String, String> placeholders) {
        return Messages.parse(raw(key, placeholders));
    }

    public Component component(String key, String... pairs) {
        return Messages.parse(raw(key, pairs));
    }

    public Component prefix() {
        return Messages.parse(raw("prefix"));
    }

    // ----------------------------------------------------------------
    // Sending
    // ----------------------------------------------------------------

    public void send(CommandSender sender, String key) {
        send(sender, key, (Map<String, String>) null);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Component msg = prefix().append(component(key, placeholders == null ? Map.of() : placeholders));
        deliver(sender, msg);
    }

    public void send(CommandSender sender, String key, String... pairs) {
        send(sender, key, pairsToMap(pairs));
    }

    public void sendNoPrefix(CommandSender sender, String key) {
        sendNoPrefix(sender, key, (Map<String, String>) null);
    }

    public void sendNoPrefix(CommandSender sender, String key, Map<String, String> placeholders) {
        Component msg = component(key, placeholders == null ? Map.of() : placeholders);
        deliver(sender, msg);
    }

    public void sendNoPrefix(CommandSender sender, String key, String... pairs) {
        sendNoPrefix(sender, key, pairsToMap(pairs));
    }

    private void deliver(CommandSender sender, Component msg) {
        // Paper's own chat API first: adventure-platform silently delivers
        // nothing on servers newer than it knows about. See AudienceCompat.
        if (sender instanceof Player && AudienceCompat.send(sender, msg)) {
            return;
        }
        if (audience != null && sender instanceof Player p) {
            audience.player(p).sendMessage(msg);
        } else {
            // Console/RCON/command blocks: adventure-platform picks its console
            // facet by sniffing the server version and silently drops messages
            // on versions it doesn't know yet, so send a legacy-serialized
            // string instead — colors still render and it can't break on
            // future server versions.
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(msg));
        }
    }

    // ----------------------------------------------------------------
    // Convenience
    // ----------------------------------------------------------------

    public String legacy(String key) {
        return TextUtil.LEGACY.serialize(component(key));
    }

    public String legacy(String key, String... pairs) {
        return TextUtil.LEGACY.serialize(component(key, pairs));
    }

    /**
     * Splits a template like {@code "Kit: {slot}"} around the named
     * placeholder. Returns {@code [prefix, suffix]} — useful for both
     * rendering the template and parsing back the placeholder value from a
     * rendered title.
     */
    public String[] splitTemplate(String key, String placeholder) {
        String tmpl = raw(key);
        String token = "{" + placeholder + "}";
        int idx = tmpl.indexOf(token);
        if (idx < 0) {
            return new String[]{tmpl, ""};
        }
        return new String[]{tmpl.substring(0, idx), tmpl.substring(idx + token.length())};
    }

    // ----------------------------------------------------------------
    // Placeholder helpers
    // ----------------------------------------------------------------

    /**
     * Substitutes placeholder tokens in a message template.
     *
     * <p>Both brace style ({@code {x}}) and angle-bracket style ({@code <x>})
     * are supported. Angle brackets are only replaced when the placeholder
     * name matches a key in the map, so MiniMessage formatting tags like
     * {@code <red>}, {@code <bold>}, {@code <white>} are left untouched —
     * those are never passed as placeholders.</p>
     */
    private static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + e.getKey() + "}", value);
            out = out.replace("<" + e.getKey() + ">", value);
        }
        return out;
    }

    private static Map<String, String> pairsToMap(String... pairs) {
        if (pairs == null || pairs.length == 0) {
            return Map.of();
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("placeholder pairs must be key,value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** why: stateless placeholder helper retained for external/legacy compatibility. */
    public static Map<String, String> placeholders(String... pairs) {
        return pairsToMap(pairs);
    }

    public Map<String, String> ph(String... pairs) {
        return pairsToMap(pairs);
    }

    static Map<String, String> applyPlaceholdersForTest(String input, String... pairs) {
        Map<String, String> map = pairsToMap(pairs);
        Map<String, String> out = new HashMap<>(map);
        out.put("__result__", applyPlaceholders(input, map));
        return out;
    }
}