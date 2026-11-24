package org.pvpcore.features.kits;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

class KitCacheTest {

    /** Regression for C2-09: async snapshot readers must not race public-kit writes. */
    @Test
    void publicKitListCanBeReadWhileAnotherThreadWrites() throws Exception {
        KitCache cache = new KitCache();
        for (int i = 0; i < 5000; i++) {
            cache.registerPublicKit(new PublicKit("kit-" + i, "Kit " + i, Material.DIAMOND_SWORD));
        }

        AtomicBoolean readerDone = new AtomicBoolean();
        CountDownLatch writerStarted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                int i = 0;
                while (!readerDone.get()) {
                    PublicKit kit = new PublicKit("extra-" + i++, "Extra", Material.DIAMOND_SWORD);
                    cache.registerPublicKit(kit);
                    writerStarted.countDown();
                    cache.removePublicKit(kit);
                    Thread.yield();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        writer.start();
        writerStarted.await(5, TimeUnit.SECONDS);

        try {
            for (int i = 0; i < 100; i++) {
                assertDoesNotThrow(() -> {
                    List<PublicKit> snapshot = cache.getPublicKitList();
                    int count = 0;
                    for (PublicKit kit : snapshot) {
                        if (kit.id == null) {
                            throw new AssertionError("null kit id in snapshot");
                        }
                        count++;
                        if ((count & 0x3F) == 0) {
                            Thread.onSpinWait();
                        }
                    }
                }, "iterating the public-kit list during a concurrent write must not throw");
            }
        } finally {
            readerDone.set(true);
        }
        writer.join(10_000);
        assertNull(failure.get(), "writer thread failed: " + failure.get());
    }
}
