package org.pvpcore.features.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatListenerTest {
    private ServerMock server;
    private CombatManager manager;
    private World from;
    private World to;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PluginMock plugin = MockBukkit.createMockPlugin();
        from = server.createWorld(new WorldCreator("combat-from"));
        to = server.createWorld(new WorldCreator("combat-to"));
        CombatConfig config = new CombatConfig(true, 15_000_000_000L, 3_000_000_000L,
                2.0, PenaltyMode.NONE, true, true, false, 10.0, true);
        manager = new CombatManager(config);
        Bukkit.getPluginManager().registerEvents(
                new CombatListener(manager, null, null), plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Regression for L-05: a world change must clear the player's combat tag. */
    @Test
    void worldChangeClearsCombatTag() {
        Player player = server.addPlayer();
        UUID id = player.getUniqueId();
        manager.tagPlayer(id, CombatCause.PLAYER_MELEE);

        assertTrue(manager.isInCombat(id));
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, from));
        assertFalse(manager.isInCombat(id), "combat state must not follow a player across worlds");
    }

    /** Pearl tagging deliberately survives the hop; world-change clearing must not undo it. */
    @Test
    void pearlTagSurvivesCrossWorldHop() {
        Player player = server.addPlayer();
        UUID id = player.getUniqueId();

        Bukkit.getPluginManager().callEvent(new PlayerTeleportEvent(player,
                new Location(from, 0, 64, 0), new Location(to, 0, 64, 0),
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(player, from));

        assertTrue(manager.isInCombat(id), "the pearl tag must still apply after a cross-world hop");
    }

    @Test
    void pearlTagAppliesEvenWithoutWorldChangeEvent() {
        Player player = server.addPlayer();
        UUID id = player.getUniqueId();

        Bukkit.getPluginManager().callEvent(new PlayerTeleportEvent(player,
                new Location(from, 0, 64, 0), new Location(to, 0, 64, 0),
                PlayerTeleportEvent.TeleportCause.ENDER_PEARL));

        assertTrue(manager.isInCombat(id), "tag must not depend on a world-change event arriving");
    }
}
