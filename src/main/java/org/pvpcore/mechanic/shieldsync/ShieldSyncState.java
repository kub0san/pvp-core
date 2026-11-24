package org.pvpcore.mechanic.shieldsync;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player Shield Sync state.
 *
 * <p>All fields except the probe fields are normally touched on the main thread.
 * {@link #medianPingMs}, {@link #preciseSampleNanos}, {@link #probeId} and
 * {@link #probeSentNanos} are written by the PacketEvents network thread, so they are
 * volatile.</p>
 */
final class ShieldSyncState {

    /** Median window used by the precise PacketEvents path. */
    final PingWindow window = new PingWindow();

    /** Coalesces the one-tick task that adjusts the active shield use. */
    final AtomicBoolean adjustmentScheduled = new AtomicBoolean();

    /** Median RTT from the probe path, or -1 until the first pong arrives. */
    volatile long medianPingMs = -1L;

    /** When the last probe sample was accepted. */
    volatile long preciseSampleNanos;

    /** Compensation already subtracted from the current shield use, in ticks. */
    volatile int activeUseCompensation;

    /** True when this shield use has already been adjusted. */
    volatile boolean activeUseAdjusted;

    /** PacketEvents probe id currently awaiting a pong, or -1. */
    volatile int probeId = -1;

    /** Nanotime when the current probe was sent. */
    volatile long probeSentNanos;
}
