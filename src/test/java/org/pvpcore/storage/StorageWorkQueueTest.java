package org.pvpcore.storage;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageWorkQueueTest {

    /** Regression for DB2-8: close() must drain queued writes instead of killing them. */
    @Test
    void closeDrainsQueuedWrites() {
        StorageWorkQueue queue = new StorageWorkQueue(Logger.getLogger("test"));
        AtomicInteger completed = new AtomicInteger();
        for (int i = 0; i < 15; i++) {
            queue.run(() -> {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                completed.incrementAndGet();
            });
        }

        queue.close();

        assertEquals(15, completed.get(), "all queued storage writes must finish before close returns");
    }
}
