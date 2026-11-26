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

    /**
     * Resolves the registry-backed pattern for a catalog key so a GUI selection can be handed to
     * {@link CosmeticApplier#applyTrim}. Returns null when the registry is unavailable or the key
     * is unknown; callers must fail closed rather than guess an object.
     */
    public static TrimPattern trimPattern(String key) {
        return keyed(RegistryKey.TRIM_PATTERN, key);
    }

    /** Registry-backed material counterpart of {@link #trimPattern(String)}. */
    public static TrimMaterial trimMaterial(String key) {
        return keyed(RegistryKey.TRIM_MATERIAL, key);
    }

    private static <T extends Keyed> T keyed(RegistryKey<T> registryKey, String id) {
        if (id == null || id.isBlank()) return null;
        Registry<T> registry = registry(registryKey);
        if (registry == null) return null;
        for (T value : registry) {
            try {
                if (value.getKey() != null && id.equals(value.getKey().toString())) return value;
            } catch (RuntimeException e) {
                // One malformed registry entry must not hide the rest.
            }
        }
        return null;
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
        return new ItemStack(vanillaTemplateMaterial(key));
    }

    private static ItemStack materialIcon(NamespacedKey key) {
        return new ItemStack(vanillaTrimMaterial(key));
    }

    /**
     * Vanilla trim-material registry paths are not Bukkit material names:
     * {@code minecraft:iron} must render as {@code IRON_INGOT}, for example.
     * Only the {@code minecraft} namespace uses the explicit, compile-checked
     * mapping; other namespaces keep the best-effort
     * {@link Material#matchMaterial(String)} lookup. {@link Material#PAPER} is
     * the deliberate "no known icon" placeholder, not an entry's real item.
     */
    static Material vanillaTrimMaterial(NamespacedKey key) {
        Material material = null;
        if ("minecraft".equals(key.getNamespace())) {
            material = switch (key.getKey()) {
                case "iron" -> Material.IRON_INGOT;
                case "gold" -> Material.GOLD_INGOT;
                case "copper" -> Material.COPPER_INGOT;
                case "netherite" -> Material.NETHERITE_INGOT;
                case "lapis" -> Material.LAPIS_LAZULI;
                case "amethyst" -> Material.AMETHYST_SHARD;
                case "diamond" -> Material.DIAMOND;
                case "emerald" -> Material.EMERALD;
                case "quartz" -> Material.QUARTZ;
                case "redstone" -> Material.REDSTONE;
                case "resin" -> Material.RESIN_BRICK;
                default -> null;
            };
        }
        if (material == null) material = item(key.getNamespace(), key.getKey());
        return material == null ? Material.PAPER : material;
    }

    /**
     * Vanilla trim-pattern registry paths are not Bukkit material names; each of
     * the 18 vanilla patterns maps to its compile-checked smithing-template item.
     * The old {@code _smithing_template} / {@code _smithery_template} guesses are
     * removed because neither name exists as a Bukkit material. Other namespaces
     * keep the best-effort {@link Material#matchMaterial(String)} lookup, and
     * {@link Material#PAPER} remains the "no known icon" placeholder.
     */
    static Material vanillaTemplateMaterial(NamespacedKey key) {
        Material template = null;
        if ("minecraft".equals(key.getNamespace())) {
            template = switch (key.getKey()) {
                case "bolt" -> Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "coast" -> Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "dune" -> Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "eye" -> Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "flow" -> Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "host" -> Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "raiser" -> Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "rib" -> Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "sentry" -> Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "shaper" -> Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "silence" -> Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "snout" -> Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "spire" -> Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "tide" -> Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "vex" -> Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "ward" -> Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "wayfinder" -> Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE;
                case "wild" -> Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
                default -> null;
            };
        }
        if (template == null) template = item(key.getNamespace(), key.getKey());
        return template == null ? Material.PAPER : template;
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
