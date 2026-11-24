package org.pvpcore.mechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for #3: declared version ranges must actually be enforced. */
class MechanicRegistryVersionRangeTest {

    @Test
    void acceptsCurrentVersionInsideRange() {
        assertTrue(MechanicRegistry.isVersionInDeclaredRange("1.21.11..26.3", "26.3"));
        assertTrue(MechanicRegistry.isVersionInDeclaredRange("..26.3", "26.2"));
        assertTrue(MechanicRegistry.isVersionInDeclaredRange("26.2..", "26.3"));
    }

    @Test
    void rejectsVersionsOutsideRange() {
        assertFalse(MechanicRegistry.isVersionInDeclaredRange("1.21.11..26.2", "26.3"));
        assertFalse(MechanicRegistry.isVersionInDeclaredRange("26.4..", "26.3"));
        assertFalse(MechanicRegistry.isVersionInDeclaredRange("..26.2", "26.3"));
        assertFalse(MechanicRegistry.isVersionInDeclaredRange("26.4", "26.3"));
    }
}
