package org.pvpcore.features.kits;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for DB2-11: save feedback must be delivered after the write resolves. */
class SaveFeedbackTest {
    private ServerMock server;
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void successIsReportedOnlyAfterWriteCompletes() {
        Player player = server.addPlayer();
        List<Boolean> outcomes = new ArrayList<>();
        CompletableFuture<Void> write = new CompletableFuture<>();
        KitManager.reportWhenWritten(plugin, player.getUniqueId(), write, (p, ok) -> outcomes.add(ok));

        write.complete(null);
        server.getScheduler().performTicks(1);

        assertEquals(List.of(true), outcomes);
    }

    @Test
    void failureIsReportedToOnlinePlayer() {
        Player player = server.addPlayer();
        List<Boolean> outcomes = new ArrayList<>();
        CompletableFuture<Void> write = new CompletableFuture<>();
        KitManager.reportWhenWritten(plugin, player.getUniqueId(), write, (p, ok) -> outcomes.add(ok));

        write.completeExceptionally(new IllegalStateException("disk failed"));
        server.getScheduler().performTicks(1);

        assertEquals(List.of(false), outcomes);
    }

    @Test
    void noFeedbackWhenPlayerLeft() {
        List<Boolean> outcomes = new ArrayList<>();
        CompletableFuture<Void> write = new CompletableFuture<>();
        KitManager.reportWhenWritten(plugin, UUID.randomUUID(), write, (p, ok) -> outcomes.add(ok));

        write.complete(null);
        server.getScheduler().performTicks(1);

        assertTrue(outcomes.isEmpty());
    }
}
