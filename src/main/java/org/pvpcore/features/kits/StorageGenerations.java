package org.pvpcore.features.kits;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-storage-id generation counters. A read captures a generation; any queued write bumps it,
 * so a late-arriving snapshot can be rejected before it overwrites newer cached state.
 * Instance-scoped (no new statics, per ADR 0008).
 */
final class StorageGenerations {
    private final ConcurrentHashMap<String, AtomicLong> values = new ConcurrentHashMap<>();

    long get(String id) {
        return values.computeIfAbsent(id, key -> new AtomicLong()).get();
    }

    long bump(String id) {
        return values.computeIfAbsent(id, key -> new AtomicLong()).incrementAndGet();
    }

    boolean isCurrent(String id, long captured) {
        return get(id) == captured;
    }
}
