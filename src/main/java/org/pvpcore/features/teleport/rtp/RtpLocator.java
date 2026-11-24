package org.pvpcore.features.teleport.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Finds one safe landing spot for a region.
 *
 * <p>How a search works: derive the <em>effective</em> bounds (requested radii intersected with
 * the world border), sample candidates uniformly from the valid shape/annulus, request their
 * chunk asynchronously, then validate a few columns of that chunk on the main thread. Chunks and
 * coarse cells that produced nothing are remembered with a TTL so repeated tries skip them.</p>
 *
 * <p>Every step runs on the main thread, so there is no locking, and the returned future is always
 * completed on the main thread. Invalid candidates and failed chunks are counted when debug mode is
 * enabled in {@code search.debug}.</p>
 */
final class RtpLocator {

    private static final int BAD_CHUNK_CAP = 8192;
    private static final long BAD_CHUNK_TTL_MILLIS = 60L * 60L * 1000L;
    private static final int MAX_COLUMN_TRIES = 48;
    private static final int MAX_BAD_SKIPS = 128;

    private final JavaPlugin plugin;
    private final RtpSettings settings;
    private final RtpValidator validator;

    private final Map<String, Map<Long, Long>> badChunks = new HashMap<>(); // region id -> chunkKey -> expiry
    private final Map<String, RtpSearchStats> lastStats = new ConcurrentHashMap<>();

    RtpLocator(JavaPlugin plugin, RtpSettings settings, RtpValidator validator) {
        this.plugin = plugin;
        this.settings = settings;
        this.validator = validator;
    }

    /** Starts a search. Main thread only. */
    CompletableFuture<RtpSpot> find(RtpRegion region) {
        World world = Bukkit.getWorld(region.worldName());
        if (world == null) {
            plugin.getLogger().warning("rtp: region '" + region.id() + "' targets world '" + region.worldName() + "' which is not loaded");
            noteStats(region.id(), null, "world-not-loaded");
            return CompletableFuture.completedFuture(null);
        }
        Search search = new Search(region, world);
        search.start();
        return search.result;
    }

    /** Diagnostic summary of the most recent search for a region, or null if none recorded. */
    RtpSearchStats lastStats(String regionId) {
        return regionId == null ? null : lastStats.get(regionId);
    }

    private void noteStats(String regionId, RtpSearchStats stats, String forcedFailure) {
        RtpSearchStats copy = new RtpSearchStats();
        if (stats != null) {
            for (int i = 0; i < stats.candidates(); i++) {
                copy.countCandidate();
            }
            for (int i = 0; i < stats.chunksLoaded(); i++) {
                copy.countChunkLoaded();
            }
            for (int i = 0; i < stats.chunkLoadErrors(); i++) {
                copy.countChunkLoadError();
            }
            stats.rejects().forEach((key, value) -> {
                for (int i = 0; i < value; i++) {
                    copy.countReject(key);
                }
            });
        } else if (forcedFailure != null) {
            copy.countReject(RtpSearchStats.Reject.TIMEOUT);
        }
        lastStats.put(regionId, copy);
    }

    /** Runs {@code task} on the main thread, now if already there. */
    private void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (IllegalPluginAccessException ignored) {
            // The plugin is shutting down; the search no longer matters.
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static boolean isBadChunkFresh(Map<Long, Long> regionBad, long key, long now) {
        Long expires = regionBad.get(key);
        if (expires == null) {
            return false;
        }
        if (now >= expires) {
            regionBad.remove(key);
            return false;
        }
        return true;
    }

    private void rememberBadChunk(String regionId, long key, long now) {
        Map<Long, Long> regionBad = badChunks.computeIfAbsent(regionId, id -> new HashMap<>());
        // Prune expired entries occasionally to keep memory flat.
        if (regionBad.size() >= BAD_CHUNK_CAP) {
            Iterator<Map.Entry<Long, Long>> iterator = regionBad.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now >= iterator.next().getValue()) {
                    iterator.remove();
                }
            }
        }
        if (regionBad.size() >= BAD_CHUNK_CAP) {
            // Still full of fresh negatives: drop the oldest entries rather than stop tracking.
            List<Map.Entry<Long, Long>> entries = new ArrayList<>(regionBad.entrySet());
            entries.sort(Map.Entry.comparingByValue());
            for (int i = 0; i < entries.size() / 4 + 1; i++) {
                regionBad.remove(entries.get(i).getKey());
            }
        }
        regionBad.put(key, now + BAD_CHUNK_TTL_MILLIS);
    }

    private final class Search {

        final CompletableFuture<RtpSpot> result = new CompletableFuture<>();

        private final RtpRegion region;
        private final World world;
        private final RtpEffectiveBounds bounds;
        private final Map<Long, Long> bad;
        private final RtpSearchStats stats = new RtpSearchStats();
        private int attemptsLeft;

        Search(RtpRegion region, World world) {
            this.region = region;
            this.world = world;
            this.bounds = RtpEffectiveBounds.of(region, world, settings.borderMargin());
            this.bad = badChunks.computeIfAbsent(region.id(), id -> new HashMap<>());
            this.attemptsLeft = settings.maxAttempts();
        }

        void start() {
            bounds.problemSummary().ifPresent(problem ->
                    plugin.getLogger().warning("rtp: region '" + region.id() + "' cannot search: " + problem));
            if (bounds.effectiveMax() <= 0) {
                stats.countReject(RtpSearchStats.Reject.OUT_OF_BOUNDS);
                finish();
                // Terminal early return: no timeout is scheduled yet, so the future would
                // never complete and callers (and RtpService search counters) would hang.
                result.complete(null);
                return;
            }
            if (settings.debug()) {
                plugin.getLogger().info(String.format(java.util.Locale.ROOT,
                        "rtp-debug: region=%s world=%s shape=%s centre=(%.0f,%.0f from %s) requested=%d-%d effective=%d-%d border(centre=%.0f,%.0f half=%.0f margin=%d)",
                        region.id(), region.worldName(), region.shape(), bounds.centerX(), bounds.centerZ(), bounds.centerSource(),
                        bounds.requestedMin(), bounds.requestedMax(), bounds.effectiveMin(), bounds.effectiveMax(),
                        bounds.borderX(), bounds.borderZ(), bounds.borderHalf(), settings.borderMargin()));
            }
            BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> {
                        if (!result.isDone()) {
                            stats.countReject(RtpSearchStats.Reject.TIMEOUT);
                            result.complete(null);
                        }
                    }, settings.searchTimeoutSeconds() * 20L);
            result.whenComplete((spot, error) -> {
                timeout.cancel();
                finish();
            });
            next();
        }

        private void finish() {
            noteStats(region.id(), stats, null);
            if (settings.debug()) {
                plugin.getLogger().info("rtp-debug: region=" + region.id() + " stats: " + stats.summary());
            }
        }

        /** Picks the next candidate and requests its chunk, or gives up. */
        private void next() {
            int skips = 0;
            long now = System.currentTimeMillis();
            while (!result.isDone()) {
                if (attemptsLeft <= 0) {
                    result.complete(null);
                    return;
                }
                int[] column = randomColumn();
                if (column == null) {
                    result.complete(null);
                    return;
                }
                int chunkX = column[0] >> 4;
                int chunkZ = column[1] >> 4;
                long key = chunkKey(chunkX, chunkZ);
                if (isBadChunkFresh(bad, key, now) && ++skips <= MAX_BAD_SKIPS) {
                    stats.countReject(RtpSearchStats.Reject.BAD_CHUNK);
                    continue;
                }
                attemptsLeft--;
                stats.countCandidate();
                load(column, chunkX, chunkZ, key);
                return;
            }
        }

        private void load(int[] column, int chunkX, int chunkZ, long key) {
            CompletableFuture<Chunk> future;
            try {
                future = world.getChunkAtAsync(chunkX, chunkZ, true);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "rtp: could not request chunk " + chunkX + "," + chunkZ, e);
                stats.countChunkLoadError();
                stats.countReject(RtpSearchStats.Reject.NO_SURFACE);
                rememberBadChunk(region.id(), key, System.currentTimeMillis());
                next();
                return;
            }
            future.whenComplete((chunk, error) -> runMain(() -> onLoaded(chunk, error, column, key)));
        }

        private void onLoaded(Chunk chunk, Throwable error, int[] column, long key) {
            if (result.isDone()) {
                return;
            }
            if (error != null || chunk == null) {
                stats.countChunkLoadError();
                stats.countReject(RtpSearchStats.Reject.NO_SURFACE);
                next();
                return;
            }
            stats.countChunkLoaded();
            try {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                int baseX = chunk.getX() << 4;
                int baseZ = chunk.getZ() << 4;
                for (int i = 0; i < settings.columnsPerChunk(); i++) {
                    int x = i == 0 ? column[0] : baseX + random.nextInt(16);
                    int z = i == 0 ? column[1] : baseZ + random.nextInt(16);
                    if (!bounds.inside(x + 0.5, z + 0.5)) {
                        stats.countReject(RtpSearchStats.Reject.OUT_OF_BOUNDS);
                        continue;
                    }
                    int feet = validator.findFeetY(world, x, z, region);
                    if (feet == RtpValidator.INVALID) {
                        stats.countReject(RtpSearchStats.Reject.NO_SURFACE);
                        continue;
                    }
                    result.complete(new RtpSpot(world.getUID(), x, feet, z, System.currentTimeMillis()));
                    return;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "rtp: error while checking chunk for region '" + region.id() + "'", e);
            }
            rememberBadChunk(region.id(), key, System.currentTimeMillis());
            next();
        }

        /**
         * A random column inside the effective bounds, or null if repeated tries fail. Uses an
         * area-uniform annulus sample for circles and a bounded-rejection ring sample for squares.
         */
        private int[] randomColumn() {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            boolean circle = region.shape() == RtpRegion.Shape.CIRCLE;
            double minRadius = bounds.effectiveMin();
            double maxRadius = bounds.effectiveMax();
            if (maxRadius <= 0) {
                return null;
            }
            for (int i = 0; i < MAX_COLUMN_TRIES; i++) {
                double x;
                double z;
                if (circle) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double radiusSquared = minRadius * minRadius
                            + random.nextDouble() * (maxRadius * maxRadius - minRadius * minRadius);
                    double radius = Math.sqrt(radiusSquared);
                    x = bounds.centerX() + Math.cos(angle) * radius;
                    z = bounds.centerZ() + Math.sin(angle) * radius;
                } else {
                    x = bounds.centerX() + (random.nextDouble() * 2.0 - 1.0) * maxRadius;
                    z = bounds.centerZ() + (random.nextDouble() * 2.0 - 1.0) * maxRadius;
                    if (minRadius > 0) {
                        double dx = Math.abs(x - bounds.centerX());
                        double dz = Math.abs(z - bounds.centerZ());
                        if (Math.max(dx, dz) < minRadius) {
                            // Inside the square's inner hole; retry with the outer ring instead of rejecting silently.
                            double offset = minRadius + random.nextDouble() * (maxRadius - minRadius);
                            if (random.nextBoolean()) {
                                x = bounds.centerX() + (random.nextBoolean() ? offset : -offset);
                                z = bounds.centerZ() + (random.nextDouble() * 2.0 - 1.0) * offset;
                            } else {
                                z = bounds.centerZ() + (random.nextBoolean() ? offset : -offset);
                                x = bounds.centerX() + (random.nextDouble() * 2.0 - 1.0) * offset;
                            }
                        }
                    }
                }
                int blockX = (int) Math.floor(x);
                int blockZ = (int) Math.floor(z);
                if (bounds.inside(blockX + 0.5, blockZ + 0.5)) {
                    return new int[]{blockX, blockZ};
                }
                stats.countReject(RtpSearchStats.Reject.OUT_OF_BOUNDS);
            }
            return null;
        }
    }
}
