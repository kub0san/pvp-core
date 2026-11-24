package org.pvpcore.features.combat;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.pvpcore.config.Messages;

import java.io.File;
import java.util.logging.Logger;

/**
 * Minimal reader for combat messages, loaded from {@code messages.yml} (the {@code pvpcore}
 * section, falling back to the root). Keys are plain MiniMessage with {@code {player}} style
 * placeholders. Absent file/keys fall back to the built-in defaults rather than failing.
 *
 * <p>Holds volatile copies so a {@code /pvpcore reload} refreshes messages without re-wiring
 * listeners.</p>
 */
final class CombatMessages {

    private static final String LOGGED_OUT_FALLBACK = "<red>{player} has combat logged out!</red>";
    private static final String SAFE_ZONE_DENIED_FALLBACK = "<red>You can't enter safe zones while in combat.</red>";

    private final File messagesFile;
    private final Logger logger;

    private volatile String loggedOut = LOGGED_OUT_FALLBACK;
    private volatile String safeZoneDenied = SAFE_ZONE_DENIED_FALLBACK;

    CombatMessages(File messagesFile, Logger logger) {
        this.messagesFile = messagesFile;
        this.logger = logger;
        reload();
    }

    /** Re-reads the file from disk. Safe to call on /pvp-core reload; never throws. */
    void reload() {
        YamlConfiguration yaml = loadYaml();
        this.loggedOut = readString(yaml, "combat.logged-out", LOGGED_OUT_FALLBACK);
        this.safeZoneDenied = readString(yaml, "combat.safe-zone-denied", SAFE_ZONE_DENIED_FALLBACK);
    }

    Component loggedOut(String playerName) {
        return Messages.parse(loggedOut.replace("{player}", playerName));
    }

    Component safeZoneDenied() {
        return Messages.parse(safeZoneDenied);
    }

    private YamlConfiguration loadYaml() {
        if (messagesFile == null || !messagesFile.exists()) {
            return null;
        }
        try {
            return YamlConfiguration.loadConfiguration(messagesFile);
        } catch (RuntimeException error) {
            if (logger != null) {
                logger.warning("combat: could not read messages.yml (" + error.getMessage() + "); using built-in defaults.");
            }
            return null;
        }
    }

    private String readString(YamlConfiguration yaml, String key, String fallback) {
        if (yaml == null) {
            return fallback;
        }
        ConfigurationSection section = yaml.getConfigurationSection("pvpcore");
        if (section != null && section.isString(key)) {
            return section.getString(key, fallback);
        }
        if (yaml.isString(key)) {
            return yaml.getString(key, fallback);
        }
        return fallback;
    }
}
