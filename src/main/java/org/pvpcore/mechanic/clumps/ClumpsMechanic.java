package org.pvpcore.mechanic.clumps;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;

/**
 * Clumps: instantly merges nearby XP orbs and instantly absorbs them when a player is close.
 *
 * <p>Unlike a timer-based approach, this listens to {@link EntitySpawnEvent} to handle
 * orbs the exact tick they are created. This matches the "PvP Clumps" behavior:</p>
 * <ol>
 *   <li>If a player is within {@code instant-collect-radius}, the orb never spawns;
 *       the XP is given directly to the player via {@link Player#giveExp(int, boolean)},
 *       preserving Mending logic.</li>
 *   <li>If no player is near, it looks for an existing orb within {@code merge-radius}.
 *       If found, it adds the XP to that orb and cancels the new spawn.</li>
 * </ol>
 */
public final class ClumpsMechanic implements Mechanic, Listener {

    /** Vanilla experience orbs store their value as a short. */
    private static final int MAX_ORB_XP = Short.MAX_VALUE;

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;

    private volatile double mergeRadius;
    private volatile double instantCollectRadius;
    private volatile boolean enableInstantCollect;

    @Override
    public String id() {
        return "clumps";
    }

    @Override
    public String displayName() {
        return "Clumps";
    }

    @Override
    public String description() {
        return "Instantly merges nearby XP orbs and instantly absorbs them near players, with Mending support.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (active) {
            return true;
        }
        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);
        if (!loadSettings()) {
            return false;
        }
        try {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            active = true;
            plugin.getLogger().info("Clumps enabled: merge radius " + mergeRadius
                    + (enableInstantCollect ? ", instant collect radius " + instantCollectRadius : "")
                    + ".");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Clumps could not register listener: " + t);
            return false;
        }
    }

    private boolean loadSettings() {
        try {
            mergeRadius = Math.max(0.5,
                    plugin.getConfig().getDouble("mechanics.clumps.merge-radius", 2.0));
            instantCollectRadius = Math.max(0.1,
                    plugin.getConfig().getDouble("mechanics.clumps.instant-collect-radius", 1.5));
            enableInstantCollect = plugin.getConfig().getBoolean(
                    "mechanics.clumps.enable-instant-collect", true);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Clumps could not read its config: " + t);
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }
        active = false;
        HandlerList.unregisterAll(this);
        plugin.getLogger().info("Clumps disabled.");
    }

    @Override
    public String statusNote() {
        if (!active) {
            return null;
        }
        return "instant merging, "
                + (enableInstantCollect ? "instant collect (Mending supported)" : "instant collect off");
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        loadSettings();

        if (wasDebug != debug) {
            plugin.getLogger().info("Clumps debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }

        if (debug) {
            debug(() -> "Settings: mergeRadius=" + mergeRadius
                    + ", instantRadius=" + instantCollectRadius
                    + ", instant=" + enableInstantCollect);
        }
    }

    // ------------------------------------------------------------------
    // Instant Processing
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOrbSpawn(EntitySpawnEvent event) {
        if (!active || !(event.getEntity() instanceof ExperienceOrb orb)) {
            return;
        }

        int amount = orb.getExperience();
        if (amount <= 0) {
            return;
        }

        // 1. Instant collect (Mending-aware)
        if (enableInstantCollect) {
            double radiusSq = instantCollectRadius * instantCollectRadius;
            for (Player player : orb.getWorld().getPlayers()) {
                if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (player.getLocation().distanceSquared(orb.getLocation()) <= radiusSq) {
                    event.setCancelled(true);
                    try {
                        player.giveExp(amount, true);
                        debug(() -> "Instantly collected " + amount + " XP for " + player.getName());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Clumps could not give " + amount + " XP to " + player.getName() + ": " + t);
                    }
                    return;
                }
            }
        }

        // 2. Instant merge (always, now that min-xp-to-merge is gone)
        double radiusSq = mergeRadius * mergeRadius;
        ExperienceOrb nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        // Look for an existing valid orb to merge into
        for (Entity entity : orb.getNearbyEntities(mergeRadius, mergeRadius, mergeRadius)) {
            if (entity instanceof ExperienceOrb other && other.isValid() && !other.equals(orb)) {
                double distSq = other.getLocation().distanceSquared(orb.getLocation());
                if (distSq <= radiusSq && distSq < nearestDistSq) {
                    nearest = other;
                    nearestDistSq = distSq;
                }
            }
        }

        if (nearest != null) {
            int existingXp = nearest.getExperience();
            int total = existingXp + amount;

            if (total <= MAX_ORB_XP) {
                // Standard merge: add to existing, cancel new spawn
                nearest.setExperience(total);
                event.setCancelled(true);
                debug(() -> "Instantly merged " + amount + " XP into orb (now " + total + ")");
            } else {
                // Overflow: fill the existing orb, let the new orb spawn with the remainder
                nearest.setExperience(MAX_ORB_XP);
                orb.setExperience(total - MAX_ORB_XP);
                // We DO NOT cancel the event; the new orb spawns with the leftover XP
                debug(() -> "Instantly merged into max capacity. Existing orb capped at " + MAX_ORB_XP
                        + ", new orb spawned with " + orb.getExperience() + " XP.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------

    private void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    private void debug(java.util.function.Supplier<String> message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message.get());
        }
    }
}