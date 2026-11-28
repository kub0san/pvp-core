package org.pvpcore.features.teleport.rtp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure in-memory tests: no Bukkit server needed. */
class RtpQueueTest {
    private final UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID d = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private final List<RtpMatch> matches = new ArrayList<>();
    private RtpQueue queue;

    @BeforeEach
    void setUp() {
        queue = new RtpQueue();
        queue.setMatchSink(matches::add);
    }

    @Test
    void firstTwoPlayersPairOldestFirst() {
        assertEquals(RtpQueue.JoinResult.JOINED, queue.join("r", a));
        assertEquals(1, queue.size("r"));
        assertTrue(matches.isEmpty());

        assertEquals(RtpQueue.JoinResult.JOINED, queue.join("r", b));
        assertEquals(List.of(new RtpMatch("r", a, b)), matches);
    }

    @Test
    void regionsAreIndependent() {
        queue.join("r1", a);
        queue.join("r2", b);

        assertTrue(matches.isEmpty());
        assertEquals(1, queue.size("r1"));
        assertEquals(1, queue.size("r2"));
        assertEquals(2, queue.size());
    }

    @Test
    void singleJoinDoesNotMatchAndThirdStartsNewQueue() {
        queue.join("r", a);
        assertTrue(matches.isEmpty());

        queue.join("r", b);
        queue.join("r", c);

        assertEquals(1, matches.size());
        assertEquals(new RtpMatch("r", a, b), matches.get(0));
        assertEquals(1, queue.size("r"));
        assertTrue(queue.isQueued(c));
        assertFalse(queue.isQueued(a));
        assertFalse(queue.isQueued(b));
    }

    @Test
    void pairingRemovesBothPlayersAndRegion() {
        queue.join("r", a);
        queue.join("r", b);

        assertFalse(queue.isQueued(a));
        assertFalse(queue.isQueued(b));
        assertEquals(Optional.empty(), queue.queuedRegion(a));
        assertEquals(Optional.empty(), queue.queuedRegion(b));
        assertEquals(0, queue.size("r"));
        assertEquals(0, queue.size());
    }

    @Test
    void fourPlayersPairTwiceInFifoOrder() {
        queue.join("r", a);
        queue.join("r", b);
        queue.join("r", c);
        queue.join("r", d);

        assertEquals(List.of(new RtpMatch("r", a, b), new RtpMatch("r", c, d)), matches);
        assertEquals(0, queue.size());
    }

    @Test
    void sameRegionRejoinIsNoOpWithoutMatchEmission() {
        assertEquals(RtpQueue.JoinResult.JOINED, queue.join("r", a));
        assertEquals(RtpQueue.JoinResult.ALREADY_IN_REGION, queue.join("r", a));
        assertEquals(1, queue.size("r"));
        assertEquals(Optional.of("r"), queue.queuedRegion(a));
        assertTrue(matches.isEmpty());
    }

    @Test
    void otherRegionRejoinIsRejectedWithoutStateChange() {
        queue.join("r1", a);
        assertEquals(RtpQueue.JoinResult.ALREADY_IN_OTHER_REGION, queue.join("r2", a));

        assertEquals(Optional.of("r1"), queue.queuedRegion(a));
        assertEquals(1, queue.size("r1"));
        assertEquals(0, queue.size("r2"));
        assertTrue(matches.isEmpty());
    }

    @Test
    void leaveRemovesPlayerAndShiftsPairingOrder() {
        assertEquals(RtpQueue.JoinResult.JOINED, queue.join("r", a));
        assertTrue(queue.leave(a));
        assertFalse(queue.leave(a));
        assertFalse(queue.isQueued(a));
        assertEquals(0, queue.size("r"));

        queue.join("r", b);
        queue.join("r", c);
        assertEquals(List.of(new RtpMatch("r", b, c)), matches);
    }

    @Test
    void kickAndQuitUseLeaveSemantics() {
        queue.join("r", a);
        assertTrue(queue.leave(a), "kick delegates to leave");
        queue.join("r", b);
        assertTrue(queue.leave(b), "quit delegates to leave");

        assertTrue(matches.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void clearResetsEverything() {
        queue.join("r1", a);
        queue.join("r2", b);
        queue.clear();

        assertFalse(queue.isQueued(a));
        assertFalse(queue.isQueued(b));
        assertEquals(Optional.empty(), queue.queuedRegion(a));
        assertEquals(0, queue.size("r1"));
        assertEquals(0, queue.size("r2"));
        assertEquals(0, queue.size());

        queue.join("r", a);
        queue.join("r", b);
        assertEquals(List.of(new RtpMatch("r", a, b)), matches);
    }

    @Test
    void regionKeysAreCaseInsensitiveAndMatchUsesPassedInRegionId() {
        queue.join("R", a);
        assertEquals(RtpQueue.JoinResult.ALREADY_IN_REGION, queue.join("r", a));
        assertEquals(Optional.of("R"), queue.queuedRegion(a));
        assertEquals(1, queue.size("r"));

        queue.join("r", b);
        assertEquals(List.of(new RtpMatch("r", a, b)), matches);
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> queue.join(null, a));
        assertThrows(IllegalArgumentException.class, () -> queue.join("r", null));
        assertEquals(0, queue.size());
    }

    @Test
    void nullSinkIsNoOp() {
        queue.setMatchSink(null);
        queue.join("r", a);
        queue.join("r", b);

        assertTrue(matches.isEmpty());
        assertEquals(0, queue.size());
    }
}
