package org.pvpcore.mechanic.shieldsync;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Shield Sync: ping-compensated shield raise timing.
 *
 * <p>Vanilla starts counting the shield's five-tick block delay when the server
 * receives the use packet. A high-ping player therefore starts blocking later than a
 * low-ping player. This mechanic shortens the server-side delay by the player's
 * one-way latency so the delay feels closer to the vanilla 250 ms. This is
 * intentionally non-vanilla lag compensation.</p>
 *
 * <p><strong>Abuse protection (implemented).</strong> Compensation is capped at
 * {@link #MAX_COMPENSATION_TICKS} ticks, and the PacketEvents probe median is
 * cross-checked against Bukkit's keep-alive ping; a median that exceeds
 * {@code bukkitPing * 2 + 100 ms} is rejected in favour of Bukkit ping. A
 * {@link #PING_OFFSET_MS} safety margin is subtracted before tick conversion so the
 * mechanic never fully compensates. Together these bound what a client can gain by
 * delaying its pong replies.</p>
 *
 * <p><strong>Paper 26.3 implementation note.</strong> The plan originally used
 * {@link LivingEntity#setShieldBlockingDelay(int)} ahead of time. On Paper 26.3 build
 * 209 that API is a no-op default: {@code getShieldBlockingDelay()} returns 5 and the
 * setter returns immediately, and the CraftBukkit entity classes do not override it.
 * Shield delay is now data-driven through the shield's {@code blocks_attacks} item
 * component. The mechanic deliberately does not edit item components, because that
 * would change the item itself and could be visible or persistent.</p>
 *
 * <p>Instead, when a player raises a shield, one coalesced next-tick task subtracts the
 * compensation from the active item's remaining use time with
 * {@link LivingEntity#setActiveItemRemainingTime(int)}. Paper's
 * {@code getItemBlockingWith()} compares {@code useDuration - useItemRemaining} against
 * the shield component's block delay, so this makes blocking start earlier by the same
 * number of ticks. The shield item and its components are never touched. The adjustment
 * is transient and is added back on disable/quit when the same use is still active.</p>
 *
 * <p>PacketEvents is optional. With it, PING/PONG probes provide a precise median RTT;
 * without it, the mechanic falls back to {@link Player#getPing()} and still works, just
 * less precisely. No packet is cancelled or rewritten and PacketEvents' lifecycle is
 * never touched.</p>
 */
public final class ShieldSyncMechanic implements Mechanic, Listener {

    /**
     * Compensate by one-way latency. The server starts counting when the use packet
     * arrives, about RTT / 2 after the player pressed. Set this to false to reproduce
     * the reference plugin's full-RTT behaviour while A/B testing.
     */
    private static final boolean USE_HALF_RTT = true;

    /**
     * Maximum compensation in ticks. 4 ticks = 200 ms one-way = 400 ms RTT.
     * Mirrors KnockbackSync's safety intent: compensate meaningfully, never absurdly.
     * Without a cap a client can delay pong replies to inflate its measured latency
     * and receive an arbitrarily large compensation.
     */
    private static final int MAX_COMPENSATION_TICKS = 4;

    /**
     * Reject the probe median when it exceeds Bukkit ping by this factor.
     * Bukkit's ping comes from keep-alive RTT, which a normal client does not delay;
     * a cheater pong-delayer only inflates the probe path.
     */
    private static final long CROSS_CHECK_FACTOR = 2L;

    /**
     * Plus a fixed slack (ms) so tiny-sample noise does not trip the check.
     */
    private static final long CROSS_CHECK_SLACK_MS = 100L;

    /**
     * Safety margin before tick conversion, matching KnockbackSync's default
     * {@code PING_OFFSET = 25}. Subtracting a small margin prevents
     * over-compensation on shields: the server should never think you raised the
     * shield before the client did.
     */
    private static final long PING_OFFSET_MS = 25L;

    private static final long PRECISE_SAMPLE_MAX_AGE_NANOS = 10_000_000_000L;

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;
    private volatile boolean precise;
    private final Map<UUID, ShieldSyncState> states = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "shieldsync";
    }

    @Override
    public String displayName() {
        return "Shield Sync";
    }

    @Override
    public String description() {
        return "Ping-compensated shield raise delay.";
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
        this.precise = false;
        this.active = false;

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);

            boolean packetEventsReady = PacketEventsGate.ready();
            if (packetEventsReady) {
                try {
                    precise = ShieldSyncPackets.start(plugin, this);
                } catch (Throwable t) {
                    precise = false;
                    plugin.getLogger().log(Level.WARNING,
                            "Shield Sync: PacketEvents probe setup failed; using Bukkit ping.", t);
                }
            }

            for (Player player : Bukkit.getOnlinePlayers()) {
                track(player);
            }

            active = true;

            if (precise) {
                plugin.getLogger().info("Shield Sync enabled: PacketEvents "
                        + PacketEventsGate.version() + " ping probes active.");
            } else {
                plugin.getLogger().info("Shield Sync enabled: "
                        + (packetEventsReady
                        ? "PacketEvents probe setup unavailable"
                        : "PacketEvents unavailable")
                        + "; using Bukkit ping (less precise).");
            }
            logShieldDelayApiStatus(plugin);
            return true;
        } catch (Throwable t) {
            rollback();
            plugin.getLogger().log(Level.SEVERE, "Shield Sync could not start", t);
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
        if (precise) {
            ShieldSyncPackets.stop();
            precise = false;
        }

        int restored = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ShieldSyncState state = states.get(player.getUniqueId());
            if (state != null && restoreActiveUse(player, state)) {
                restored++;
            }
        }
        states.clear();

        plugin.getLogger().info("Shield Sync disabled"
                + (restored > 0 ? "; restored " + restored + " active shield use(s)." : "."));
    }

    @Override
    public String statusNote() {
        if (!active || precise) {
            return null;
        }
        return PacketEventsGate.ready()
                ? "PacketEvents probe setup failed; using Bukkit ping"
                : "using Bukkit ping; install PacketEvents for accuracy";
    }

    @Override
    public void refresh(PvPCorePlugin plugin) {
        boolean wasDebug = debug;
        debug = CoreConfig.isDebug(plugin);
        if (wasDebug != debug) {
            plugin.getLogger().info("Shield Sync debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }
        if (debug) {
            debug("State: " + states.size() + " player(s), "
                    + (precise ? "PacketEvents probes" : "Bukkit ping") + ".");
            logShieldDelayApiStatus(plugin);
        }
    }

    // ------------------------------------------------------------------
    // Player tracking
    // ------------------------------------------------------------------

    void track(Player player) {
        if (player == null) {
            return;
        }

        ShieldSyncState state = states.computeIfAbsent(
                player.getUniqueId(), ignored -> new ShieldSyncState());

        if (precise && !ShieldSyncPackets.track(player)) {
            try {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (active && precise && player.isOnline()) {
                        ShieldSyncPackets.track(player);
                    }
                }, 20L);
            } catch (IllegalStateException ignored) {
                // Plugin is shutting down.
            }
        }
        if (debug) {
            debug("Tracking " + player.getName() + " (Bukkit ping "
                    + Math.max(0, player.getPing()) + " ms).");
        }
    }

    /** Called from the PacketEvents network thread when a new median is available. */
    void onMedian(UUID playerId, long medianMs) {
        ShieldSyncState state = states.get(playerId);
        if (state != null) {
            state.medianPingMs = medianMs;
            state.preciseSampleNanos = System.nanoTime();
        }
    }

    /** Package-private access for the PacketEvents probe class. */
    ShieldSyncState state(UUID playerId) {
        return states.get(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (active) {
            track(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!active) {
            return;
        }
        Player player = event.getPlayer();
        ShieldSyncState state = states.get(player.getUniqueId());
        if (state != null) {
            restoreActiveUse(player, state);
            states.remove(player.getUniqueId(), state);
        }
        if (precise) {
            ShieldSyncPackets.untrack(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        ShieldSyncState state = states.get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.activeUseAdjusted = false;
            state.activeUseCompensation = 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        if (active && !precise && PacketEventsGate.ready()) {
            try {
                precise = ShieldSyncPackets.start(plugin, this);
                if (precise) {
                    plugin.getLogger().info("Shield Sync: PacketEvents probes activated after server load.");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, 
                        "Shield Sync: PacketEvents probe setup failed on late load.", t);
            }
        }
    }

    // ------------------------------------------------------------------
    // Per-use compensation
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!active) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (!isShield(event.getItem())
                && !isShield(player.getInventory().getItemInMainHand())
                && !isShield(player.getInventory().getItemInOffHand())) {
            return;
        }

        ShieldSyncState state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        if (player.hasActiveItem() && !isShield(player.getActiveItem())) {
            return;
        }

        if (!player.hasActiveItem()) {
            state.activeUseAdjusted = false;
            state.activeUseCompensation = 0;
        } else if (state.activeUseAdjusted) {
            return;
        }

        if (!state.adjustmentScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin,
                    () -> applyUseAdjustment(player.getUniqueId(), state));
        } catch (IllegalStateException ignored) {
            state.adjustmentScheduled.set(false);
        }
    }

    private void applyUseAdjustment(UUID playerId, ShieldSyncState scheduledState) {
        scheduledState.adjustmentScheduled.set(false);

        if (!active || states.get(playerId) != scheduledState) {
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!player.hasActiveItem()) {
            return;
        }

        ItemStack activeItem = player.getActiveItem();
        if (activeItem == null || activeItem.getType() != Material.SHIELD) {
            return;
        }
        if (scheduledState.activeUseAdjusted) {
            return;
        }

        long pingMs = effectivePing(player, scheduledState);
        int compensation = compensationTicks(pingMs);
        if (compensation <= 0) {
            scheduledState.activeUseCompensation = 0;
            if (debug) {
                debug(player.getName() + " ping=" + pingMs
                        + " ms; no shield compensation.");
            }
            return;
        }

        int remaining = player.getActiveItemRemainingTime();
        if (remaining <= compensation) {
            return;
        }

        int adjusted = remaining - compensation;
        try {
            player.setActiveItemRemainingTime(adjusted);
            scheduledState.activeUseCompensation = compensation;
            scheduledState.activeUseAdjusted = true;
            if (debug) {
                debug(player.getName() + " ping=" + pingMs
                        + " ms -> " + compensation + " tick(s) of shield compensation"
                        + " (remaining " + remaining + " -> " + adjusted
                        + ", " + player.getActiveItemHand() + ").");
            }
        } catch (Throwable t) {
            if (debug) {
                plugin.getLogger().log(Level.WARNING,
                        "Shield Sync: could not adjust " + player.getName()
                                + "'s active shield use.", t);
            }
        }
    }

    /**
     * Returns the ping used for the next shield raise. The precise median is preferred
     * while fresh, but only if it passes the cross-check against Bukkit ping; otherwise
     * Bukkit's current value is used. A {@link #PING_OFFSET_MS} safety margin is
     * subtracted before the caller converts to ticks.
     */
    private long effectivePing(Player player, ShieldSyncState state) {
        long now = System.nanoTime();
        long bukkitPing = Math.max(0, player.getPing());
        long ping;

        if (precise && state.medianPingMs >= 0L
                && now - state.preciseSampleNanos <= PRECISE_SAMPLE_MAX_AGE_NANOS) {
            long probe = state.medianPingMs;
            if (probe > bukkitPing * CROSS_CHECK_FACTOR + CROSS_CHECK_SLACK_MS) {
                if (debug) {
                    debug(player.getName() + " probe median " + probe
                            + " ms rejected (Bukkit ping " + bukkitPing
                            + " ms); using Bukkit ping.");
                }
                ping = bukkitPing;
            } else {
                ping = probe;
            }
        } else {
            ping = bukkitPing;
        }

        return Math.max(0L, ping - PING_OFFSET_MS);
    }

    /**
     * Converts a ping in milliseconds to whole ticks of compensation.
     *
     * <p>With {@link #USE_HALF_RTT}, a 100 ms RTT is zero ticks and 150 ms is one tick
     * after the {@link #PING_OFFSET_MS} margin. Compensation is capped at
     * {@link #MAX_COMPENSATION_TICKS}, so a 400 ms RTT player gets 4 ticks (the
     * maximum) rather than 8.</p>
     */
    static int compensationTicks(long pingMs) {
        double effective = USE_HALF_RTT
                ? Math.max(0L, pingMs) / 2.0
                : Math.max(0L, pingMs);
        long ticks = Math.round(effective / 50.0);
        return (int) Math.min(MAX_COMPENSATION_TICKS, ticks);
    }

    private static boolean isShield(ItemStack item) {
        return item != null && item.getType() == Material.SHIELD;
    }

    private boolean restoreActiveUse(Player player, ShieldSyncState state) {
        int compensation = state.activeUseCompensation;
        boolean adjusted = state.activeUseAdjusted;
        state.activeUseAdjusted = false;
        state.activeUseCompensation = 0;

        if (!adjusted || compensation <= 0 || player == null || !player.isOnline()
                || !player.hasActiveItem()) {
            return false;
        }

        ItemStack activeItem = player.getActiveItem();
        if (activeItem == null || activeItem.getType() != Material.SHIELD) {
            return false;
        }

        try {
            int remaining = player.getActiveItemRemainingTime();
            if (remaining < 0) {
                return false;
            }
            player.setActiveItemRemainingTime(remaining + compensation);
            if (debug) {
                debug("Restored " + compensation + " tick(s) for "
                        + player.getName() + "'s active shield use.");
            }
            return true;
        } catch (Throwable t) {
            if (debug) {
                plugin.getLogger().log(Level.WARNING,
                        "Shield Sync: could not restore " + player.getName()
                                + "'s active shield use.", t);
            }
            return false;
        }
    }

    private void rollback() {
        active = false;
        try {
            HandlerList.unregisterAll(this);
        } catch (Throwable ignored) {
            // The listener may not have registered yet.
        }
        if (precise) {
            try {
                ShieldSyncPackets.stop();
            } catch (Throwable ignored) {
                // PacketEvents may already be shutting down.
            }
            precise = false;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ShieldSyncState state = states.get(player.getUniqueId());
            if (state != null) {
                restoreActiveUse(player, state);
            }
        }
        states.clear();
    }

    private static String nativeShieldDelayImplementation() {
        String[] candidates = {
                "org.bukkit.craftbukkit.entity.CraftPlayer",
                "org.bukkit.craftbukkit.entity.CraftLivingEntity"
        };
        for (String className : candidates) {
            try {
                Class<?> type = Class.forName(className);
                Method method = type.getMethod("setShieldBlockingDelay", int.class);
                Class<?> declaringClass = method.getDeclaringClass();
                if (declaringClass != LivingEntity.class) {
                    return declaringClass.getName();
                }
            } catch (Throwable ignored) {
                // Try the next implementation class.
            }
        }
        return null;
    }

    private void logShieldDelayApiStatus(PvPCorePlugin plugin) {
        String implementation = nativeShieldDelayImplementation();
        if (implementation == null) {
            plugin.getLogger().info("Shield Sync: LivingEntity#setShieldBlockingDelay "
                    + "is still the no-op default on this Paper build; using "
                    + "setActiveItemRemainingTime per shield use.");
        } else {
            plugin.getLogger().info("Shield Sync: setShieldBlockingDelay is implemented by "
                    + implementation + "; the native ahead-of-time delay path should be "
                    + "reviewed. Still using setActiveItemRemainingTime for now.");
        }
    }

    private void debug(String message) {
        if (debug && plugin != null) {
            plugin.getLogger().info("[debug] " + message);
        }
    }
}