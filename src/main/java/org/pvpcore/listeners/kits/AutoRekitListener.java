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
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.RekitKitResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;

public class AutoRekitListener implements Listener {
    private final Plugin plugin;
    private final KitsContext kits;
    private final Map<UUID, BukkitTask> pendingRespawns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> warnedMissingKits = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AutoRekitListener(Plugin plugin, KitsContext kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (kits == null || !kits.isEnabled() || kits.config() == null) return;
        if (!kits.config().getBoolean("rekit.respawn.enabled", true)) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("perplayerkit.rekitonrespawn")) return;
        long delay = Math.max(1, kits.config().getLong("rekit.respawn.delay-ticks", 0));
        // Check the actual destination after respawning, including later teleports during the delay.
        cancelRespawn(player);
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingRespawns.remove(player.getUniqueId());
            if (plugin.getServer().getPlayer(player.getUniqueId()) == player)
                restore(player, "rekit.respawn", "perplayerkit.rekitonrespawn", LocationFeature.REKIT_RESPAWN);
        }, delay);
        pendingRespawns.put(player.getUniqueId(), task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { cancelRespawn(event.getPlayer()); }

    private void cancelRespawn(Player player) {
        BukkitTask task = pendingRespawns.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        if (kits == null || !kits.isEnabled() || kits.config() == null) return;
        if (!kits.config().getBoolean("rekit.kill.enabled", false)) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null) restore(killer, "rekit.kill", "perplayerkit.rekitonkill", LocationFeature.REKIT_KILL);
    }

    private void restore(Player player, String path, String permission, LocationFeature feature) {
        if (kits == null || kits.config() == null) return;
        KitManager km = kits.kitManager();
        LocationAccess locationAccess = kits.locationAccess();
        if (!player.isOnline() || player.isDead() || !player.hasPermission(permission)
                || km == null || km.isLoading(player.getUniqueId())
                || (locationAccess != null && !locationAccess.check(player, feature).allowed())) return;
        ConfigurationSection mappings = kits.config().getConfigurationSection(path + ".kits");
        List<String> regions = List.of();
        if (RekitKitResolver.hasRegionEntries(mappings, player.getWorld().getName())) {
            if (locationAccess == null) return;
            try { regions = locationAccess.regions(player); }
            catch (LocationAccess.RegionUnavailableException e) {
                locationAccess.warn(path + ".kits", player, e);
                return;
            }
        }
        String configured = RekitKitResolver.resolveKit(mappings, player.getWorld().getName(), regions);
        if (configured == null || !giveConfiguredPublicKit(player, km, configured, path)) {
            KitManager.KitReference ref = km.getLastKitReference(player.getUniqueId());
            if (ref != null && player.hasPermission(ref.publicId() == null ? "perplayerkit.kit" : "perplayerkit.publickit"))
                km.loadLastKit(player);
        }
        if (kits.config().getBoolean(path + ".restore-enderchest", false)
                && player.hasPermission("perplayerkit.enderchest")) km.restoreLastEnderchest(player);
    }

    private boolean giveConfiguredPublicKit(Player player, KitManager km, String configured, String path) {
        if (km == null || !player.hasPermission("perplayerkit.publickit")) return false;
        String id = km.getPublicKitList().stream().map(kit -> kit.id)
                .filter(key -> key.equalsIgnoreCase(configured)).findFirst().orElse(configured);
        if (km.hasPublicKit(id)) return km.loadPublicKitSilent(player, id);
        if (warnedMissingKits.add(path + ":" + configured)) plugin.getLogger().warning(path
                + " is configured to give public kit '" + configured + "' but that kit has no saved contents. "
                + "Using the player's last loaded kit instead.");
        return false;
    }
}
