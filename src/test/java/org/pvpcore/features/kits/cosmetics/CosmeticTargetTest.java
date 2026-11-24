package org.pvpcore.features.kits.cosmetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticTargetTest {
    @Test
    void targetMappingMatchesKitContents() {
        assertEquals(36, CosmeticTarget.BOOTS.index());
        assertEquals(40, CosmeticTarget.SHIELD.index());
    }
}