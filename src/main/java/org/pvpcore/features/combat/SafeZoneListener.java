package org.pvpcore.features.combat;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks combat-tagged players from entering WorldGuard safe zones (regions with {@code pvp: deny}).
 * Adapted from NikeyV1/CombatLog (GPL-3.0+), trimmed to entry blocking without the barrier visuals.
 *
 * <p>This class references WorldGuard types, so it may only be loaded when the plugin is present.
 * {@link CombatTagFeature} creates it only after {@code Bukkit.getPluginManager().getPlugin("WorldGuard")}
 * is non-null; servers without WorldGuard never class-load it.</p>
 */
final class SafeZoneListener implements Listener {

    private static final long DENY_MESSAGE_COOLDOWN_MS = 2000L;

    private final CombatManager combatManager;
    private final CombatMessages messages;

    /** Last time each player got a denial message, to avoid per-tick spam while pushing the wall. */
    private final Map<UUID, Long> lastDenyMessageAt = new ConcurrentHashMap<>();

    SafeZoneListener(CombatManager combatManager, CombatMessages messages) {
        this.combatManager = combatManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return; // No destination (disconnect/world change): nothing to check.
        }
        if (!hasMovedBlock(event)) {
            return;
        }
        Player player = event.getPlayer();
        if (!combatManager.isInCombat(player.getUniqueId())) {
            return;
        }
        // Block only the entry: from a non-safe-zone block into a safe-zone block. A tagged
        // player already inside a safe zone (tagged by an explosion that ignores regions, or
        // admin-teleported in) can move within it and leave; only re-entry is blocked.
        // Short-circuit order: `from` first, so a player already inside a safe zone pays for
        // only one RegionQuery per move, not two (the query cache makes the common wilderness
        // path cheap as well).
        if (!isSafeZone(event.getFrom(), player) && isSafeZone(event.getTo(), player)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    // Highest as well: teleport blocking should run after other plugins have decided the destination.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!combatManager.isInCombat(player.getUniqueId())) {
            return;
        }
        if (!isSafeZone(event.getFrom(), player) && isSafeZone(event.getTo(), player)) {
            event.setCancelled(true);
            deny(player);
        }
    }

    /** Drops the per-player deny cooldown state when a player leaves. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer().getUniqueId());
    }

    private boolean isSafeZone(Location location, Player player) {
        if (location == null) {
            return false;
        }
        if (!combatManager.config().safeZoneMoveBlocking()) {
            return false;
        }
        try {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            var localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            // pvp flag deny => safe zone.
            return !query.testState(BukkitAdapter.adapt(location), localPlayer, Flags.PVP);
        } catch (Exception | LinkageError error) {
            // WorldGuard vanished mid-session (or its registry is not ready): stop
            // enforcing rather than leak errors on every move.
            return false;
        }
    }

    private boolean hasMovedBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }

    private void deny(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastDenyMessageAt.get(player.getUniqueId());
        if (last == null || now - last >= DENY_MESSAGE_COOLDOWN_MS) {
            lastDenyMessageAt.put(player.getUniqueId(), now);
            player.sendMessage(messages.safeZoneDenied());
        }
    }

    void removePlayer(UUID playerId) {
        lastDenyMessageAt.remove(playerId);
    }
}
