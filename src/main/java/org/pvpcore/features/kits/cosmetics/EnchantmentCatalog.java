package org.pvpcore.features.kits.cosmetics;

import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable, sorted view of the server's enchantment registry for the kit item editor.
 *
 * <p>A registry may be unavailable during early startup or in unit tests; in that case
 * the catalog is empty rather than throwing. An instance may also be constructed with
 * an explicit enchantment collection, which is the injection seam used by tests and by
 * screens that already hold a registry snapshot. No mutable static state is involved.</p>
 */
public final class EnchantmentCatalog {

    /**
     * One selectable enchantment. Identity is the stable namespaced key, never the display
     * component: translations and admin overrides can change the label at runtime.
     */
    public record Entry(String key, Enchantment enchantment, Component label, Component description,
                        int vanillaMax, String category) {
        public Entry {
            Objects.requireNonNull(enchantment, "enchantment");
            key = key == null || key.isBlank() ? "unknown/unknown" : key;
            label = label == null ? Component.empty() : label;
            description = description == null ? Component.text("No description available") : description;
            category = category == null || category.isBlank() ? "Unknown" : category;
        }

        /** Plain enchanted-book icon, used when no level is selected. */
        public ItemStack icon() {
            return icon(0);
        }

        /**
         * Enchanted-book icon carrying the selected level as a stored enchant. The stored
         * enchant is what players need to see, so no {@code HIDE_ENCHANTS} flag is added.
         */
        public ItemStack icon(int level) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            if (level <= 0) return book;
            try {
                ItemMeta meta = book.getItemMeta();
                if (meta instanceof EnchantmentStorageMeta storage) {
                    storage.addStoredEnchant(enchantment, level, true);
                    book.setItemMeta(storage);
                }
            } catch (RuntimeException | LinkageError ignored) {
                // A malformed enchantment still gets a usable plain-book icon.
            }
            return book;
        }
    }

    private final Collection<Enchantment> injected;

    /** Live-registry catalog; safe to construct at startup when the registry is absent. */
    public EnchantmentCatalog() {
        this(null);
    }

    /**
     * Catalog over a fixed enchantment collection. A null collection means "read the live
     * Bukkit registry"; an empty collection is a genuinely empty catalog.
     */
    public EnchantmentCatalog(Collection<Enchantment> injected) {
        this.injected = injected;
    }

    /** All registry enchantments, ordered by human label with the key as tie-break. */
    public static List<Entry> entries() {
        return new EnchantmentCatalog().getEntries();
    }

    /** Entries from this catalog's registry snapshot or injected collection. */
    public List<Entry> getEntries() {
        if (injected != null) return collect(injected);
        Registry<Enchantment> registry;
        try {
            registry = Bukkit.getRegistry(Enchantment.class);
        } catch (RuntimeException | LinkageError ex) {
            return List.of();
        }
        return registry == null ? List.of() : collect(registry);
    }

    private static List<Entry> collect(Iterable<Enchantment> source) {
        List<Entry> entries = new ArrayList<>();
        try {
            for (Enchantment enchantment : source) {
                if (enchantment == null) continue;
                try {
                    Component display;
                    try {
                        display = enchantment.displayName(1);
                    } catch (RuntimeException | LinkageError ex) {
                        continue; // A malformed registry entry is skipped, not partially rendered.
                    }
                    String key = keyOf(enchantment);
                    entries.add(new Entry(key, enchantment, labelFor(display, key),
                            descriptionFor(enchantment), vanillaMax(enchantment), categoryFor(enchantment, key)));
                } catch (RuntimeException | LinkageError ex) {
                    // One broken registry entry must not hide every other enchantment.
                }
            }
        } catch (RuntimeException | LinkageError ex) {
            // A broken registry iterator yields a partial (possibly empty) catalog.
        }
        entries.sort(Comparator.<Entry, String>comparing(entry -> plain(entry.label()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::key));
        return List.copyOf(entries);
    }

    /** Plain text of a display component; empty when serialization is unavailable. */
    public static String plain(Component component) {
        if (component == null) return "";
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static String keyOf(Enchantment enchantment) {
        try {
            NamespacedKey key = enchantment.getKey();
            if (key != null) return key.toString();
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the legacy name.
        }
        try {
            String name = enchantment.getName();
            if (name != null && !name.isBlank()) {
                return "unknown/" + name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the stable sentinel.
        }
        return "unknown/unknown";
    }

    private static Component labelFor(Component display, String key) {
        String text = plain(display);
        if (display != null && !text.isBlank() && !translationLike(display, text)) return display;
        return Component.text(humanReadable(key));
    }

    private static boolean translationLike(Component component, String plainText) {
        if (component instanceof TranslatableComponent translatable && plainText.equals(translatable.key())) {
            return true;
        }
        return plainText.matches("[a-z0-9_/-]+(\\.[a-z0-9_/-]+)+");
    }

    private static Component descriptionFor(Enchantment enchantment) {
        try {
            Component description = enchantment.description();
            if (description != null && !plain(description).isBlank()) return description;
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to the safe fallback.
        }
        return Component.text("No description available");
    }

    /** Vanilla ceiling from the registry, never from a GUI-specific heuristic. */
    private static int vanillaMax(Enchantment enchantment) {
        try {
            return Math.max(1, enchantment.getMaxLevel());
        } catch (RuntimeException | LinkageError ignored) {
            return 1;
        }
    }

    /**
     * Derives a deterministic category from the enchantment's primary items when available,
     * otherwise from the key namespace. Never reimplements the trim icon mapping.
     */
    private static String categoryFor(Enchantment enchantment, String key) {
        try {
            var primary = enchantment.getPrimaryItems();
            if (primary != null) {
                for (TypedKey<org.bukkit.inventory.ItemType> item : primary.values()) {
                    String path = item.key().asString();
                    int colon = path.indexOf(':');
                    String readable = humanReadable(colon >= 0 ? path.substring(colon + 1) : path);
                    if (!readable.isBlank()) return readable;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // MockBukkit and custom enchantments may not emulate primary items.
        }
        int colon = key.indexOf(':');
        return humanReadable(colon >= 0 ? key.substring(0, colon) : key);
    }

    /** Turns "test-mod:wrap_around" into "Wrap Around"; blank keys become "unknown". */
    static String humanReadable(String keyString) {
        if (keyString == null || keyString.isBlank()) return "unknown";
        int colon = keyString.indexOf(':');
        String path = (colon >= 0 ? keyString.substring(colon + 1) : keyString)
                .replace('_', ' ').replace('-', ' ').trim();
        if (path.isBlank()) return "unknown";
        StringBuilder out = new StringBuilder(path.length());
        boolean capitalize = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            out.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = c == ' ';
        }
        return out.toString();
    }
}
