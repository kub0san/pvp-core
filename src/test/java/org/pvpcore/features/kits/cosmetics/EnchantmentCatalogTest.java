package org.pvpcore.features.kits.cosmetics;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The enchantment catalog must expose registry-backed labels and non-empty icons. */
class EnchantmentCatalogTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void catalogUsesDisplayNamesAndIcons() {
        var entries = EnchantmentCatalog.entries();
        assertFalse(entries.isEmpty());
        entries.forEach(e -> {
            assertNotNull(e.enchantment());
            assertNotNull(e.label());
            assertFalse(e.icon().getType().isAir());
        });
    }

    @Test
    void entriesAreSortedByDisplayName() {
        List<String> labels = EnchantmentCatalog.entries().stream()
                .map(e -> PlainTextComponentSerializer.plainText().serialize(e.label()))
                .toList();
        List<String> sorted = labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        assertEquals(sorted, labels);
    }
}
