package org.pvpcore.features.kits;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitRecordStoreTest {

    /** Regression for #7: "error" is valid stored data, not a missing-kit marker. */
    @Test
    void errorStringIsValidStoredData() {
        assertTrue(KitRecordStore.isStoredData("error"));
        assertTrue(KitRecordStore.isStoredData("whatever"));
        assertFalse(KitRecordStore.isStoredData(null));
        assertFalse(KitRecordStore.isStoredData(KitRecordStore.DELETED));
    }
}
