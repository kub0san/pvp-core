package org.pvpcore.mechanic.shieldsync;

import java.util.Arrays;

/**
 * Median of the most recent ping samples.
 *
 * <p>Five samples are enough to ignore a single delayed pong while still following a
 * sustained latency change within a few probes. The median is deliberately preferred
 * over the reference plugin's "reject samples more than 80 ms above the current value"
 * filter, which can never accept a real, lasting ping increase.</p>
 */
final class PingWindow {

    private static final int SIZE = 5;

    private final long[] samples = new long[SIZE];
    private int count;
    private int next;

    /** Adds one round-trip sample and returns the current median in milliseconds. */
    synchronized long add(long milliseconds) {
        long sample = Math.max(0L, milliseconds);
        samples[next] = sample;
        next = (next + 1) % SIZE;
        count = Math.min(SIZE, count + 1);

        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        return sorted[count / 2];
    }
}
