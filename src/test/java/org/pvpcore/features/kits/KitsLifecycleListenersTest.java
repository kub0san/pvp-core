package org.pvpcore.features.kits;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for X-02: listeners registered before a failed enable must be removable. */
class KitsLifecycleListenersTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void unregisterListenersRemovesModuleListeners() {
        PluginMock plugin = MockBukkit.createMockPlugin();
        KitsLifecycle lifecycle = new KitsLifecycle();
        Listener listener = new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
            }
        };

        lifecycle.registerListener(listener, plugin);
        assertTrue(HandlerList.getRegisteredListeners(plugin).stream()
                .anyMatch(entry -> entry.getListener() == listener));

        lifecycle.unregisterListeners();
        assertFalse(HandlerList.getRegisteredListeners(plugin).stream()
                .anyMatch(entry -> entry.getListener() == listener));
    }
}
