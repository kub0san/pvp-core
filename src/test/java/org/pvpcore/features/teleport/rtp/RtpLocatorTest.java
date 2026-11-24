package org.pvpcore.features.teleport.rtp;

import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;

class RtpLocatorTest {
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

    /** Regression for issue #1: a region with no usable radius must not hang the search future. */
    @Test
    void findCompletesWhenEffectiveMaxRadiusIsZero() throws Exception {
        World world = server.createWorld(new WorldCreator("rtp-zero-radius"));
        world.getWorldBorder().setSize(4); // smaller than borderMargin => effective max radius 0

        RtpRegion region = new RtpRegion("zero", world.getName(), RtpRegion.Shape.CIRCLE,
                true, 0, 0, 0, 0, RtpRegion.Vertical.SURFACE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, "");
        RtpSettings settings = new RtpSettings(true, "zero", 0, 0, 12, 6, 16, 2, 15,
                false, false, 0, 900, 10, Set.of(), Set.of(), false, Map.of("zero", region));
        RtpValidator validator = new RtpValidator(Set.of(), Set.of(), List.of());
        RtpLocator locator = new RtpLocator(plugin, settings, validator);

        CompletableFuture<RtpSpot> future = locator.find(region);

        assertNull(future.get(1, TimeUnit.SECONDS),
                "search future must complete with no spot when the effective radius is zero");
    }
}
