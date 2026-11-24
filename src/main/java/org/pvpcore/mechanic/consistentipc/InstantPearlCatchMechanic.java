package org.pvpcore.mechanic.consistentipc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Fixes the vanilla inconsistencies with Instant Pearl Catches (IPC) using PacketEvents.
 *
 * <p>Vanilla pearl catches are inconsistent because of two issues:</p>
 * <ul>
 *   <li><b>Early Hits:</b> If a pearl hits a wind charge before tick 5, the catch results in
 *       a weak, useless 1.5-block "hop". (Fixed via {@link ProjectileHitEvent}).</li>
 *   <li><b>Tick-Order Dependency:</b> If a pearl and wind charge are thrown on the exact
 *       same tick, the server may process them in the wrong order, causing the pearl to miss.</li>
 * </ul>
 *
 * <p>This mechanic uses PacketEvents to monitor incoming {@code USE_ITEM} packets on the network
 * thread. If it detects a player rapidly clicking to throw both items in the same tick, it flags
 * the launch and manually resolves the collision on the next tick. This mimics the
 * {@code EntityTickListMixin} from the InstantPearlCatch mod.</p>
 */
public final class InstantPearlCatchMechanic implements Mechanic, Listener {

    private static final int MIN_CATCH_TICKS = 5;
    private static final long IPC_WINDOW_NANOS = 50_000_000L; // 50ms (1 tick)

    private PvPCorePlugin plugin;
    private volatile boolean active;

    // Tracks the exact nanoTime of the last USE_ITEM packet per player (Network Thread)
    private final Map<UUID, Long> lastUseItemPacket = new ConcurrentHashMap<>();
    // Tracks active projectiles awaiting manual collision resolution (Main Thread)
    private final Map<UUID, WindCharge> activeWindCharges = new ConcurrentHashMap<>();
    private final Map<UUID, EnderPearl> activePearls = new ConcurrentHashMap<>();

    private PacketListenerAbstract packetListener;

    @Override
    public String id() {
        return "consistentipc";
    }

    @Override
    public String displayName() {
        return "Consistent IPC";
    }

    @Override
    public String description() {
        return "Uses PacketEvents to ensure 100% consistent Instant Pearl Catches.";
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
    public boolean requiresPacketEvents() {
        return true;
    }

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (active) {
            return true;
        }
        this.plugin = plugin;

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);

            packetListener = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
                @Override
                public void onPacketReceive(PacketReceiveEvent event) {
                    if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
                        if (event.getUser() != null && event.getUser().getUUID() != null) {
                            lastUseItemPacket.put(event.getUser().getUUID(), System.nanoTime());
                        }
                    }
                }
            };
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);

            active = true;
            plugin.getLogger().info("Consistent IPC enabled: network-level tick-order tracking active.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Consistent IPC could not start", t);
            rollback();
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) return;
        active = false;

        HandlerList.unregisterAll(this);

        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable ignored) {}
            packetListener = null;
        }

        lastUseItemPacket.clear();
        activeWindCharges.clear();
        activePearls.clear();
        plugin.getLogger().info("Consistent IPC disabled.");
    }

    @Override
    public String statusNote() {
        return active ? "network tick tracking active" : null;
    }

    // ------------------------------------------------------------------
    // 1. Early Hit Prevention (ProjectileMixin equivalent)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!active) return;

        if (event.getEntity() instanceof EnderPearl pearl && event.getHitEntity() instanceof WindCharge) {
            if (pearl.getTicksLived() < MIN_CATCH_TICKS) {
                event.setCancelled(true);
                if (CoreConfig.isDebug(plugin)) {
                    plugin.getLogger().info("[debug] Blocked early IPC on tick " + pearl.getTicksLived());
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. Tick-Order Resolution (EntityTickListMixin equivalent)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!active || !(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        Long packetTime = lastUseItemPacket.get(uuid);

        // If the packet was sent in the last tick (50ms), we know this is a rapid IPC attempt.
        boolean isRapidThrow = packetTime != null && (System.nanoTime() - packetTime) <= IPC_WINDOW_NANOS;

        if (event.getEntity() instanceof WindCharge windCharge) {
            activeWindCharges.put(uuid, windCharge);
            if (isRapidThrow) {
                scheduleIpcCheck(player);
            }
        } else if (event.getEntity() instanceof EnderPearl pearl) {
            activePearls.put(uuid, pearl);
            if (isRapidThrow) {
                scheduleIpcCheck(player);
            }
        }
    }

    private void scheduleIpcCheck(Player player) {
        // Run at the end of the tick to check if they collided, bypassing vanilla tick order
        Bukkit.getScheduler().runTask(plugin, () -> resolveIpc(player));
    }

    private void resolveIpc(Player player) {
        if (!active || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        EnderPearl pearl = activePearls.remove(uuid);
        WindCharge charge = activeWindCharges.remove(uuid);

        if (pearl != null && charge != null && pearl.isValid() && charge.isValid()) {
            if (pearl.getTicksLived() < MIN_CATCH_TICKS) {
                return; // Too early, let them fly further
            }

            // Check if they are overlapping (the "Already Inside" bug fix)
            BoundingBox chargeBox = charge.getBoundingBox().expand(0.3);

            if (chargeBox.overlaps(pearl.getBoundingBox()) ||
                    pearl.getLocation().distanceSquared(charge.getLocation()) < 2.0) {

                forceCatch(player, pearl, charge);
            }
        }
    }

    private void forceCatch(Player player, EnderPearl pearl, WindCharge charge) {
        charge.getWorld().createExplosion(pearl.getLocation(), 1.2f, false, false, charge);
        charge.remove();

        // By using ENDER_PEARL, we trigger your LegacyPearlsMechanic to restore momentum
        player.teleport(pearl.getLocation(), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        pearl.remove();

        if (CoreConfig.isDebug(plugin)) {
            plugin.getLogger().info("[debug] Forced IPC catch for " + player.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastUseItemPacket.remove(uuid);
        activeWindCharges.remove(uuid);
        activePearls.remove(uuid);
    }

    private void rollback() {
        active = false;
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {}
        if (packetListener != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            } catch (Throwable ignored) {}
            packetListener = null;
        }
    }
}