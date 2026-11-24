package org.pvpcore.features.kits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for DB2-10: failed-write recovery files must be readable and retired safely. */
class FailedWriteRecoveryTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsAndRetiresRecoveryFile() throws Exception {
        MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        Path folder = plugin.getDataFolder().toPath();
        String yaml = "storage-type: sqlite\nkits.player-1: encoded-data\ndelete.player-2: true\n";
        Files.writeString(folder.resolve("failed-kit-writes-100.yml"), yaml, StandardCharsets.UTF_8);

        List<FailedWriteRecovery.Entry> entries = FailedWriteRecovery.read(folder);

        assertEquals(2, entries.size());
        assertTrue(entries.contains(new FailedWriteRecovery.Entry("player-1", "encoded-data")));
        assertTrue(entries.contains(new FailedWriteRecovery.Entry("player-2", KitRecordStore.DELETED)));

        FailedWriteRecovery.markApplied(folder.resolve("failed-kit-writes-100.yml"));
        assertFalse(Files.exists(folder.resolve("failed-kit-writes-100.yml")));
        assertTrue(Files.exists(folder.resolve("failed-kit-writes-100.yml.applied")));
        assertTrue(FailedWriteRecovery.read(folder).isEmpty());
    }

    @Test
    void rewritingDropsSupersededIds() throws Exception {
        MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        Path folder = plugin.getDataFolder().toPath();
        Path file = folder.resolve("failed-kit-writes-200.yml");
        Files.writeString(file, "kits.one: a\nkits.two: b\n", StandardCharsets.UTF_8);

        FailedWriteRecovery.rewrite(file, Map.of("one", "a"));

        List<FailedWriteRecovery.Entry> entries = FailedWriteRecovery.read(folder);
        assertEquals(List.of(new FailedWriteRecovery.Entry("one", "a")), entries);
    }
}
