package org.pvpcore.features.teleport.rtp;

import org.bukkit.WorldCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.pvpcore.features.teleport.TeleportModule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Queue lifecycle: reload and disable clear pooled players without replacing the queue. */
class RtpModuleQueueLifecycleTest {
    private ServerMock server;
    private PluginMock plugin;
    private TeleportModule teleport;
    private RtpModule rtp;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.createWorld(new WorldCreator("w"));
        teleport = new TeleportModule(plugin);
        teleport.enable();
        rtp = teleport.rtp();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadClearsQueue() {
        rtp.rtpq().join("r", UUID.randomUUID());
        assertEquals(1, rtp.rtpq().size());

        rtp.reload();

        assertEquals(0, rtp.rtpq().size());
    }

    @Test
    void disableClearsQueue() {
        rtp.rtpq().join("r", UUID.randomUUID());
        assertEquals(1, rtp.rtpq().size());

        rtp.disable();

        assertEquals(0, rtp.rtpq().size());
    }

    @Test
    void reloadDoesNotRecreateQueue() {
        RtpQueue queue = rtp.rtpq();
        queue.join("r", UUID.randomUUID());

        rtp.reload();

        assertSame(queue, rtp.rtpq());
        assertEquals(0, rtp.rtpq().size());
    }
}
