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
package org.pvpcore.listeners.kits;

import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.UpdateChecker;
import org.pvpcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class JoinListener implements Listener {

    private final Plugin plugin;
    private final KitsContext kits;
    private final UpdateChecker updateChecker;

    public JoinListener(Plugin plugin, KitsContext kits, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.kits = kits;
        this.updateChecker = updateChecker;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (kits == null || !kits.isEnabled()) {
            // Kits disabled: no data loading, update notices or MOTD messages.
            return;
        }
        Player player = e.getPlayer();
        var config = kits.config();

        if (player.hasPermission("perplayerkit.admin") && config != null
                && config.getBoolean("updates.notify-admins-on-join", true)) {
            updateChecker.sendUpdateMessage(player);
        }

        UUID uuid = player.getUniqueId();
        KitManager km = kits.kitManager();
        if (km != null) {
            km.loadPlayerDataAsync(uuid);
        }

        // Check if MOTD is enabled and send MOTD messages
        if (config != null && config.getBoolean("motd.enabled")) {
            List<Component> motdMessages = new ArrayList<>();
            if (kits.lang() != null) {
                kits.lang().rawList("motd.message").forEach(message -> motdMessages.add(TextUtil.parse(message)));
            }

            // Delay for sending the MOTD
            long delaySeconds = config.getLong("motd.delay-seconds");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && kits.broadcastManager() != null) {
                    motdMessages.forEach(message -> kits.broadcastManager().sendComponentMessage(player, message));
                }
            }, delaySeconds * 20L);
        }
    }


}


