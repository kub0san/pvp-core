package org.pvpcore.util.kits;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.PvPCorePlugin;

/**
 * Manages playing configurable sounds for plugin feedback (success, error, UI interactions).
 */
public class SoundManager {
    private static final String BASE_KEY = "sounds.";

    private static Sound getSound(String key, String defaultName) {
        String path = BASE_KEY + key;
        var kits = kitsContext();
        String soundName = kits != null && kits.config() != null ? kits.config().getString(path, defaultName) : defaultName;

        // 1. Try to get the configured sound
        if (soundName != null) {
            // Convert something like ENTITY_PLAYER_LEVELUP to entity.player.levelup for the registry
            String cleanName = soundName.toLowerCase().replace('_', '.');
            Sound sound = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.fromString(cleanName));
            if (sound != null) {
                return sound;
            } else {
                if (kits != null && kits.plugin() != null) {
                    kits.plugin().getLogger()
                            .warning("Invalid sound '" + soundName + "' for config key '" + path + "'. Using default '" + defaultName + "'.");
                }
            }
        }

        // 2. Try to get the default sound
        if (defaultName != null) {
            String cleanDefault = defaultName.toLowerCase().replace('_', '.');
            Sound fallback = org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.fromString(cleanDefault));
            if (fallback != null) {
                return fallback;
            }
        }

        // 3. Ultimate fallback
        return Sound.UI_BUTTON_CLICK;
    }

    public static void playSuccess(Player player) {
        play(player, getSound("success", "ENTITY_PLAYER_LEVELUP"));
    }

    public static void playFailure(Player player) {
        play(player, getSound("failure", "ENTITY_ITEM_BREAK"));
    }

    public static void playClick(Player player) {
        play(player, getSound("click", "UI_BUTTON_CLICK"));
    }

    public static void playOpenGui(Player player) {
        play(player, getSound("open-gui", "UI_BUTTON_CLICK"));
    }

    public static void playCloseGui(Player player) {
        play(player, getSound("close-gui", "UI_BUTTON_CLICK"));
    }

    private static void play(Player player, Sound sound) {
        var kits = kitsContext();
        if (kits != null && kits.config() != null && !kits.config().getBoolean("sounds.enabled", true)) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    private static org.pvpcore.features.kits.KitsContext kitsContext() {
        try {
            PvPCorePlugin plugin = JavaPlugin.getPlugin(PvPCorePlugin.class);
            return plugin == null ? null : plugin.kitsContext();
        } catch (Throwable ignored) {
            return null;
        }
    }
}