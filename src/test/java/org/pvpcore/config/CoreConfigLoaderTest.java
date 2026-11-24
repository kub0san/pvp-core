package org.pvpcore.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CoreConfigLoaderTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Regression for #8: malformed YAML must not abort the owning module's enable. */
    @Test
    void invalidYamlFallsBackWithoutThrowing() throws Exception {
        PluginMock plugin = MockBukkit.createMockPlugin();
        String raw = "key: [unclosed\n";
        File file = new File(plugin.getDataFolder(), "broken.yml");
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), raw, StandardCharsets.UTF_8);

        var result = assertDoesNotThrow(() ->
                CoreConfigLoader.loadWithStatus(plugin, "broken.yml", List.of()));

        assertNotNull(result.config());
        assertEquals(raw, Files.readString(file.toPath(), StandardCharsets.UTF_8),
                "a malformed admin file must not be silently overwritten");
    }
}
