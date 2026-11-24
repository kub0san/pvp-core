package org.pvpcore.mechanic.legacypearls;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.util.Vector;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;
import org.pvpcore.util.VersionSupport;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Restores the pre-26.2 Ender Pearl momentum behaviour.
 *
 * <p>Before 26.2 a pearl teleport did not touch the thrower's delta movement, so the
 * momentum the player had at the moment of the throw carried straight through the
 * teleport: the position packet that moves the client also carried that momentum, the
 * player never stopped for a tick.</p>
 *
 * <p>Since 26.2 the teleport resets momentum. Restoring it with a next-tick
 * {@link Player#setVelocity(Vector)} is not equivalent to the old behaviour: the client is
 * already stopped by the position packet, and it only learns about the restored velocity
 * one tick later through a {@code Set Entity Motion} packet. That produces a visible
 * one-tick "sticky" stop and a second velocity packet that vanilla never sent.</p>
 *
 * <p>This mechanic instead captures the exact pre-teleport velocity (all three axes, not
 * the look direction) in {@link PlayerTeleportEvent} and hands it to
 * {@link LegacyPearlPositionListener}, which writes it into the outgoing
 * {@code PLAYER_POSITION_AND_LOOK} packet before it is encoded. The client applies the
 * momentum together with the new position, exactly like a pre-26.2 server, with no task,
 * no delay and no extra packet.</p>
 *
 * <p>PacketEvents is used for the packet rewrite. When it is unavailable the mechanic
 * falls back to the old next-tick {@code setVelocity}, which still works but keeps the
 * one-tick stop; {@link #statusNote()} reports that degraded mode.</p>
 */
public final class LegacyPearlsMechanic implements Mechanic, Listener {

    /** A pending capture older than this can never belong to an in-flight teleport. */
    private static final long MAX_PENDING_NANOS = 1_000_000_000L;

    /**
     * Momentum captured just before a pearl teleport, in Bukkit velocity units
     * (blocks per tick), which are also the units of the position packet's delta.
     */
    record Momentum(double x, double y, double z, long capturedAtNanos) {

        static Momentum of(Vector velocity) {
            return new Momentum(velocity.getX(), velocity.getY(), velocity.getZ(),
                    System.nanoTime());
        }

        boolean isNegligible() {
            return Math.abs(x) < 1.0E-6 && Math.abs(y) < 1.0E-6 && Math.abs(z) < 1.0E-6;
        }

        /**
         * A captured teleport always sends its packet in the same server tick. Anything
         * older was never consumed (the teleport aborted between the event and the packet),
         * so it must not be applied to an unrelated later teleport.
         */
        boolean isStale() {
            return System.nanoTime() - capturedAtNanos > MAX_PENDING_NANOS;
        }
    }

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;
    /** True when the outgoing position packet is rewritten; false means the delayed fallback. */
    private volatile boolean packetRewrite;
    private final Map<UUID, Momentum> pending = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "legacypearls";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("legacyperls", "legacyperl");
    }

    @Override
    public String displayName() {
        return "Legacy Ender Pearls";
    }

    @Override
    public String description() {
        return "Keeps player momentum through an Ender Pearl teleport, like pre-26.2.";
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
        this.packetRewrite = false;
        this.pending.clear();
        this.active = false;

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);

            if (PacketEventsGate.ready()) {
                try {
                    packetRewrite = LegacyPearlPacketSupport.start(plugin, this);
                } catch (Throwable t) {
                    packetRewrite = false;
                    plugin.getLogger().log(Level.WARNING,
                            "Legacy Ender Pearls: PacketEvents packet rewrite failed; "
                                    + "falling back to next-tick velocity restore.", t);
                }
            }

            active = true;

            if (packetRewrite) {
                plugin.getLogger().info("Legacy Ender Pearls enabled: momentum is carried "
                        + "by the teleport packet (no tick delay).");
            } else {
                plugin.getLogger().info("Legacy Ender Pearls enabled with a one-tick delay ("
                        + (PacketEventsGate.ready()
                        ? "PacketEvents rewrite unavailable"
                        : PacketEventsGate.unavailableDetail())
                        + "). Install PacketEvents 2.14.0+ for seamless momentum.");
            }
            return true;
        } catch (Throwable t) {
            rollback();
            plugin.getLogger().log(Level.SEVERE, "Legacy Ender Pearls could not start", t);
            return false;
        }
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }

        active = false;
        pending.clear();

        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {
            // The listener may not have registered.
        }

        if (packetRewrite) {
            try {
                LegacyPearlPacketSupport.stop();
            } catch (Throwable ignored) {
                // PacketEvents may already be shutting down.
            }
            packetRewrite = false;
        }

        plugin.getLogger().info("Legacy Ender Pearls disabled.");
    }

    @Override
    public String statusNote() {
        if (!active) {
            return null;
        }
        if (!VersionSupport.isAtLeast("26.2")) {
            return "server already keeps pearl momentum";
        }
        if (packetRewrite) {
            return "momentum carried by the teleport packet";
        }
        return PacketEventsGate.ready()
                ? "PacketEvents rewrite failed; one-tick delay"
                : "one-tick delay; install PacketEvents for seamless momentum";
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("Legacy Ender Pearls debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }
    }

    // ------------------------------------------------------------------
    // Capture
    // ------------------------------------------------------------------

    /**
     * Captures the velocity before the teleport is applied. MONITOR runs after every
     * other plugin, and {@code ignoreCancelled} keeps protection plugins that block the
     * teleport from leaving a stale entry behind.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!active || event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        Player player = event.getPlayer();
        Vector velocity = player.getVelocity();
        Momentum momentum = Momentum.of(velocity);

        // A pearl thrown from a standstill has nothing to restore, and vanilla leaves the
        // player stopped in that case too. Skipping it avoids rewriting a zero delta.
        if (momentum.isNegligible()) {
            if (debug) {
                debug(player.getName() + " pearl teleport with no momentum; nothing to restore.");
            }
            return;
        }

        if (packetRewrite) {
            // Consumed by the position-packet listener on the network thread before the
            // packet leaves the server. One entry per player, overwritten on a spree of
            // pearls and cleared on quit, so this cannot grow.
            pending.put(player.getUniqueId(), momentum);
            if (debug) {
                debug(player.getName() + " pearl momentum "
                        + String.format("%.4f/%.4f/%.4f", momentum.x(), momentum.y(), momentum.z())
                        + " queued for the teleport packet.");
            }
            return;
        }

        // Degraded path: no PacketEvents. Paper applies the teleport after this event, so
        // the restore has to wait for the next tick.
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active && player.isOnline()) {
                    player.setVelocity(new Vector(momentum.x(), momentum.y(), momentum.z()));
                    if (debug) {
                        debug(player.getName() + " pearl momentum restored one tick late.");
                    }
                }
            });
        } catch (IllegalStateException ignored) {
            // Plugin is shutting down.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (active && !packetRewrite && PacketEventsGate.ready()) {
            try {
                packetRewrite = LegacyPearlPacketSupport.start(plugin, this);
                if (packetRewrite) {
                    plugin.getLogger().info("Legacy Ender Pearls: PacketEvents rewrite activated after server load.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, 
                        "Legacy Ender Pearls: PacketEvents packet rewrite failed on late load.", t);
            }
        }
    }

    // ------------------------------------------------------------------
    // Packet listener access
    // ------------------------------------------------------------------

    /** Removes and returns the momentum waiting for this player's next position packet. */
    Momentum take(UUID playerId) {
        Momentum momentum = pending.remove(playerId);
        if (momentum == null || momentum.isStale()) {
            return null;
        }
        return momentum;
    }

    boolean isDebug() {
        return debug;
    }

    void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    void reportException(String where, Throwable throwable) {
        if (plugin != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Legacy Ender Pearls " + where + " failed", throwable);
        }
    }

    private void rollback() {
        active = false;
        pending.clear();
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {
            // The listener may not have registered yet.
        }
        if (packetRewrite) {
            try {
                LegacyPearlPacketSupport.stop();
            } catch (Throwable ignored) {
                // PacketEvents may already be shutting down.
            }
            packetRewrite = false;
        }
    }
}
