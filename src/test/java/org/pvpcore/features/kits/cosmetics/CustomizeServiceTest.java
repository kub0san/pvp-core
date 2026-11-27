package org.pvpcore.features.kits.cosmetics;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitSaveResult;
import org.pvpcore.gui.kits.GUI;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeServiceTest {
    private ServerMock server;
    private Player player;
    private ItemStack[] original;
    private CustomizeSession session;
    private CompletableFuture<Void> pendingWrite;
    private KitSaveResult saveResult;
    private AtomicInteger saves;
    private AtomicInteger reads;
    private AtomicReference<ItemStack[]> savedKit;
    private CustomizeRules rules;
    private CustomizeService.KitSaver saver;
    private CustomizeService.GuardChecker guard;
    private CustomizeService.CurrentKitReader reader;
    private CustomizeService.StaleChecker stale;
    private GUI gui;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        rules = CustomizeRules.parse(null);
        original = kit();
        session = new CustomizeSession(original);
        session.selectTarget(36);
        session.stageTarget(new ItemStack(Material.NETHERITE_CHESTPLATE));

        pendingWrite = new CompletableFuture<>();
        saveResult = new KitSaveResult(true, pendingWrite);
        saves = new AtomicInteger();
        reads = new AtomicInteger();
        savedKit = new AtomicReference<>();
        saver = (p, slot, kit) -> {
            saves.incrementAndGet();
            savedKit.set(kit);
            return saveResult;
        };
        guard = p -> true;
        reader = (p, slot) -> {
            reads.incrementAndGet();
            return kit();
        };
        stale = p -> false;
        gui = new GUI(MockBukkit.createMockPlugin(), null);
    }

    @AfterEach
    void tearDown() {
        GUI.shutdown();
        MockBukkit.unmock();
    }

    private static ItemStack[] kit() {
        ItemStack[] kit = new ItemStack[41];
        kit[36] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        kit[40] = new ItemStack(Material.SHIELD);
        return kit;
    }

    private CustomizeService service() {
        return new CustomizeService(rules, saver, guard, reader, stale);
    }

    @Test
    void validCommitQueuesWriteWithoutCancellingIt() {
        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session));

        assertEquals(1, saves.get());
        assertEquals(1, reads.get());
        assertNotNull(savedKit.get());
        assertEquals(Material.NETHERITE_CHESTPLATE, savedKit.get()[36].getType());
        assertFalse(pendingWrite.isCancelled());
    }

    @Test
    void staleKitIsRejectedBeforeSaving() {
        reader = (p, slot) -> {
            reads.incrementAndGet();
            ItemStack[] changed = kit();
            changed[40] = new ItemStack(Material.SHIELD, 2);
            return changed;
        };

        assertEquals(ApplyResult.KIT_CHANGED, service().commit(player, 1, session));
        assertEquals(0, saves.get());
        assertEquals(1, reads.get());
    }

    @Test
    void permissionRevokedIsRejectedBeforeReading() {
        guard = p -> false;

        assertEquals(ApplyResult.PERMISSION_DENIED, service().commit(player, 1, session));
        assertEquals(0, reads.get());
        assertEquals(0, saves.get());
    }

    @Test
    void rejectedSaveIsReportedAsStorageFailed() {
        saveResult = KitSaveResult.rejected();

        assertEquals(ApplyResult.STORAGE_FAILED, service().commit(player, 1, session));
        assertTrue(saves.get() > 0);
    }

    @Test
    void exceptionalWriteIsLoggedWithoutThrowing() {
        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session));
        assertDoesNotThrow(() -> pendingWrite.completeExceptionally(new IllegalStateException("disk failed")));
        assertTrue(pendingWrite.isCompletedExceptionally());
    }

    @Test
    void disabledCosmeticsRejectContent() {
        rules = new CustomizeRules(false, false, false, 256,
                java.util.Set.of(), java.util.Set.of());

        assertEquals(ApplyResult.INVALID_CONTENT, service().commit(player, 1, session));
        assertEquals(0, saves.get());
    }

    @Test
    void reloadInvalidatedScreenIsRejected() {
        UUID viewer = player.getUniqueId();
        UUID operationId = gui.setPendingCustomize(viewer, session);
        gui.invalidateCustomizeSessions();

        assertTrue(gui.isCustomizeStale(viewer));
        assertNull(gui.takePendingCustomize(viewer, operationId));

        stale = p -> gui.isCustomizeStale(p.getUniqueId());
        assertEquals(ApplyResult.KIT_CHANGED, service().commit(player, 1, session));
        assertEquals(0, saves.get());
    }

    @Test
    void pendingCustomizeUsesUniqueOperationIdsAndIgnoresMismatch() {
        UUID viewer = player.getUniqueId();
        UUID first = gui.setPendingCustomize(viewer, session);
        CustomizeSession newer = new CustomizeSession(original);
        UUID second = gui.setPendingCustomize(viewer, newer);

        assertFalse(first.equals(second));
        assertNull(gui.takePendingCustomize(viewer, first));
        assertSame(newer, gui.takePendingCustomize(viewer, second));
        assertNull(gui.takePendingCustomize(viewer, second));
    }

    @Test
    void closeAfterQueuedStillCommitsTheWrite() {
        UUID viewer = player.getUniqueId();
        gui.setPendingCustomize(viewer, session);

        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session));
        assertEquals(1, saves.get());

        // Closing the screen and clearing the pending entry must not cancel the write.
        gui.clearPendingCustomize(viewer);
        assertDoesNotThrow(() -> pendingWrite.complete(null));

        assertFalse(pendingWrite.isCancelled());
        assertTrue(pendingWrite.isDone());
        assertEquals(1, saves.get());
        assertEquals(Material.NETHERITE_CHESTPLATE, savedKit.get()[36].getType());
    }

    @Test
    void forgetMainMenuPageClearsPendingAndStaleState() {
        UUID viewer = player.getUniqueId();
        UUID operationId = gui.setPendingCustomize(viewer, session);
        gui.registerCustomizeScreen(viewer);
        gui.invalidateCustomizeSessions();
        assertTrue(gui.isCustomizeStale(viewer));

        GUI.forgetMainMenuPage(viewer);

        assertFalse(gui.isCustomizeStale(viewer));
        assertNull(gui.takePendingCustomize(viewer, operationId));
        gui.invalidateCustomizeSessions();
        assertFalse(gui.isCustomizeStale(viewer)); // no screen or pending left to invalidate
    }

    @Test
    void openScreenViewersAreInvalidatedOnReload() {
        UUID viewer = player.getUniqueId();
        gui.registerCustomizeScreen(viewer);

        gui.invalidateCustomizeSessions();

        assertTrue(gui.isCustomizeStale(viewer));
        stale = p -> gui.isCustomizeStale(p.getUniqueId());
        assertEquals(ApplyResult.KIT_CHANGED, service().commit(player, 1, session));
        assertEquals(0, saves.get());
    }

    @Test
    void unregisteringAllScreensStopsReloadInvalidation() {
        UUID viewer = player.getUniqueId();
        gui.registerCustomizeScreen(viewer); // customize screen
        gui.registerCustomizeScreen(viewer); // rename screen
        gui.unregisterCustomizeScreen(viewer);
        gui.unregisterCustomizeScreen(viewer);

        gui.invalidateCustomizeSessions();

        assertFalse(gui.isCustomizeStale(viewer));
    }

    @Test
    void holdPendingCustomizeDoesNotClearReloadStaleMarker() {
        UUID viewer = player.getUniqueId();
        gui.markCustomizeStale(viewer);

        UUID operationId = gui.holdPendingCustomize(viewer, session);

        assertTrue(gui.isCustomizeStale(viewer));
        assertNull(gui.takePendingCustomize(viewer, operationId));
    }

    @Test
    void setPendingCustomizeDoesNotClearReloadStaleMarker() {
        UUID viewer = player.getUniqueId();
        gui.markCustomizeStale(viewer);

        UUID operationId = gui.setPendingCustomize(viewer, session);

        assertTrue(gui.isCustomizeStale(viewer));
        assertNull(gui.takePendingCustomize(viewer, operationId));
    }

    @Test
    void failedSaveRebaseFromCacheKeepsStagedEditsAndRetryQueues() {
        AtomicReference<ItemStack[]> cache = new AtomicReference<>(kit());
        reader = (p, slot) -> cache.get();
        saver = (p, slot, kit) -> {
            saves.incrementAndGet();
            cache.set(kit); // KitManager mutates the cache before the DB future resolves
            savedKit.set(kit);
            return saveResult;
        };

        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session));
        pendingWrite.completeExceptionally(new IllegalStateException("disk failed"));

        CustomizeSession rebased = session.rebaseFrom(cache.get());

        assertTrue(rebased.matchesCurrent(cache.get()));
        assertEquals(Material.NETHERITE_CHESTPLATE, rebased.target().getType());
        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, rebased));
        assertEquals(2, saves.get());
    }

    @Test
    void saveFailureMessageKeysAreDistinctAndLegacyKeyStillSaysNoChanges() throws Exception {
        YamlConfiguration yaml;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("messages.yml")) {
            assertNotNull(in, "bundled messages.yml must be on the test classpath");
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String legacy = yaml.getString("pvpcore.error.cosmetics-save-failed");
        String unconfirmed = yaml.getString("pvpcore.error.cosmetics-save-unconfirmed");

        assertNotNull(legacy);
        assertNotNull(unconfirmed);
        assertNotEquals(legacy, unconfirmed);
        assertTrue(legacy.toLowerCase(Locale.ROOT).contains("no changes were applied"));
    }

    @Test
    void outcomeReceivesSuccessOnlyAfterQueuedWriteCompletes() {
        AtomicReference<Boolean> outcome = new AtomicReference<>();

        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session, outcome::set));
        assertNull(outcome.get());
        assertFalse(pendingWrite.isDone());

        pendingWrite.complete(null);
        assertEquals(Boolean.TRUE, outcome.get());
    }

    @Test
    void outcomeReceivesFailureWithoutPropagatingTheStorageError() {
        AtomicReference<Boolean> outcome = new AtomicReference<>();

        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session, outcome::set));
        assertDoesNotThrow(() -> pendingWrite.completeExceptionally(new IllegalStateException("disk failed")));

        assertEquals(Boolean.FALSE, outcome.get());
        assertEquals(1, saves.get());
    }

    @Test
    void throwingOutcomeCallbackIsLoggedWithoutBreakingCompletion() {
        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session, success -> {
            throw new IllegalStateException("callback failed");
        }));
        assertDoesNotThrow(() -> pendingWrite.complete(null));
    }

    @Test
    void emptyKitSnapshotMatchesFreshEmptyReadAtApply() {
        session = new CustomizeSession(new ItemStack[0]);
        reader = (p, slot) -> null; // a kit that was never saved reads back as null

        assertEquals(ApplyResult.QUEUED, service().commit(player, 1, session));
        assertEquals(1, saves.get());
        assertNotNull(savedKit.get());
        assertEquals(41, savedKit.get().length);
    }
}
