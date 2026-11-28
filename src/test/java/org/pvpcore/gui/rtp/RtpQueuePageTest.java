package org.pvpcore.gui.rtp;

import org.bukkit.WorldCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The queue picker must offer only regions that are registered, permission-allowed and
 * backed by a loaded world. Those are the same destinations the queue command accepts.
 */
class RtpQueuePageTest {
    private ServerMock server;
    private PluginMock plugin;
    private RtpModule rtp;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("rtpq-test");
        File folder = new File(plugin.getDataFolder(), "teleport");
        Files.createDirectories(folder.toPath());
        Files.writeString(new File(folder, "rtp.yml").toPath(), """
                enabled: true
                pool:
                  enabled: false
                regions:
                  arena:
                    world: arena
                  vip:
                    world: arena
                    permission: rtp.vip
                  ghost:
                    world: never-loaded
                """, StandardCharsets.UTF_8);

        rtp = new RtpModule(plugin, null, null, folder);
        rtp.enable();
    }

    @AfterEach
    void tearDown() {
        rtp.disable();
        MockBukkit.unmock();
    }

    @Test
    void eligibleRegionsKeepConfigOrderAndRespectPermissionAndWorldFilters() {
        PlayerMock player = server.addPlayer("Queuer");
        server.createWorld(new WorldCreator("arena"));

        assertEquals(List.of("arena"), ownIds(player));

        player.addAttachment(plugin, "rtp.vip", true);
        assertEquals(List.of("arena", "vip"), ownIds(player));
    }

    @Test
    void unloadedWorldsAreNotOffered() {
        PlayerMock player = server.addPlayer("Queuer");

        assertEquals(List.of(), ownIds(player));
    }

    /** Only the three regions this test wrote; the bundled rtp.yml adds its own defaults. */
    private List<String> ownIds(PlayerMock player) {
        return RtpQueuePage.visibleQueuedDestinations(rtp, player).stream()
                .map(RtpRegion::id)
                .filter(id -> List.of("arena", "vip", "ghost").contains(id))
                .toList();
    }
}
