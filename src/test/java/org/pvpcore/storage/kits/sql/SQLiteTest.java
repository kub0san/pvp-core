package org.pvpcore.storage.kits.sql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SQLiteTest {
    private PluginMock plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void connectsAndReturnsNullForMissingRow() {
        SQLite storage = new SQLite(plugin);
        assertDoesNotThrow(storage::connect, "the bundled SQLite backend must be able to open its database");
        assertDoesNotThrow(storage::init);
        try {
            assertNull(storage.getKitDataByID("definitely-missing"),
                    "a missing row is a null result, not a magic error string");
        } finally {
            assertDoesNotThrow(storage::close);
        }
    }

    /** Regression for #7: a legitimate stored value must not be mistaken for an error sentinel. */
    @Test
    void literalErrorBlobRoundTrips() {
        SQLite storage = new SQLite(plugin);
        assertDoesNotThrow(storage::connect);
        assertDoesNotThrow(storage::init);
        try {
            storage.saveKitDataByID("weird-data", "error");
            assertEquals("error", storage.getKitDataByID("weird-data"));
        } finally {
            assertDoesNotThrow(storage::close);
        }
    }
}
