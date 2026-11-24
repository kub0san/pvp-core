package org.pvpcore.features.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Handles all server teleports with warmup, cooldown, and {@code /back} bookkeeping.
 *
 * <h2>Threading</h2>
 * Every public method dispatches to the main thread if it was called elsewhere. All
 * Bukkit API access, all state mutation, and every player message happens on the main
 * thread. Suppliers passed to {@link #begin(Player, Supplier)} are invoked on the main
 * thread and must be fast; do expensive work (chunk loads, async queries) before calling
 * {@code begin}.
 *
 * <h2>Lifecycle of a teleport</h2>
 * <ol>
 *   <li>Warmup check (existing warmup — reject).</li>
 *   <li>Cooldown check (unless the player has bypass or the teleport is forced).</li>
 *   <li>Warmup countdown if configured, cancelled by movement or damage.</li>
 *   <li>Destination resolved, current position saved to {@code /back}.</li>
 *   <li>{@link Player#teleportAsync(Location)} with a 30 second timeout.</li>
 *   <li>Cooldown set, success message sent with {@code x}, {@code y}, {@code z}, {@code world}.</li>
 * </ol>
 *
 * <h2>Placeholders</h2>
 * The success message is rendered with {@code <x>}, {@code <y>}, {@code <z>} and
 * {@code <world>} substituted from the destination. Messages that don't use those tokens
 * ignore the extra placeholders, so one code path works for {@code /tpa}, {@code /back},
 * {@code /spawn} and {@code /rtp}.
 */
public final class TeleportService {

    private static final String BYPASS_COOLDOWN = "pvpcore.teleportation.bypass.cooldown";
    private static final String BYPASS_WARMUP = "pvpcore.teleportation.bypass.warmup";
    private static final long TELEPORT_TIMEOUT_SECONDS = 30;

    private final JavaPlugin plugin;
    private final TeleportModule module;

    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();

    private record Warmup(BukkitTask task, World world, int x, int y, int z) {}

    /**
     * Immutable per-call settings. {@code -1} for warmup or cooldown means "use the
     * server-wide default from {@code teleportation/config.yml}".
     */
    public record Options(int warmupSeconds, int cooldownSeconds,
                          String failureKey, String successKey) {
        public Options {
            Objects.requireNonNull(failureKey, "failureKey");
            Objects.requireNonNull(successKey, "successKey");
        }
    }

    public TeleportService(JavaPlugin plugin, TeleportModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ================================================================
    // Public API
    // ================================================================

    /** Starts a teleport with the default warmup, cooldown and messages. */
    public void begin(Player player, Supplier<Location> destination) {
        begin(player, destination, defaultOptions());
    }

    /** Starts a teleport with custom warmup, cooldown and messages. */
    public void begin(Player player, Supplier<Location> destination, Options options) {
        onMain(() -> doBegin(player, destination, options));
    }

    /**
     * Teleports immediately, bypassing both warmup and cooldown. Used by {@code /tphere}
     * and other admin actions where the target has not initiated anything and should not
     * be penalised.
     */
    public void teleportNow(Player player, Location destination) {
        onMain(() -> doTeleportNow(player, destination, 0, "teleport-success"));
    }

    // ================================================================
    // Queries
    // ================================================================

    /** Seconds left on the player's cooldown, or 0. */
    public int getRemainingCooldown(Player player) {
        if (player.hasPermission(BYPASS_COOLDOWN)) return 0;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(id);
        if (until == null) return 0;
        if (until <= now) {
            cooldownUntil.remove(id);
            return 0;
        }
        return (int) Math.ceil((until - now) / 1000.0);
    }

    /** True while this player has a warmup running. Used by {@code /rtp} to avoid a wasted search. */
    public boolean hasPending(Player player) {
        return warmups.containsKey(player.getUniqueId());
    }

    public boolean hasWarmups() {
        return !warmups.isEmpty();
    }

    /** The player's last pre-teleport location, or {@code null} if none is saved or the world is gone. */
    public Location backLocation(Player player) {
        Location loc = back.get(player.getUniqueId());
        if (loc == null) return null;
        if (loc.getWorld() == null) {
            back.remove(player.getUniqueId());
            return null;
        }
        return loc;
    }

    public void recordBack(Player player, Location location) {
        if (location == null || location.getWorld() == null) return;
        back.put(player.getUniqueId(), location.clone());
    }

    // ================================================================
    // Warmup control (listeners)
    // ================================================================

    public void checkMoved(Player player, Location to) {
        Warmup warmup = warmups.get(player.getUniqueId());
        if (warmup == null) return;
        if (to.getWorld() != warmup.world()
                || to.getBlockX() != warmup.x()
                || to.getBlockY() != warmup.y()
                || to.getBlockZ() != warmup.z()) {
            cancelWarmup(player.getUniqueId(), "warmup-cancelled-move");
        }
    }

    public void cancelWarmup(UUID uuid, String messageKey) {
        Warmup warmup = warmups.remove(uuid);
        if (warmup == null) return;
        warmup.task().cancel();
        if (messageKey != null) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) player.sendMessage(module.messages().get(messageKey));
        }
    }

    // ================================================================
    // Cleanup
    // ================================================================

    public void forget(UUID uuid) {
        cancelWarmup(uuid, null);
        cooldownUntil.remove(uuid);
        back.remove(uuid);
    }

    public void shutdown() {
        for (Warmup w : warmups.values()) {
            w.task().cancel();
        }
        warmups.clear();
        cooldownUntil.clear();
        back.clear();
    }

    // ================================================================
    // Internals
    // ================================================================

    private Options defaultOptions() {
        return new Options(-1, -1, "teleport-failed", "teleport-success");
    }

    private void doBegin(Player player, Supplier<Location> destination, Options options) {
        if (!player.isOnline()) return;

        // 1. Warmup gate
        if (warmups.containsKey(player.getUniqueId())) {
            player.sendMessage(module.messages().get("teleport-pending"));
            return;
        }

        // 2. Cooldown gate
        int remainingCooldown = getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            player.sendMessage(module.messages().get("cooldown-active", "seconds", String.valueOf(remainingCooldown)));
            return;
        }

        int warmupSeconds = resolveWarmup(options, player);
        int cooldownSeconds = resolveCooldown(options);

        if (warmupSeconds <= 0) {
            doResolveAndTeleport(player, destination, cooldownSeconds,
                    options.successKey(), options.failureKey());
            return;
        }

        // 3. Warmup
        player.sendMessage(module.messages().get("warmup-started", "seconds", String.valueOf(warmupSeconds)));
        Location startLoc = player.getLocation();
        UUID uuid = player.getUniqueId();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Remove first so an offline player cannot leak the entry.
            Warmup current = warmups.remove(uuid);
            if (current == null || !player.isOnline()) return;
            doResolveAndTeleport(player, destination, cooldownSeconds,
                    options.successKey(), options.failureKey());
        }, warmupSeconds * 20L);

        warmups.put(uuid, new Warmup(task,
                startLoc.getWorld(), startLoc.getBlockX(), startLoc.getBlockY(), startLoc.getBlockZ()));
    }

    private int resolveWarmup(Options options, Player player) {
        if (player.hasPermission(BYPASS_WARMUP)) return 0;
        int requested = options.warmupSeconds() < 0
                ? module.settings().warmupSeconds() : options.warmupSeconds();
        return Math.max(0, requested);
    }

    private int resolveCooldown(Options options) {
        int requested = options.cooldownSeconds() < 0
                ? module.settings().cooldownSeconds() : options.cooldownSeconds();
        return Math.max(0, requested);
    }

    /**
     * Calls the supplier on the main thread. Any exception is logged and reported to the
     * player as a failure rather than swallowed silently.
     */
    private void doResolveAndTeleport(Player player, Supplier<Location> destination,
                                      int cooldownSeconds, String successKey, String failureKey) {
        Location destinationLoc;
        try {
            destinationLoc = destination.get();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Teleport destination supplier threw for " + player.getName(), e);
            player.sendMessage(module.messages().get(failureKey));
            return;
        }
        if (destinationLoc == null || destinationLoc.getWorld() == null) {
            player.sendMessage(module.messages().get(failureKey));
            return;
        }
        doTeleportNow(player, destinationLoc, cooldownSeconds, successKey);
    }

    private void doTeleportNow(Player player, Location destination,
                               int cooldownSeconds, String successKey) {
        if (destination == null || destination.getWorld() == null) {
            player.sendMessage(module.messages().get("teleport-failed"));
            return;
        }

        recordBack(player, player.getLocation());

        UUID uuid = player.getUniqueId();
        String worldName = destination.getWorld().getName();
        String x = String.valueOf(destination.getBlockX());
        String y = String.valueOf(destination.getBlockY());
        String z = String.valueOf(destination.getBlockZ());

        player.teleportAsync(destination)
                .orTimeout(TELEPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((success, throwable) -> {
                    // whenComplete runs on whichever thread completed the future.
                    // Dispatch back to main so all messages and state writes are safe.
                    onMain(() -> handleResult(player, uuid, success, throwable,
                            cooldownSeconds, successKey, x, y, z, worldName));
                });
    }

    private void handleResult(Player player, UUID uuid, Boolean success, Throwable throwable,
                              int cooldownSeconds, String successKey,
                              String x, String y, String z, String worldName) {
        if (!player.isOnline()) return;

        if (throwable != null) {
            Throwable cause = throwable;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof TimeoutException) {
                plugin.getLogger().warning("Teleport of " + player.getName()
                        + " timed out after " + TELEPORT_TIMEOUT_SECONDS + "s");
            } else {
                plugin.getLogger().log(Level.WARNING,
                        "Teleport of " + player.getName() + " failed", cause);
            }
            player.sendMessage(module.messages().get("teleport-failed"));
            return;
        }

        if (Boolean.TRUE.equals(success)) {
            if (cooldownSeconds > 0) {
                cooldownUntil.put(uuid, System.currentTimeMillis() + cooldownSeconds * 1000L);
            }
            // The placeholders are ignored by templates that do not use them,
            // so /tpa, /back and /spawn see no difference.
            player.sendMessage(module.messages().get(successKey,
                    "x", x, "y", y, "z", z, "world", worldName));
        } else {
            player.sendMessage(module.messages().get("teleport-failed"));
        }
    }

    private void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }
}