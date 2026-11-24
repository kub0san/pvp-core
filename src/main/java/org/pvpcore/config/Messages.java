package org.pvpcore.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.util.AudienceCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A YAML file of MiniMessage strings (https://docs.papermc.io/adventure/minimessage/format/).
 * The copy bundled in the jar fills in any key missing from the server's file.
 *
 * <p>Placeholders are written {@code <name>}. {@code <prefix>} is available in every message.
 * Placeholder values are inserted as plain text, so a player name can never inject tags.</p>
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final YamlConfiguration yaml;
    private final String prefix;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
        this.prefix = yaml.getString("prefix", "");
    }

    /** Loads {@code file}, falling back to {@code resourcePath} inside the jar for missing keys. */
    public static Messages load(JavaPlugin plugin, File file, String resourcePath) {
        return new Messages(readYaml(plugin, file, resourcePath));
    }

    /** Loads a nested {@code section} from the unified {@code messages.yml}. */
    public static Messages load(JavaPlugin plugin, File file, String resourcePath, String section) {
        return new Messages(readYaml(plugin, file, resourcePath, section));
    }

    /** Reads a YAML file with the bundled copy as defaults. Also used for plain config files. */
    public static YamlConfiguration readYaml(JavaPlugin plugin, File file, String resourcePath) {
        return readYaml(plugin, file, resourcePath, null);
    }

    /** Reads a nested section from a unified YAML file. */
    public static YamlConfiguration readYaml(JavaPlugin plugin, File file, String resourcePath, String section) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration bundled = null;
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + resourcePath + ": " + e);
        }
        if (bundled != null) {
            yaml.setDefaults(bundled);
        }
        if (section == null) {
            return yaml;
        }
        YamlConfiguration extracted = extractSection(yaml, section);
        YamlConfiguration bundledExtracted = bundled == null ? null : extractSection(bundled, section);
        if (bundledExtracted != null) {
            extracted.setDefaults(bundledExtracted);
        }
        return extracted;
    }

    private static YamlConfiguration extractSection(YamlConfiguration source, String section) {
        YamlConfiguration out = new YamlConfiguration();
        if (source == null || section == null || !source.contains(section)) {
            return out;
        }
        Object sec = source.get(section);
        if (sec instanceof org.bukkit.configuration.ConfigurationSection cs) {
            for (java.util.Map.Entry<String, Object> e : cs.getValues(true).entrySet()) {
                out.set(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /**
     * Returns the message for {@code key}. Arguments are name/value pairs, for example
     * {@code get("request-sent", "player", "Steve", "seconds", "120")} fills {@code <player>}
     * and {@code <seconds>}.
     */
    public Component get(String key, String... pairs) {
        return component(key, pairs);
    }

    /** Façade: resolves a message key to a component (MiniMessage, with <prefix> and placeholders). */
    public Component component(String key, String... pairs) {
        String raw = rawTemplate(key);
        if (raw == null) {
            return Component.text("Missing message: " + key, NamedTextColor.RED);
        }
        List<TagResolver> resolvers = new ArrayList<>();
        resolvers.add(Placeholder.parsed("prefix", prefix));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolvers.add(Placeholder.unparsed(pairs[i], pairs[i + 1]));
        }
        return MINI.deserialize(raw, TagResolver.resolver(resolvers));
    }

    /** Façade: returns the raw configured template for {@code key}, or {@code null} if missing. */
    public String raw(String key) {
        return rawTemplate(key);
    }

    /** Façade: returns the raw configured template, falling back to {@code fallback} when missing. */
    public String raw(String key, String fallback) {
        String raw = rawTemplate(key);
        return raw != null ? raw : fallback;
    }

    private String rawTemplate(String key) {
        return yaml.getString(key);
    }

    /** Façade: sends a resolved message component to a sender. */
    public void send(CommandSender sender, String key, String... pairs) {
        if (sender == null) return;
        AudienceCompat.send(sender, component(key, pairs));
    }

    /** Façade: broadcasts a resolved message component to recipients. */
    public void broadcast(Iterable<? extends CommandSender> recipients, String key, String... pairs) {
        Component msg = component(key, pairs);
        for (CommandSender recipient : recipients) {
            if (recipient != null) {
                AudienceCompat.send(recipient, msg);
            }
        }
    }

    /** Façade: broadcasts a resolved message component to online players. */
    public void broadcast(JavaPlugin plugin, String key, String... pairs) {
        if (plugin == null) return;
        broadcast(plugin.getServer().getOnlinePlayers(), key, pairs);
    }

    /** Façade helper: parse MiniMessage/legacy-tag text to a component. */
    public static Component parse(String miniMessage) {
        return org.pvpcore.util.TextUtil.parse(miniMessage);
    }

    /** Façade helper: send a component to a sender. */
    public static void sendTo(CommandSender sender, Component message) {
        if (sender == null || message == null) return;
        AudienceCompat.send(sender, message);
    }

    /** Façade helper: broadcast a component to recipients. */
    public static void broadcastTo(Iterable<? extends CommandSender> recipients, Component message) {
        if (recipients == null || message == null) return;
        for (CommandSender recipient : recipients) {
            if (recipient != null) AudienceCompat.send(recipient, message);
        }
    }
}
