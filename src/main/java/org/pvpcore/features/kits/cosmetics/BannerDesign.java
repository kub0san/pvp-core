package org.pvpcore.features.kits.cosmetics;

import org.bukkit.DyeColor;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ShieldMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a banner/shield design.
 *
 * <p>The original {@link Pattern} objects are retained so that resolvable data-pack
 * patterns and inline keyless patterns are carried forward untouched on a no-op write.</p>
 */
public final class BannerDesign {

    /** One dye/pattern layer of the design. */
    public record Layer(DyeColor dye, PatternType pattern) {
        public Layer {
            Objects.requireNonNull(dye, "dye");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    // Nullable on purpose: a fresh shield has no base colour and must stay that way.
    private final DyeColor baseColor;
    private final List<Pattern> patterns;

    private BannerDesign(DyeColor baseColor, List<Pattern> patterns) {
        this.baseColor = baseColor;
        this.patterns = List.copyOf(patterns);
    }

    /** Reads a design from a banner or shield, keeping its original pattern objects. */
    public static BannerDesign from(ItemStack bannerOrShield) {
        Objects.requireNonNull(bannerOrShield, "bannerOrShield");
        ItemMeta meta = bannerOrShield.getItemMeta();
        if (!(meta instanceof BannerMeta bannerMeta)) {
            throw new IllegalArgumentException("Item is not a banner or shield");
        }
        DyeColor base = meta instanceof ShieldMeta shieldMeta ? shieldMeta.getBaseColor() : null;
        return new BannerDesign(base, bannerMeta.getPatterns());
    }

    /** An empty design: no layers and a null base colour. */
    public static BannerDesign blank() {
        return new BannerDesign(null, List.of());
    }

    /** The raw shield base colour; {@code null} means "not set" and renders white. */
    public DyeColor baseColor() {
        return baseColor;
    }

    /** Immutable layer view, in render order. */
    public List<Layer> layers() {
        return patterns.stream()
                .map(pattern -> new Layer(pattern.getColor(), pattern.getPattern()))
                .toList();
    }

    public int size() {
        return patterns.size();
    }

    /** Returns a copy with the given base colour; {@code null} clears it. */
    public BannerDesign withBaseColor(DyeColor dye) {
        return new BannerDesign(dye, patterns);
    }

    /** Appends a layer while the design is below {@code maxLayers}; otherwise unchanged. */
    public BannerDesign addLayer(Layer layer, int maxLayers) {
        Objects.requireNonNull(layer, "layer");
        if (patterns.size() >= maxLayers) return this;
        List<Pattern> copy = new ArrayList<>(patterns);
        copy.add(new Pattern(layer.dye(), layer.pattern()));
        return new BannerDesign(baseColor, copy);
    }

    public BannerDesign removeLayer(int index) {
        List<Pattern> copy = new ArrayList<>(patterns);
        copy.remove(index);
        return new BannerDesign(baseColor, copy);
    }

    public BannerDesign moveLayer(int from, int to) {
        List<Pattern> copy = new ArrayList<>(patterns);
        Pattern moved = copy.remove(from);
        copy.add(to, moved);
        return new BannerDesign(baseColor, copy);
    }

    /**
     * Writes this design onto a copy of {@code shield}. Metadata is only changed when it
     * actually differs, so a no-op never adds a base-colour component to a fresh shield.
     */
    public ItemStack applyTo(ItemStack shield) {
        Objects.requireNonNull(shield, "shield");
        if (!(shield.getItemMeta() instanceof ShieldMeta)) {
            throw new IllegalArgumentException("Banner target must be a shield");
        }
        ItemStack result = shield.clone();
        ShieldMeta target = (ShieldMeta) result.getItemMeta();
        if (!target.getPatterns().equals(patterns)) {
            target.setPatterns(patterns);
        }
        if (!Objects.equals(target.getBaseColor(), baseColor)) {
            target.setBaseColor(baseColor);
        }
        result.setItemMeta(target);
        return result;
    }
}
