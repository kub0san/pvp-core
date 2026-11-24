package org.pvpcore.mechanic.shieldfixes;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.pvpcore.PvPCorePlugin;
import org.pvpcore.config.core.CoreConfig;
import org.pvpcore.mechanic.Mechanic;
import org.pvpcore.mechanic.PacketEventsGate;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Shield Fixes: repairs two vanilla/Paper shield bugs.
 *
 * <ul>
 *   <li><a href="https://bugs.mojang.com/browse/MC-105068">MC-105068</a>: a blocked
 *       hit still sends the player-hurt sound. The mechanic mutes the buggy hurt
 *       sound and Paper's own block sound, then plays exactly one
 *       {@code item.shield.block} itself. This gives a single, consistent block
 *       sound on every blocked hit, regardless of whether ShieldStun's Paper setting
 *       is on.</li>
 *   <li><a href="https://bugs.mojang.com/browse/MC-238293">MC-238293</a>: other
 *       players can miss a shield raise or release. Outgoing hand-state metadata is
 *       cached per viewer, and the real server state is re-sent after use/release.</li>
 * </ul>
 *
 * <p>ShieldFixes is the single source of truth for the blocked-hit sound. It works
 * whether ShieldStun is on (Paper skips the block sound, ShieldFixes provides it) or
 * off (Paper sends both the correct block sound and the buggy hurt sound; ShieldFixes
 * mutes both and provides the single block sound).</p>
 *
 * <p>Requires PacketEvents 2.14.0 or newer. PacketEvents is a soft dependency:
 * without it this mechanic refuses to enable and every other mechanic keeps
 * working. All PacketEvents types are confined to {@link ShieldPacketSupport}.</p>
 */
public final class ShieldFixesMechanic implements Mechanic, Listener {

    private PvPCorePlugin plugin;
    private volatile boolean active;
    private volatile boolean debug;
    private volatile ShieldState state;

    @Override
    public String id() {
        return "shieldfixes";
    }

    @Override
    public String displayName() {
        return "Shield Fixes";
    }

    @Override
    public String description() {
        return "Fixes shield hit sounds and blocking animation desync.";
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

    @Override
    public boolean enable(PvPCorePlugin plugin) {
        if (active) {
            return true;
        }

        this.plugin = plugin;
        this.debug = CoreConfig.isDebug(plugin);
        ShieldState newState = new ShieldState();
        for (Player player : Bukkit.getOnlinePlayers()) {
            newState.addPlayer(player);
        }

        try {
            int listenerCount = ShieldPacketSupport.start(plugin, this, newState);
            this.state = newState;
            active = true;
            Bukkit.getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("Shield Fixes hooked into PacketEvents "
                    + PacketEventsGate.version() + ": "
                    + listenerCount + " packet listener(s) registered.");
        } catch (Throwable t) {
            active = false;
            ShieldPacketSupport.stop();
            HandlerList.unregisterAll(this);
            newState.clear();
            state = null;
            plugin.getLogger().log(Level.SEVERE, "Shield Fixes could not start", t);
            return false;
        }

        plugin.getLogger().info("Shield Fixes enabled: " + newState.trackedPlayers()
                + " online player(s), hand-state metadata index "
                + ShieldState.HAND_STATE_INDEX + ".");
        return true;
    }

    @Override
    public void disable(PvPCorePlugin plugin) {
        if (!active) {
            return;
        }
        active = false;
        HandlerList.unregisterAll(this);
        int removed = ShieldPacketSupport.stop();
        if (removed > 0) {
            plugin.getLogger().info("Shield Fixes detached from PacketEvents: "
                    + removed + " packet listener(s) unregistered.");
        }
        ShieldState oldState = state;
        state = null;
        if (oldState != null) {
            oldState.clear();
        }
    }

    @Override
    public boolean requiresPacketEvents() {
        return true;
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
            plugin.getLogger().info("Shield Fixes debug logging "
                    + (debug ? "enabled" : "disabled") + ".");
        }
        ShieldState current = state;
        if (debug && current != null) {
            debug("State: " + current.trackedPlayers() + " player(s), "
                    + current.cachedEntities() + " hand-state entit(ies).");
        }
    }

    // ------------------------------------------------------------------
    // Blocked-hit sound (main thread)
    // ------------------------------------------------------------------

    /**
     * Marks the victim's next player-hurt and block sounds as blocked-hit audio, then
     * plays ShieldFixes' own {@code item.shield.block} sound once.
     *
     * <p>The {@link ShieldPacketListener} mutes both Paper's hurt sound (MC-105068) and
     * Paper's block sound at the blocked-hit position. ShieldFixes plays its own sound
     * at a slightly raised position so the listener's quantized-position check does not
     * match it. This gives exactly one audible block sound per blocked hit, whether or
     * not ShieldStun's Paper setting is on.</p>
     */
    @SuppressWarnings("deprecation") // BLOCKING is still the only Bukkit signal for a reduced hit.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        ShieldState current = state;
        if (!active || current == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim) || !victim.isBlocking()) {
            return;
        }
        double blocking;
        try {
            blocking = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);
        } catch (IllegalArgumentException ignored) {
            // The modifier is absent when the shield did not block this damage.
            return;
        }
        if (blocking >= 0.0D) {
            return;
        }

        current.markBlocked(victim, System.nanoTime());

        // Play ShieldFixes' own block sound at a position the packet listener's
        // quantized check does not match, so it is not muted. Vanilla pitch range.
        float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F;
        Location soundLocation = victim.getLocation().add(0, 0.25, 0);
        victim.getWorld().playSound(soundLocation, Sound.ITEM_SHIELD_BLOCK, 1.0F, pitch);

        if (debug) {
            debug("Blocked hit for " + victim.getName()
                    + " at " + quantized(victim.getX()) + "," + quantized(victim.getY())
                    + "," + quantized(victim.getZ()) + " (blocking=" + blocking
                    + ", played shield-block sound).");
        }
    }

    // ------------------------------------------------------------------
    // Hand-state resync (main thread)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        ShieldState current = state;
        if (!active || current == null) {
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
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.SHIELD || player.hasCooldown(item)) {
            return;
        }
        if (debug) {
            debug("Shield use by " + player.getName() + " in " + event.getHand() + ".");
        }
        // Let the server process the use first. The reference implementation treats
        // two ticks as the earliest safe point for the forced resync.
        scheduleResync(player, 2L, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ShieldState current = state;
        if (active && current != null) {
            current.addPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        ShieldState current = state;
        if (active && current != null) {
            current.removePlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        ShieldState current = state;
        if (!active || current == null) {
            return;
        }
        Player player = event.getPlayer();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ShieldState latest = state;
                if (active && latest != null && player.isOnline()) {
                    latest.addPlayer(player);
                }
            });
        } catch (IllegalStateException ignored) {
            // The plugin is shutting down; the next join/create will re-register.
        }
    }

    /**
     * Schedules one coalesced resync. An existing task is upgraded to a forced
     * resync when a shield use arrives before it runs.
     */
    void scheduleResync(Player player, long delayTicks, boolean force) {
        ShieldState current = state;
        if (!active || current == null || player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!current.beginResync(uuid, force)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> runResync(uuid, current), Math.max(0L, delayTicks));
        } catch (IllegalStateException ignored) {
            current.endResync(uuid);
        }
    }

    private void runResync(UUID uuid, ShieldState scheduledState) {
        if (!active || state != scheduledState) {
            scheduledState.endResync(uuid);
            return;
        }
        boolean force = scheduledState.endResync(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }
        scheduledState.addPlayer(player);

        byte handBits = 0;
        if (player.isHandRaised()) {
            EquipmentSlot hand = player.getHandRaised();
            handBits = hand == EquipmentSlot.OFF_HAND
                    ? ShieldState.HAND_USING_OFF
                    : ShieldState.HAND_USING_MAIN;
        }
        int sent = ShieldPacketSupport.sendHandState(player, scheduledState, handBits, force, this);
        if (debug) {
            debug("Hand-state resync for " + player.getName() + ": "
                    + describe(handBits) + ", " + sent + " viewer(s)"
                    + (force ? " (forced)." : "."));
        }
    }

    private static String describe(byte handBits) {
        if ((handBits & ShieldState.HAND_MASK) == ShieldState.HAND_USING_OFF) {
            return "off hand raised";
        }
        if ((handBits & ShieldState.HAND_MASK) == ShieldState.HAND_USING_MAIN) {
            return "main hand raised";
        }
        return "hand released";
    }

    private static int quantized(double coordinate) {
        return (int) (coordinate * 8.0D);
    }
}