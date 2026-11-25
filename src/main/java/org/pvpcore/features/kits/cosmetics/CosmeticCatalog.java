package org.pvpcore.features.kits.cosmetics;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.pvpcore.config.kits.CustomizeRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Immutable catalog of the cosmetic options offered by the kit GUI.
 *
 * <p>Entries are built once from the server registries; the GUI only renders the
 * allowlist-filtered views. When a registry is unavailable (unit tests, early startup)
 * that view is simply empty rather than throwing.</p>
 */
public final class CosmeticCatalog {

    /** One selectable cosmetic option. Identity is the registry key string. */
    public record Entry(String key, String label, ItemStack icon) {
        public Entry {
            Objects.requireNonNull(key, "key");
            if (label == null || label.isBlank()) label = "unknown";
            icon = icon == null ? new ItemStack(Material.PAPER) : icon;
        }
    }

    private final List<Entry> patterns;
    private final List<Entry> materials;
    private final List<Entry> banners;
    private final CustomizeRules rules;

    /** Package-private seam for deterministic tests. */
    CosmeticCatalog(List<Entry> patterns, List<Entry> materials, List<Entry> banners, CustomizeRules rules) {
        this.patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
        this.materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        this.banners = List.copyOf(Objects.requireNonNull(banners, "banners"));
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /** Reads all three registries; an unavailable registry contributes an empty list. */
    public static CosmeticCatalog fromRegistries(CustomizeRules rules) {
        Objects.requireNonNull(rules, "rules");
        return new CosmeticCatalog(
                entries(RegistryKey.TRIM_PATTERN, CosmeticCatalog::patternLabel, CosmeticCatalog::templateIcon),
                entries(RegistryKey.TRIM_MATERIAL, CosmeticCatalog::materialLabel, CosmeticCatalog::materialIcon),
                entries(RegistryKey.BANNER_PATTERN,
                        (value, key) -> humanReadable(key), key -> new ItemStack(Material.PAPER)),
                rules);
    }

    public List<Entry> trimPatterns() {
        return filter(patterns, rules::allowsTrimPattern);
    }

    public List<Entry> trimMaterials() {
        return filter(materials, rules::allowsTrimMaterial);
    }

    public List<Entry> bannerPatterns() {
        return filter(banners, rules::allowsBannerPattern);
    }

    private static List<Entry> filter(List<Entry> entries, Predicate<String> allowed) {
        return entries.stream().filter(entry -> allowed.test(entry.key())).toList();
    }

    private static <T extends Keyed> List<Entry> entries(
            RegistryKey<T> registryKey, Labeler<T> labeler, Function<NamespacedKey, ItemStack> iconFactory) {
        Registry<T> registry = registry(registryKey);
        if (registry == null) return List.of();
        List<Entry> result = new ArrayList<>();
        for (T value : registry) {
            try {
                NamespacedKey key = value.getKey();
                if (key == null) continue;
                String id = key.toString();
                result.add(new Entry(id, labeler.label(value, id), iconFactory.apply(key)));
            } catch (RuntimeException e) {
                // A single malformed registry entry must not hide the rest of the catalog.
            }
        }
        return List.copyOf(result);
    }

    private static <T extends Keyed> Registry<T> registry(RegistryKey<T> registryKey) {
        try {
            RegistryAccess access = RegistryAccess.registryAccess();
            return access == null ? null : access.getRegistry(registryKey);
        } catch (RuntimeException e) {
            // No running server (unit tests) or an otherwise unavailable registry.
            return null;
        }
    }

    private static String patternLabel(TrimPattern pattern, String keyString) {
        return describedLabel(pattern.description(), keyString);
    }

    private static String materialLabel(TrimMaterial material, String keyString) {
        return describedLabel(material.description(), keyString);
    }

    /** Registry descriptions are usually untranslated keys; use the readable key in that case. */
    private static String describedLabel(Component description, String keyString) {
        if (description != null) {
            try {
                String text = PlainTextComponentSerializer.plainText().serialize(description);
                String translationKey = description instanceof TranslatableComponent translatable
                        ? translatable.key() : null;
                if (text != null && !text.isBlank() && !text.equals(translationKey)) return text;
            } catch (RuntimeException e) {
                // Fall through to the key-derived label.
            }
        }
        return humanReadable(keyString);
    }

    /** Turns "minecraft:diagonal_left" into "Diagonal Left"; blank keys become "unknown". */
    private static String humanReadable(String keyString) {
        if (keyString == null || keyString.isBlank()) return "unknown";
        int colon = keyString.indexOf(':');
        String path = (colon >= 0 ? keyString.substring(colon + 1) : keyString).replace('_', ' ').trim();
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

    private static ItemStack templateIcon(NamespacedKey key) {
        Material template = item(key.getNamespace(), key.getKey() + "_smithing_template");
        if (template == null) template = item(key.getNamespace(), key.getKey() + "_smithery_template");
        return new ItemStack(template == null ? Material.PAPER : template);
    }

    private static ItemStack materialIcon(NamespacedKey key) {
        Material material = item(key.getNamespace(), key.getKey());
        return new ItemStack(material == null ? Material.PAPER : material);
    }

    private static Material item(String namespace, String path) {
        try {
            Material material = Material.matchMaterial(namespace + ":" + path);
            return material != null && material.isItem() ? material : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface Labeler<T> {
        String label(T value, String keyString);
    }
}
