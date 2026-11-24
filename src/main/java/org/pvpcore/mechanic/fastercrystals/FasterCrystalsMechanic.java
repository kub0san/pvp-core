package org.pvpcore.mechanic.fastercrystals;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;

/**
 * Faster Crystals: reacts to the client's swing and interact packets so crystals break and
 * place without waiting for the client's action packets to be processed.
 *
 * <p>Requires PacketEvents 2.14.0 or newer. PacketEvents is a soft dependency: without it
 * the mechanic refuses to enable and every other mechanic keeps working. Placement keeps
 * PvPCore's protection hooks: {@link FastCrystalPlaceEvent} fires before the spawn and a
 * synthetic Bukkit {@code EntityPlaceEvent} fires after it.</p>
 *
 * <p>Accepted packets hand their main-thread work to the next-tick Bukkit scheduler,
 * matching the reference plugin's behaviour on Paper (its Folia region scheduler collapses
 * to the same path on non-Folia servers).</p>
 *
 * <p>Two behaviours are intentionally copied from the reference implementation:</p>
 * <ul>
 *   <li>a destroyed crystal stays tracked for 40 ticks, because the placement packet often
 *       arrives just after the destroy and needs the old position;</li>
 *   <li>swings are classified with the packet that preceded them, so dropping an item or
 *       using an item does not attack a crystal.</li>
 * </ul>
 */
public final class FasterCrystalsMechanic implements Mechanic, Listener {

    /**
     * Placement outcomes. Counting every early return makes skipped placements visible
     * without enabling per-event debug logging.
     */
    enum Outcome {
        PLACED,
        NO_CRYSTAL,
        SPECTATOR,
        ADVENTURE,
        CROSS_WORLD,
        WRONG_ITEM,
        NO_RANGE,
        NO_BLOCK,
        BAD_BASE,
        WRONG_BASE,
        OUTSIDE_BORDER,
        SPACE_BLOCKED,
        OCCUPIED,
        PLACE_CANCELLED,
        ENTITY_PLACE_CANCELLED,
        NOT_CONSUMED,
        EXCEPTION
    }

    private final LongAdder[] outcomes = new LongAdder[Outcome.values().length];
    private final AtomicBoolean exceptionLogged = new AtomicBoolean();

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;

    private CrystalTracker tracker;
    private Map<UUID, PlayerPacketState> states;

    public FasterCrystalsMechanic() {
        for (int i = 0; i < outcomes.length; i++) {
            outcomes[i] = new LongAdder();
        }
    }

    @Override
    public String id() {
        return "fastercrystals";
    }

    @Override
    public String displayName() {
        return "Faster Crystals";
    }

    @Override
    public String description() {
        return "Packet-driven crystal breaking and placement.";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    boolean isActive() {
        return active;
    }

    boolean isDebug() {
        return debug;
    }

    /** Verbose logging, enabled by the root {@code debug} config flag. */
    void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }

    void count(Outcome outcome) {
        outcomes[outcome.ordinal()].increment();
    }

    /**
     * Records an exception and logs only the first one. Repeated failures would otherwise
     * flood the console during fast crystalling; the counter still shows how many occurred.
     */
    void reportException(String where, Throwable throwable) {
        count(Outcome.EXCEPTION);
        if (exceptionLogged.compareAndSet(false, true) && plugin != null) {
            plugin.getLogger().log(Level.WARNING,
                    "Faster Crystals " + where + " failed; further exceptions are counted only",
                    throwable);
        }
    }

    /** Logs placement outcomes, skipping the summary when empty unless debugging. */
    void logSummary(String context) {
        if (plugin == null) {
            return;
        }
        long total = 0L;
        StringBuilder placementCounts = new StringBuilder();
        for (Outcome outcome : Outcome.values()) {
            long count = outcomes[outcome.ordinal()].sum();
            if (count == 0L) {
                continue;
            }
            total += count;
            if (placementCounts.length() > 0) {
                placementCounts.append(", ");
            }
            placementCounts.append(outcome).append('=').append(count);
        }
        if (total > 0L || debug) {
            plugin.getLogger().info("Faster Crystals outcomes " + context + ": "
                    + (placementCounts.length() == 0 ? "none" : placementCounts));
        }
    }

    private void resetCounters() {
        for (LongAdder counter : outcomes) {
            counter.reset();
        }
        exceptionLogged.set(false);
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

        var standalone = plugin.getServer().getPluginManager().getPlugin("FasterCrystals");
        if (standalone != null && standalone.isEnabled()) {
            plugin.getLogger().warning("The standalone FasterCrystals plugin is also enabled. "
                    + "Both plugins will handle the same packets, which can place or consume "
                    + "crystals twice; disable one before comparing behaviour.");
        }

        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);
        this.tracker = new CrystalTracker();
        this.states = new ConcurrentHashMap<>();
        active = true;
        resetCounters();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                states.put(player.getUniqueId(), new PlayerPacketState());
            }

            // EntityAddToWorldEvent only covers new entities, so pick up loaded crystals now.
            for (World world : Bukkit.getWorlds()) {
                for (EnderCrystal crystal : world.getEntitiesByClass(EnderCrystal.class)) {
                    tracker.add(crystal);
                }
            }

            int listenerCount = CrystalPacketSupport.start(plugin, this, tracker, states);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("Faster Crystals hooked into PacketEvents "
                    + PacketEventsGate.version() + ": " + listenerCount
                    + " packet listener(s) registered.");
        } catch (Throwable t) {
            active = false;
            CrystalPacketSupport.stop();
            HandlerList.unregisterAll(this);
            tracker.clear();
            states.clear();
            tracker = null;
            states = null;
            plugin.getLogger().log(Level.SEVERE, "Faster Crystals could not start", t);
            return false;
        }

        plugin.getLogger().info("Faster Crystals enabled: tracking " + tracker.size() + " crystal(s).");
        return true;
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }
        logSummary("at disable");
        active = false;
        HandlerList.unregisterAll(this);
        int removed = CrystalPacketSupport.stop();
        if (removed > 0) {
            plugin.getLogger().info("Faster Crystals detached from PacketEvents: "
                    + removed + " packet listener(s) unregistered.");
        }
        if (tracker != null) {
            tracker.clear();
        }
        if (states != null) {
            states.clear();
        }
        tracker = null;
        states = null;
    }

    @Override
    public String statusNote() {
        if (!isActive() && !PacketEventsGate.ready()) {
            return PacketEventsGate.unavailableDetail();
        }
        return null;
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("Faster Crystals debug logging " + (debug ? "enabled" : "disabled") + ".");
        }
        logSummary("after reload");
        if (debug && tracker != null && states != null) {
            debug("State: " + tracker.size() + " crystal(s) tracked, "
                    + states.size() + " player state(s).");
        }
    }

    // ------------------------------------------------------------------
    // Crystal tracking (main thread)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!active || tracker == null) {
            return;
        }
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        tracker.add(crystal);
        if (debug) {
            debug("tracking crystal id=" + crystal.getEntityId()
                    + " in " + crystal.getWorld().getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (!active || tracker == null) {
            return;
        }
        if (!(event.getEntity() instanceof EnderCrystal crystal)) {
            return;
        }
        int entityId = crystal.getEntityId();
        if (!tracker.contains(entityId)) {
            return;
        }
        if (debug) {
            debug("crystal id=" + entityId + " removed; keeping it tracked for "
                    + CrystalTracker.REMOVAL_DELAY_TICKS + " ticks");
        }
        // Keep it briefly: the follow-up placement packet may arrive after the destroy.
        // Expiry is lazy; no per-crystal scheduler task is needed.
        tracker.retain(entityId, CrystalTracker.REMOVAL_DELAY_NANOS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (active && tracker != null) {
            tracker.removeWorld(event.getWorld());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Do not use ignoreCancelled here: auth plugins may hold or alter the join flow, and
        // the packet listeners also create state lazily if this event is missed entirely.
        if (active && states != null) {
            states.put(event.getPlayer().getUniqueId(), new PlayerPacketState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (active && states != null) {
            states.remove(event.getPlayer().getUniqueId());
        }
    }
}