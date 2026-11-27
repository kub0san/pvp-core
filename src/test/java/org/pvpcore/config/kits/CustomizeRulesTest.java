package org.pvpcore.config.kits;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizeRulesTest {
    @Test void defaultsAndEmptyAllowlists() {
        CustomizeRules rules = CustomizeRules.parse(null);
        assertTrue(rules.enabled());
        assertFalse(rules.allowClickEvents());
        assertFalse(rules.allowHoverEvents());
        assertEquals(256, rules.maxNameLength());
        assertTrue(rules.allowsTrimPattern("minecraft:coast"));
    }

    @Test void clampsKnownValuesAndWarnsOnUnknownKey() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("max-name-length", 99999);
        section.set("allowed-trim-patterns", List.of("minecraft:coast", "not a key"));
        section.set("bogus", true);
        List<String> warnings = new ArrayList<>();
        CustomizeRules rules = CustomizeRules.parse(section, warnings::add);
        assertEquals(1024, rules.maxNameLength());
        assertTrue(rules.allowsTrimPattern("minecraft:coast"));
        assertFalse(rules.allowsTrimPattern("minecraft:eye"));
        assertEquals(2, warnings.size());
    }

    @Test void normalizesKeysAndHonorsToggles() {
        MemoryConfiguration section = new MemoryConfiguration();
        section.set("enabled", false);
        section.set("allow-click-events", true);
        section.set("allow-hover-events", true);
        section.set("max-name-length", 64);
        section.set("allowed-trim-materials", List.of("MINECRAFT:EMERALD"));
        List<String> warnings = new ArrayList<>();
        CustomizeRules rules = CustomizeRules.parse(section, warnings::add);

        assertFalse(rules.enabled());
        assertTrue(rules.allowClickEvents());
        assertTrue(rules.allowHoverEvents());
        assertEquals(64, rules.maxNameLength());
        assertTrue(rules.allowedTrimMaterials().contains("minecraft:emerald"));
        assertTrue(rules.allowsTrimMaterial("minecraft:emerald"));
        assertFalse(rules.allowsTrimMaterial("minecraft:diamond"));
        assertTrue(warnings.isEmpty());
    }
}
