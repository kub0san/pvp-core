package org.pvpcore.features.kits;

import java.util.concurrent.CompletableFuture;

/**
 * Outcome of an explicit asynchronous kit save. A rejected request has no storage work to wait
 * for; an accepted one carries the write future so callers can report completion without polling.
 */
public record KitSaveResult(boolean accepted, CompletableFuture<Void> write) {

    public KitSaveResult {
        if (write == null) write = CompletableFuture.completedFuture(null);
    }

    /** Rejected request: validation failed before anything was queued. */
    public static KitSaveResult rejected() {
        return new KitSaveResult(false, null);
    }
}
