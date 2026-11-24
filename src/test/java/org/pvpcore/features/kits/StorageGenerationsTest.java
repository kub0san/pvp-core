package org.pvpcore.features.kits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for DB2-7: a snapshot taken before a write must be rejected. */
class StorageGenerationsTest {

    @Test
    void writeInvalidatesSnapshotsOfThatIdOnly() {
        StorageGenerations generations = new StorageGenerations();
        long before = generations.get("playerkit-1");
        long other = generations.get("playerkit-2");

        generations.bump("playerkit-1");

        assertFalse(generations.isCurrent("playerkit-1", before));
        assertTrue(generations.isCurrent("playerkit-2", other));
        assertTrue(generations.isCurrent("playerkit-1", generations.get("playerkit-1")));
    }
}
