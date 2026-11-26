package org.pvpcore.features.worldgen;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for F-P2-02: one immutable settings snapshot must be published atomically, and a
 * component that captured a snapshot must keep reading it after a concurrent reload.
 *
 * <p>These tests deliberately use the {@code load(Logger, FileConfiguration)} seam so no plugin
 * or server is needed. Every assertion is against {@code origin/master} semantics only; no
 * PR #32-only behaviour (border/radius cross-check, biome DEFAULTS fallback, noise floor,
 * shared {@code positionHash01}, root-level {@code biomes.<path>} lookup) is asserted here.</p>
 */
class WorldGenSettingsSnapshotTest {

    private static final Logger LOG = Logger.getLogger(WorldGenSettingsSnapshotTest.class.getName());

    static {
        LOG.setLevel(Level.OFF);
    }

    private static YamlConfiguration config(int radius, int border, List<String> expectedWorlds) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("max-radius-blocks", radius);
        cfg.set("world-border-diameter", border);
        cfg.set("expected-worlds", expectedWorlds);
        return cfg;
    }

    private static boolean coherent(WorldGenSettings.Snapshot snapshot) {
        int radius = snapshot.maxRadiusBlocks();
        int border = snapshot.worldBorderDiameter();
        Set<String> worlds = snapshot.expectedWorlds();
        return (radius == 100 && border == 200 && worlds.equals(Set.of("arena-a")))
                || (radius == 200 && border == 400 && worlds.equals(Set.of("arena-b")));
    }

    /** A single published snapshot never exposes fields from two different config generations. */
    @Test
    void snapshotFieldsStayConsistentUnderConcurrentReload() throws Exception {
        WorldGenSettings settings = new WorldGenSettings();
        YamlConfiguration configA = config(100, 200, List.of("arena-a"));
        YamlConfiguration configB = config(200, 400, List.of("arena-b"));
        settings.load(LOG, configA);

        final int iterations = 3000;
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writerDone = new AtomicBoolean(false);
        AtomicBoolean torn = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    settings.load(LOG, (i & 1) == 0 ? configB : configA);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                writerDone.set(true);
            }
        }, "worldgen-settings-writer");

        Thread reader = new Thread(() -> {
            try {
                start.await();
                while (!writerDone.get()) {
                    // Validate every single captured snapshot on its own. A fresh object built
                    // from several independent volatile fields could mix generations here.
                    WorldGenSettings.Snapshot first = settings.snapshot();
                    if (!coherent(first)) {
                        torn.set(true);
                    }
                    WorldGenSettings.Snapshot second = settings.snapshot();
                    if (first != second && !coherent(second)) {
                        torn.set(true);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "worldgen-settings-reader");

        writer.start();
        reader.start();
        start.countDown();
        writer.join(15_000);
        reader.join(15_000);

        assertFalse(writer.isAlive(), "writer thread must finish");
        assertFalse(reader.isAlive(), "reader thread must finish");
        assertFalse(torn.get(), "a single published snapshot must never mix old/new fields");
    }

    /** A snapshot captured before a reload keeps all of its original values. */
    @Test
    void capturedSnapshotIsUnchangedByLaterReload() {
        WorldGenSettings settings = new WorldGenSettings();
        settings.load(LOG, config(100, 200, List.of("alpha")));
        WorldGenSettings.Snapshot captured = settings.snapshot();

        settings.load(LOG, config(300, 600, List.of("beta")));

        assertEquals(100, captured.maxRadiusBlocks());
        assertEquals(200, captured.worldBorderDiameter());
        assertEquals(Set.of("alpha"), captured.expectedWorlds());
        WorldGenSettings.Snapshot current = settings.snapshot();
        assertNotSame(captured, current);
        assertEquals(300, current.maxRadiusBlocks());
        assertEquals(600, current.worldBorderDiameter());
        assertEquals(Set.of("beta"), current.expectedWorlds());
    }

    /** One {@code load(logger, config)} call derives terrain and expected worlds from that config. */
    @Test
    void terrainAndExpectedWorldsComeFromTheSameConfigObject() {
        YamlConfiguration cfg = config(100, 200, List.of("arena-x", "arena-y", ""));
        cfg.set("biomes.plains.surface-y", 90);

        WorldGenSettings settings = new WorldGenSettings();
        settings.load(LOG, cfg);

        WorldGenSettings.Snapshot snapshot = settings.snapshot();
        assertEquals(Set.of("arena-x", "arena-y"), snapshot.expectedWorlds());
        assertEquals(90, snapshot.terrain().get(ArenaBiome.PLAINS).surfaceY());
        assertEquals(100, snapshot.maxRadiusBlocks());
        assertEquals(200, snapshot.worldBorderDiameter());
    }

    /** Master radius rule: floor at 16 only, never a border/radius cross-check. */
    @Test
    void radiusFloorIsSixteenAndNoBorderCrossCheck() {
        WorldGenSettings settings = new WorldGenSettings();

        settings.load(LOG, config(4, 20, List.of()));
        assertEquals(16, settings.snapshot().maxRadiusBlocks(),
                "master floors max-radius-blocks at one chunk (16)");

        // A border smaller than 2*radius must NOT clamp the radius (PR #32-only behaviour).
        settings.load(LOG, config(500, 24, List.of()));
        assertEquals(500, settings.snapshot().maxRadiusBlocks());
        assertEquals(24, settings.snapshot().worldBorderDiameter());

        // insideRadius keeps master's inclusive, plain-int radius semantics.
        assertTrue(settings.insideRadius(0, 0));
        assertTrue(settings.insideRadius(31, 31)); // max block X = 511 > 500, min = 496 <= 500
        assertFalse(settings.insideRadius(32, 32)); // min block X = 512 > 500
    }

    /** Old convenience accessors must always read the current published snapshot. */
    @Test
    void convenienceAccessorsReadTheCurrentSnapshot() {
        WorldGenSettings settings = new WorldGenSettings();
        settings.load(LOG, config(100, 200, List.of()));

        assertEquals(100, settings.maxRadiusBlocks());
        assertEquals(200, settings.worldBorderDiameter());
        assertFalse(settings.allowMobSpawning());
        assertEquals(6000L, settings.dayTime());

        settings.load(LOG, config(250, 500, List.of()));
        WorldGenSettings.Snapshot current = settings.snapshot();

        assertEquals(250, settings.maxRadiusBlocks());
        assertEquals(500, settings.worldBorderDiameter());
        assertEquals(current.maxRadiusBlocks(), settings.maxRadiusBlocks());
        assertEquals(current.worldBorderDiameter(), settings.worldBorderDiameter());
    }
}
