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
import org.pvpcore.util.CooldownManager;
import org.pvpcore.util.AudienceCompat;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BroadcastManager {

    private static final int LINE_LENGTH = 60; // Length of the strikethrough line
    private static final String FIGURE_SPACE = " "; // A whitespace character of consistent width
    private static BroadcastManager instance;
    private final int broadcastDistance = 200;
    private final Plugin plugin;
    private final org.pvpcore.features.kits.KitsContext context;
    private final CooldownManager repairBroadcastCooldown = new CooldownManager(5);
    private final CooldownManager kitroomBroadcastCooldown = new CooldownManager(15);
    private final BukkitAudiences audience;
    private BukkitTask scheduledBroadcastTask;

    public BroadcastManager(org.pvpcore.features.kits.KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        audience = BukkitAudiences.create(plugin);
        instance = this;
    }

    private org.bukkit.configuration.file.FileConfiguration cfg() {
        return context.config();
    }

    private Lang lang() {
        return context.lang();
    }

    /** why: stateless broadcast formatting utility retained for external/legacy compatibility. */
    public static Component generateBroadcastComponent(String message) {
        String strikeThroughLine = "<gray>" + " ".repeat(3) + "<st>" + FIGURE_SPACE.repeat(LINE_LENGTH) + "</st>";

        int messageLength = MiniMessage.miniMessage().stripTags(message).length();

        int padding = (LINE_LENGTH - messageLength) / 2;

        String formattedMessage = strikeThroughLine + "\n\n" + " ".repeat(3) + FIGURE_SPACE.repeat(Math.max(padding, 0)) + message + "\n\n" + strikeThroughLine;

        return Messages.parse(formattedMessage);
    }

    private boolean isKitLoadingMessage(MessageKey key) {
        return key == MessageKey.PLAYER_LOADED_PRIVATE_KIT ||
                key == MessageKey.PLAYER_LOADED_PUBLIC_KIT ||
                key == MessageKey.PLAYER_LOADED_ENDER_CHEST;
    }

    private void broadcastMessage(Player player, String message, String permission) {
        World world = player.getWorld();

        Lang l = lang();
        Component prefix = l != null ? l.prefix() : Component.empty();
        Component body = TextUtil.parse(message);
        for (Player broadcastPlayer : world.getPlayers()) {
            if (broadcastPlayer.getLocation().distance(player.getLocation()) < broadcastDistance) {
                if (broadcastPlayer.hasPermission(permission)) {
                    sendComponentMessage(broadcastPlayer, prefix.append(body));
                }
            }
        }
    }

    private void broadcastMessage(Player player, MessageKey key, CooldownManager cooldownManager) {
        broadcastMessage(player, key, cooldownManager, null);
    }

    private void broadcastMessage(Player player, MessageKey key, CooldownManager cooldownManager, String kitName) {
        org.bukkit.configuration.file.FileConfiguration c = cfg();
        if (c == null || !c.getBoolean("broadcasts.enabled", true)) return;

        if (cooldownManager != null && cooldownManager.isOnCooldown(player)) {
            return;
        }

        String configBase = key.getConfigKey();
        String enabledPath = configBase + ".enabled";
        String permissionPath = configBase + ".permission";

        if (!c.getBoolean(enabledPath, true)) {
            return;
        }

        String permission = c.getString(permissionPath, "perplayerkit.kitnotify");

        String playerName;
        if (c.getBoolean("broadcasts.use-display-name", false)) {
            playerName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(player.displayName());
        } else {
            playerName = player.getName();
        }

        Lang l = lang();
        if (l == null) return;
        String message = l.raw(key.getLangKey(), Map.of(
                "player", playerName,
                "kitname", kitName == null ? "" : kitName
        ));

        broadcastMessage(player, message, permission);

        if (cooldownManager != null) {
            cooldownManager.setCooldown(player);
        }
    }

    public void broadcastPlayerRepaired(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_REPAIRED, repairBroadcastCooldown);
    }

    public void broadcastPlayerHealed(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_HEALED, null);
    }

    public void broadcastPlayerOpenedKitRoom(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_OPENED_KIT_ROOM, kitroomBroadcastCooldown);
    }

    public void broadcastPlayerLoadedPrivateKit(Player player, String kitName) {
        broadcastMessage(player, MessageKey.PLAYER_LOADED_PRIVATE_KIT, null, kitName);
    }

    public void broadcastPlayerLoadedPublicKit(Player player, String kitName) {
        broadcastMessage(player, MessageKey.PLAYER_LOADED_PUBLIC_KIT, null, kitName);
    }

    public void broadcastPlayerLoadedEnderChest(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_LOADED_ENDER_CHEST, null);
    }

    public void broadcastPlayerCopiedKit(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_COPIED_KIT, null);
    }

    public void broadcastPlayerCopiedEC(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_COPIED_EC, null);
    }

    public void broadcastPlayerRegeared(Player player) {
        broadcastMessage(player, MessageKey.PLAYER_REGEARED, null);
    }

    public void startScheduledBroadcast() {
        List<Component> messages = new ArrayList<>();
        Lang l = lang();
        if (l != null) {
            l.rawList("scheduled-broadcast.messages").forEach(message -> messages.add(generateBroadcastComponent(message)));
        }

        int[] index = {0};

        if (scheduledBroadcastTask != null) {
            scheduledBroadcastTask.cancel();
            scheduledBroadcastTask = null;
        }

        org.bukkit.configuration.file.FileConfiguration c = cfg();
        boolean enabled = c == null || c.getBoolean("enabled", true);
        if (enabled && c != null && c.getBoolean("broadcasts.scheduled.enabled") && !messages.isEmpty()) {
            scheduledBroadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendComponentMessage(player, messages.get(index[0]));
                }
                index[0] = (index[0] + 1) % messages.size();
            }, 0, Math.max(1, c.getInt("broadcasts.scheduled.period-seconds", 90)) * 20L);
        }
    }

    /** Every component this class sends to a player goes through here. */
    public void sendComponentMessage(Player player, Component message) {
        // Paper's own chat API first: adventure-platform silently delivers
        // nothing on servers newer than it knows about. See AudienceCompat.
        if (AudienceCompat.send(player, message)) {
            return;
        }
        audience.player(player).sendMessage(message);
    }

    public enum MessageKey {
        PLAYER_REPAIRED("broadcasts.actions.player-repaired", "broadcast-messages.player-repaired"),
        PLAYER_HEALED("broadcasts.actions.player-healed", "broadcast-messages.player-healed"),
        PLAYER_OPENED_KIT_ROOM("broadcasts.actions.player-opened-kit-room", "broadcast-messages.player-opened-kit-room"),
        PLAYER_LOADED_PRIVATE_KIT("broadcasts.actions.player-loaded-private-kit", "broadcast-messages.player-loaded-private-kit"),
        PLAYER_LOADED_PUBLIC_KIT("broadcasts.actions.player-loaded-public-kit", "broadcast-messages.player-loaded-public-kit"),
        PLAYER_LOADED_ENDER_CHEST("broadcasts.actions.player-loaded-enderchest", "broadcast-messages.player-loaded-enderchest"),
        PLAYER_COPIED_KIT("broadcasts.actions.player-copied-kit", "broadcast-messages.player-copied-kit"),
        PLAYER_COPIED_EC("broadcasts.actions.player-copied-ec", "broadcast-messages.player-copied-ec"),
        PLAYER_REGEARED("broadcasts.actions.player-regeared", "broadcast-messages.player-regeared");

        private final String configKey;
        private final String langKey;

        MessageKey(String configKey, String langKey) {
            this.configKey = configKey;
            this.langKey = langKey;
        }

        public String getConfigKey() {
            return configKey;
        }

        public String getLangKey() {
            return langKey;
        }
    }

    /** why: static shutdown retained for module disable/reload lifecycle cleanup of singleton-style state. */
    public static void shutdown() {
        BroadcastManager current = instance;
        if (current != null) {
            if (current.scheduledBroadcastTask != null) {
                current.scheduledBroadcastTask.cancel();
                current.scheduledBroadcastTask = null;
            }
            if (current.audience != null) {
                try {
                    current.audience.close();
                } catch (RuntimeException ignored) {
                    // The server may already be tearing the audience down.
                }
            }
        }
        instance = null;
    }
}
