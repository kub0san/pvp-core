package org.pvpcore.features.teleport.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.pvpcore.config.Messages;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MockBukkit tests for the /rtpq match pipeline (eligibility, one shared spot, cancel-on-failure). */
class RtpMatchmakerTest {
    private static final String PERMISSION = "pvpcore.teleportation.rtpq";

    private ServerMock server;
    private PluginMock plugin;
    private TeleportModule teleport;
    private TeleportService service;
    private Messages messages;
    private RtpSettings currentSettings;
    private RtpRegion region;
    private Location spot;
    private PlayerMock first;
    private PlayerMock second;
    private AtomicInteger acquires;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        World world = server.createWorld(new WorldCreator("rtpq-world"));
        spot = new Location(world, 10, 64, 10);

        teleport = new TeleportModule(plugin);
        teleport.enable();
        service = teleport.service();
        messages = teleport.messages();

        region = new RtpRegion("r", world.getName(), RtpRegion.Shape.CIRCLE,
                true, 0, 0, 0, 0, RtpRegion.Vertical.SURFACE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, "");
        currentSettings = settings(0);
        first = server.addPlayer();
        second = server.addPlayer();
        first.setOp(false);
        second.setOp(false);
        acquires = new AtomicInteger();
        drain(first);
        drain(second);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bothEligible_sharedAcquireCalledOnce_andBothBegin() {
        grantBoth();
        RtpMatchmaker matchmaker = matchmaker((r, p) -> {
            acquires.incrementAndGet();
            return CompletableFuture.completedFuture(spot);
        });

        matchmaker.startMatch(match());

        assertEquals(1, acquires.get(), "one shared acquire() for both players");
        assertFalse(drain(first).isEmpty(), "first player must be notified of the match");
        assertFalse(drain(second).isEmpty(), "second player must be notified of the match");
    }

    @Test
    void nullAcquire_cancelsBoth_rtpFailed_noRequeue() {
        grantBoth();
        RtpMatchmaker matchmaker = matchmaker((r, p) -> {
            acquires.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        matchmaker.startMatch(match());

        assertEquals(1, acquires.get());
        assertFalse(drain(first).isEmpty(), "first player must be told the match failed");
        assertFalse(drain(second).isEmpty(), "second player must be told the match failed");
        assertFalse(service.hasPending(first), "no warmup may start when acquire returns null");
        assertFalse(service.hasPending(second));
    }

    @Test
    void ineligibleAtMatchFormation_oneOffline_cancelsBoth() {
        first.addAttachment(plugin, PERMISSION, true);
        RtpMatchmaker matchmaker = countingMatchmaker();
        UUID offline = UUID.randomUUID();

        matchmaker.startMatch(new RtpMatch("r", first.getUniqueId(), offline));

        assertEquals(0, acquires.get(), "acquire() must not run when a player is offline");
        assertFalse(drain(first).isEmpty(), "the online player must still be told the match failed");
    }

    @Test
    void ineligibleNoPermission_cancelsBoth() {
        // Both players are online but neither has the /rtpq permission.
        RtpMatchmaker matchmaker = countingMatchmaker();

        matchmaker.startMatch(match());

        assertEquals(0, acquires.get(), "no acquire() when a player lacks the queue permission");
        assertFalse(drain(first).isEmpty());
        assertFalse(drain(second).isEmpty());
    }

    @Test
    void ineligibleHasPending_cancelsBoth() {
        grantBoth();
        service.begin(second, () -> spot,
                new TeleportService.Options(60, 0, "rtp-failed", "rtp-success"));
        assertTrue(service.hasPending(second));
        drain(second); // discard warmup-started

        RtpMatchmaker matchmaker = countingMatchmaker();
        matchmaker.startMatch(match());

        assertEquals(0, acquires.get(), "a player with a pending warmup cannot be matched");
        assertFalse(drain(first).isEmpty());
        assertFalse(drain(second).isEmpty());
    }

    @Test
    void recheckAtTeleportTime_oneWentOffline_cancelsBothWithoutBegin() {
        grantBoth();
        currentSettings = settings(60); // if begin() ran, a warmup would be pending
        CompletableFuture<Location> future = new CompletableFuture<>();
        RtpMatchmaker matchmaker = matchmaker((r, p) -> {
            acquires.incrementAndGet();
            return future;
        });

        matchmaker.startMatch(match());
        assertEquals(1, acquires.get());
        drain(first);
        drain(second);

        second.kick(); // eligibility changes between acquire() and its completion
        future.complete(spot);

        assertFalse(service.hasPending(first), "no warmup may start after the recheck fails");
        assertFalse(service.hasPending(second));
        assertFalse(drain(first).isEmpty(), "both players must be told the match failed");
    }

    private RtpSettings settings(int warmup) {
        return new RtpSettings(true, "r", warmup, 0, 12, 6, 16, 2, 15,
                false, false, 0, 900, 10, Set.of(), Set.of(), false, Map.of("r", region));
    }

    private RtpMatchmaker matchmaker(
            java.util.function.BiFunction<RtpRegion, org.bukkit.entity.Player, CompletableFuture<Location>> acquire) {
        return new RtpMatchmaker(plugin, service, () -> currentSettings, () -> messages, acquire);
    }

    private RtpMatchmaker countingMatchmaker() {
        return matchmaker((r, p) -> {
            acquires.incrementAndGet();
            return CompletableFuture.completedFuture(spot);
        });
    }

    private void grantBoth() {
        first.addAttachment(plugin, PERMISSION, true);
        second.addAttachment(plugin, PERMISSION, true);
    }

    private RtpMatch match() {
        return new RtpMatch("r", first.getUniqueId(), second.getUniqueId());
    }

    private static List<String> drain(PlayerMock player) {
        List<String> out = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            out.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return out;
    }
}
