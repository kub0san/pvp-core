package org.pvpcore.gui.kits.editor;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorHintLoreTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void stripRemovesHintButKeepsNameAndLore() {
        ItemStack item = named("Sword", "Original lore");
        ItemStack display = EditorHintLore.apply(item.clone());
        assertTrue(EditorHintLore.has(display));

        EditorHintLore.strip(display);

        assertFalse(EditorHintLore.has(display));
        assertEquals("Sword", display.getItemMeta().getDisplayName());
        assertEquals(List.of("Original lore"), display.getItemMeta().getLore());
    }

    @Test
    void applyIsIdempotent() {
        ItemStack display = EditorHintLore.apply(named("Sword", "Original lore"));

        EditorHintLore.apply(display);
        EditorHintLore.apply(display);

        assertEquals(4, display.getItemMeta().lore().size());
        EditorHintLore.strip(display);
        assertEquals(1, display.getItemMeta().lore().size());
    }

    @Test
    void plainItemsHaveNoHintsAndAreNotChangedByStrip() {
        ItemStack plain = named("Sword", "Original lore", "Edit: Shift-Click");

        assertFalse(EditorHintLore.has(plain));
        EditorHintLore.strip(plain);

        assertEquals(List.of("Original lore", "Edit: Shift-Click"), plain.getItemMeta().getLore());
    }

    @Test
    void stripRemovesOnlyTheExactHintLines() {
        ItemStack item = named("Sword", "Original lore", "Edit: something else");
        ItemStack display = EditorHintLore.apply(item);

        EditorHintLore.strip(display);

        assertEquals(List.of("Original lore", "Edit: something else"), display.getItemMeta().getLore());
    }

    private static ItemStack named(String name, String... lore) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }
}
