package org.pvpcore.features.kits.cosmetics;

import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, sorted view of the server's enchantment registry for the kit item editor.
 *
 * <p>A registry may be unavailable during early startup or in unit tests; in that case
 * the catalog is empty rather than throwing. The GUI reads this once per render, so a
 * single malformed registry entry can never hide the rest of the list.</p>
 */
public final class EnchantmentCatalog {

    /** One selectable enchantment: registry object, level-one display name and list icon. */
    public record Entry(Enchantment enchantment, Component label, ItemStack icon) {
        public Entry {
            Objects.requireNonNull(enchantment, "enchantment");
            Objects.requireNonNull(label, "label");
            if (icon == null || icon.getType().isAir()) icon = new ItemStack(Material.ENCHANTED_BOOK);
        }
    }

    private EnchantmentCatalog() {
    }

    /** All registry enchantments, ordered by their plain level-one display name. */
    public static List<Entry> entries() {
        Registry<Enchantment> registry;
        try {
            registry = Bukkit.getRegistry(Enchantment.class);
        } catch (RuntimeException | LinkageError ex) {
            return List.of();
        }
        if (registry == null) return List.of();

        List<Entry> entries = new ArrayList<>();
        for (Enchantment enchantment : registry) {
            try {
                Component label = enchantment.displayName(1);
                if (label == null) continue;
                entries.add(new Entry(enchantment, label, icon(enchantment)));
            } catch (RuntimeException | LinkageError ex) {
                // One broken registry entry must not hide every other enchantment.
            }
        }
        entries.sort(Comparator.comparing(entry -> plain(entry.label()), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    /** Plain text of a display component; empty when serialization is unavailable. */
    public static String plain(Component component) {
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * Uses the first supported item as the icon, falling back to an enchanted book when
     * the enchantment has no supported items or none resolves to a known material.
     */
    private static ItemStack icon(Enchantment enchantment) {
        try {
            for (TypedKey<org.bukkit.inventory.ItemType> supported : enchantment.getSupportedItems().values()) {
                Material material = material(supported.key().asString());
                if (material != null && material.isItem() && !material.isAir()) {
                    return new ItemStack(material);
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            // Fall through to the book icon.
        }
        return new ItemStack(Material.ENCHANTED_BOOK);
    }

    private static Material material(String registryKey) {
        String name = registryKey;
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        try {
            return Material.matchMaterial(name);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
