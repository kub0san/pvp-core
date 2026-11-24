package org.pvpcore.features.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.pvpcore.features.teleport.TeleportModule;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hands out landing spots. Each region keeps a small pool of spots that were found in the
 * background, so /rtp is normally instant; when the pool is empty the player's search runs
 * on demand. Main thread only, no locking.
 *
 * <p>This is also the seam for a future 1v1 queue: call {@link #acquire} for a region and use
 * the returned location.</p>
 */
public final class RtpService {

    private final JavaPlugin plugin;
    private final TeleportModule teleport;
    private final Map<String, ArrayDeque<RtpSpot>> pools = new HashMap<>();
    private final Map<String, Integer> pending = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();

    private RtpSettings settings;
    private RtpValidator validator;
    private RtpLocator locator;
    private BukkitTask refillTask;
    private int activeSearches;
    /** Bumped on every reload and shutdown; searches that started under an older value are ignored. */
    private int epoch;
    private final Map<String, String> lastDebugBounds = new HashMap<>();

    public RtpService(JavaPlugin plugin, TeleportModule teleport) {
        this.plugin = plugin;
        this.teleport = teleport;
    }

    /** Applies new settings. Clears the pools, because regions or safety rules may have changed. */
    void configure(RtpSettings newSettings, List<LocationVerifier> verifiers) {
        stopTask();
        epoch++;
        pools.clear();
        pending.clear();
        failures.clear();
        lastDebugBounds.clear();
        activeSearches = 0;

        settings = newSettings;
        validator = new RtpValidator(newSettings.unsafeBlocks(), newSettings.blockedBiomes(), verifiers);
        locator = new RtpLocator(plugin, newSettings, validator);

        for (RtpRegion region : newSettings.regions().values()) {
            World world = Bukkit.getWorld(region.worldName());
            if (world == null) {
                plugin.getLogger().warning("rtp: region '" + region.id() + "' targets unloaded world '" + region.worldName() + "'");
                continue;
            }
            RtpEffectiveBounds bounds = RtpEffectiveBounds.of(region, world, newSettings.borderMargin());
            lastDebugBounds.put(region.id(), bounds.summary());
            bounds.problemSummary().ifPresent(problem ->
                    plugin.getLogger().warning("rtp: region '" + region.id() + "': " + problem));
        }

        if (newSettings.enabled()) {
            long interval = newSettings.poolRefillIntervalSeconds() * 20L;
            refillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, interval);
        }
    }

    void shutdown() {
        stopTask();
        epoch++;
        pools.clear();
        pending.clear();
        failures.clear();
        activeSearches = 0;
    }

    private void stopTask() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    /**
     * Gets a landing location for {@code player} in {@code region}. The future completes on the
     * main thread, with null if no safe spot could be found. The location faces the way the
     * player is currently facing.
     */
    public CompletableFuture<Location> acquire(RtpRegion region, Player player) {
        Location pooled = takeFromPool(region);
        topUp(region);
        if (pooled != null) {
            return CompletableFuture.completedFuture(face(pooled, player));
        }

        player.sendMessage(teleport.messages().get("rtp-searching"));
        CompletableFuture<Location> out = new CompletableFuture<>();
        locator.find(region).whenComplete((spot, error) -> {
            Location location = error == null && spot != null ? spot.toLocation() : null;
            out.complete(location == null ? null : face(location, player));
        });
        return out;
    }

    /** How many ready spots the region's pool holds. */
    public int ready(String regionId) {
        ArrayDeque<RtpSpot> pool = pools.get(regionId);
        return pool == null ? 0 : pool.size();
    }

    private static Location face(Location location, Player player) {
        Location here = player.getLocation();
        location.setYaw(here.getYaw());
        location.setPitch(here.getPitch());
        return location;
    }

    private ArrayDeque<RtpSpot> pool(RtpRegion region) {
        return pools.computeIfAbsent(region.id(), id -> new ArrayDeque<>());
    }

    /** Takes the oldest usable spot, dropping expired or no-longer-safe ones. Null if none. */
    private Location takeFromPool(RtpRegion region) {
        ArrayDeque<RtpSpot> pool = pools.get(region.id());
        if (pool == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long maxAge = settings.poolMaxAgeSeconds() * 1000L;
        while (!pool.isEmpty()) {
            RtpSpot spot = pool.pollFirst();
            World world = spot.world();
            if (world == null || spot.isExpired(now, maxAge)) {
                continue;
            }
            // Blocks may have changed since the spot was found. Re-check only when the chunk is
            // already loaded; loading it here would put chunk I/O on the main thread.
            if (world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)
                    && !validator.isStandable(world, spot.x(), spot.y(), spot.z())) {
                continue;
            }
            return spot.toLocation();
        }
        return null;
    }

    /** Runs every refill interval: drops expired spots, then tops pools up if anyone is online. */
    private void tick() {
        if (!settings.enabled() || !teleport.settings().enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = settings.poolMaxAgeSeconds() * 1000L;
        boolean anyone = !Bukkit.getOnlinePlayers().isEmpty();
        for (RtpRegion region : settings.regions().values()) {
            ArrayDeque<RtpSpot> pool = pool(region);
            while (!pool.isEmpty() && pool.peekFirst().isExpired(now, maxAge)) {
                pool.pollFirst();
            }
            // Nobody online means nobody to use the pool; do not generate chunks for nothing.
            if (anyone) {
                topUp(region);
            }
        }
    }

    /** Starts background searches until the pool is full or the concurrency limit is reached. */
    private void topUp(RtpRegion region) {
        if (!settings.enabled() || !settings.poolEnabled() || settings.poolSize() <= 0
                || !teleport.settings().enabled()) {
            return;
        }
        if (Bukkit.getWorld(region.worldName()) == null) {
            return;
        }
        int missing = settings.poolSize() - pool(region).size() - pending.getOrDefault(region.id(), 0);
        while (missing-- > 0 && activeSearches < settings.maxConcurrentSearches()) {
            startRefill(region);
        }
    }

    private void startRefill(RtpRegion region) {
        final int startedEpoch = epoch;
        final String id = region.id();
        activeSearches++;
        pending.merge(id, 1, Integer::sum);
        locator.find(region).whenComplete((spot, error) -> {
            if (startedEpoch != epoch) {
                return; // reloaded or shut down meanwhile; the counters were already reset
            }
            activeSearches--;
            pending.merge(id, -1, Integer::sum);
            if (error != null || spot == null) {
                noteFailure(id);
                return;
            }
            failures.remove(id);
            pool(region).addLast(spot);
            topUp(region);
        });
    }

    /** Diagnostic lines describing effective RTP bounds and the latest search stats for admin inspection. */
    public List<String> debugReport(String regionId) {
        List<String> lines = new java.util.ArrayList<>();
        RtpSettings current = settings == null ? null : settings;
        if (current == null) {
            lines.add("RTP is not configured.");
            return lines;
        }
        for (RtpRegion region : current.regions().values()) {
            if (regionId != null && !region.id().equalsIgnoreCase(regionId)) {
                continue;
            }
            World world = Bukkit.getWorld(region.worldName());
            lines.add("region '" + region.id() + "' world=" + region.worldName()
                    + " loaded=" + (world != null));
            if (world != null) {
                RtpEffectiveBounds bounds = RtpEffectiveBounds.of(region, world, current.borderMargin());
                lines.add("  " + bounds.summary());
                bounds.problemSummary().ifPresent(problem -> lines.add("  WARNING: " + problem));
            } else {
                String cached = lastDebugBounds.get(region.id());
                if (cached != null) {
                    lines.add("  last-known bounds: " + cached);
                }
                lines.add("  WARNING: world not loaded; cannot compute border/effective range");
            }
            RtpSearchStats stats = locator == null ? null : locator.lastStats(region.id());
            if (stats != null) {
                lines.add("  last search: " + stats.summary());
            } else {
                lines.add("  last search: no search recorded yet");
            }
            lines.add("  pool ready=" + ready(region.id()) + " failures=" + failures.getOrDefault(region.id(), 0));
        }
        if (lines.isEmpty()) {
            lines.add("No region matches '" + regionId + "'.");
        }
        return lines;
    }

    private void noteFailure(String id) {
        int count = failures.merge(id, 1, Integer::sum);
        if (count == 1 || count % 10 == 0) {
            plugin.getLogger().warning("rtp: no safe location found for region '" + id + "' (" + count
                    + " failed background searches in a row). Check its radius, biomes and world border.");
        }
    }
}
