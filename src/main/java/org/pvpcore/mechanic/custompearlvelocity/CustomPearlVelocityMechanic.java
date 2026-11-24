package org.pvpcore.mechanic.custompearlvelocity;

import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows tweaking the vanilla velocity and gravity of Ender Pearls.
 *
 * <p>Practice servers often use a velocity multiplier (e.g., 1.15x) and a slight Y-boost
 * to make pearls feel "catchable" or to mimic specific server metas. This mechanic allows
 * those tweaks without touching the collision logic.</p>
 *
 * <p>Since Bukkit cannot modify gravity directly, this mechanic "nudges" the pearl's
 * velocity every tick to simulate a lower gravity multiplier.</p>
 */
public final class CustomPearlVelocityMechanic implements Mechanic, Listener {

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;

    private double velocityMultiplier;
    private double yBoost;
    private double gravityMultiplier;

    // Tracks active gravity-nudge tasks so we can cancel them cleanly
    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "custompearlvelocity";
    }

    @Override
    public String displayName() {
        return "Custom Pearl Velocity";
    }

    @Override
    public String description() {
        return "Tweaks Ender Pearl velocity, Y-boost, and gravity.";
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
        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);
        loadSettings();

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            active = true;
            plugin.getLogger().info("Custom Pearl Velocity enabled: " + describeSettings());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Custom Pearl Velocity could not register: " + t);
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) return;
        active = false;
        HandlerList.unregisterAll(this);

        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        plugin.getLogger().info("Custom Pearl Velocity disabled.");
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        this.debug = CoreConfig.isDebug(plugin);
        loadSettings();
        if (debug) {
            plugin.getLogger().info("[debug] Custom Pearl Velocity settings reloaded: " + describeSettings());
        }
    }

    private void loadSettings() {
        // Defaults: 1.0 (vanilla), 0.0 (no boost), 1.0 (vanilla gravity)
        velocityMultiplier = plugin.getConfig().getDouble("mechanics.custompearlvelocity.velocity-multiplier", 1.0);
        yBoost = plugin.getConfig().getDouble("mechanics.custompearlvelocity.y-boost", 0.0);
        gravityMultiplier = plugin.getConfig().getDouble("mechanics.custompearlvelocity.gravity-multiplier", 1.0);
    }

    private String describeSettings() {
        return String.format("velocity=%.2fx, y-boost=%.2f, gravity=%.2fx",
                velocityMultiplier, yBoost, gravityMultiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!active || !(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player)) {
            return;
        }

        // 1. Apply Velocity Multiplier & Y-Boost
        Vector velocity = pearl.getVelocity();

        // Multiply the X and Z axes for horizontal speed
        velocity.setX(velocity.getX() * velocityMultiplier);
        velocity.setZ(velocity.getZ() * velocityMultiplier);

        // Add the Y boost (and optionally multiply the vanilla Y)
        velocity.setY((velocity.getY() * velocityMultiplier) + yBoost);

        pearl.setVelocity(velocity);

        if (debug) {
            plugin.getLogger().info("[debug] Applied velocity tweaks to pearl for "
                    + pearl.getShooter() + ": " + String.format("%.2f/%.2f/%.2f",
                    velocity.getX(), velocity.getY(), velocity.getZ()));
        }

        // 2. Apply Gravity Nudge (if the multiplier is not 1.0)
        if (Math.abs(gravityMultiplier - 1.0) > 0.01) {
            startGravityNudge(pearl, gravityMultiplier);
        }
    }

    /**
     * Bukkit doesn't let us change gravity directly. To simulate lower/higher gravity,
     * we apply a tiny upward/downward velocity nudge every tick.
     */
    private void startGravityNudge(EnderPearl pearl, double multiplier) {
        UUID id = pearl.getUniqueId();

        // Vanilla pearl gravity is 0.03 blocks/tick.
        // If multiplier is 0.5, we want to add 0.015 upward every tick.
        // If multiplier is 2.0, we want to add 0.03 downward every tick.
        double vanillaGravity = 0.03;
        double nudgeAmount = (1.0 - multiplier) * vanillaGravity;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!pearl.isValid()) {
                cancelTask(id);
                return;
            }
            Vector vel = pearl.getVelocity();
            vel.setY(vel.getY() + nudgeAmount);
            pearl.setVelocity(vel);
        }, 1L, 1L);

        activeTasks.put(id, task);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl) {
            cancelTask(pearl.getUniqueId());
        }
    }

    private void cancelTask(UUID id) {
        BukkitTask task = activeTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }
}