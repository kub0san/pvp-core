package org.pvpcore.storage;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Orders kit writes, deletes and subsequent join reads; drains before storage closes. */
public final class StorageWorkQueue implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Kits-storage");
        thread.setDaemon(true);
        return thread;
    });
    private final Logger logger;

    public StorageWorkQueue(Logger logger) { this.logger = logger; }

    public CompletableFuture<Void> run(Runnable work) {
        return supply(() -> { work.run(); return null; });
    }

    public <T> CompletableFuture<T> supply(Supplier<T> work) {
        return CompletableFuture.supplyAsync(work, executor).whenComplete((value, error) -> {
            if (error == null) return;
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            logger.log(Level.SEVERE, "Storage operation failed: " + cause.getMessage(), cause);
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            // Wait max 5 seconds for queued writes to finish
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // Force kill
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
