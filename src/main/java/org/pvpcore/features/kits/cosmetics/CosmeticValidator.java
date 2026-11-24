package org.pvpcore.features.kits.cosmetics;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BannerPatternLayers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ShieldMeta;
import org.pvpcore.config.kits.CustomizeRules;

import java.util.Objects;

/** Validation for cosmetic inputs: item types, strict name tags and banner readability. */
public final class CosmeticValidator {
    /** Distinguishes a rejected formatting tag from other malformed names at the call site. */
    public static final class BlockedTagException extends IllegalArgumentException {
        BlockedTagException(String message) {
            super(message);
        }

        BlockedTagException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final CustomizeRules rules;

    public CosmeticValidator(CustomizeRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * Parses raw chat text into a component. Only colour, decorations and gradient are
     * enabled by default; click and hover are opt-in. Every other tag is rejected by
     * the allowlisted resolver. The raw length is checked before parsing.
     */
    public Component parseName(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Name must not be null");
        }
        if (raw.length() > rules.maxNameLength()) {
            throw new IllegalArgumentException("Name exceeds " + rules.maxNameLength() + " characters");
        }

        TagResolver.Builder tags = TagResolver.builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.gradient());
        if (rules.allowClickEvents()) {
            tags.resolver(StandardTags.clickEvent());
        }
        if (rules.allowHoverEvents()) {
            tags.resolver(StandardTags.hoverEvent());
        }

        // MiniMessage leaves unresolved tags as literal text even in strict mode, so wrap
        // the allowlisted resolvers and reject anything they cannot resolve.
        TagResolver allowed = tags.build();
        TagResolver rejecting = new TagResolver() {
            @Override
            public Tag resolve(String name, ArgumentQueue arguments, Context context) {
                Tag tag = allowed.resolve(name, arguments, context);
                if (tag == null) {
                    throw new BlockedTagException("Cosmetic name tag is not allowed: " + name);
                }
                return tag;
            }

            @Override
            public boolean has(String name) {
                return allowed.has(name);
            }
        };

        MiniMessage miniMessage = MiniMessage.builder().tags(rejecting).strict(true).build();
        try {
            return miniMessage.deserialize(raw);
        } catch (RuntimeException e) {
            // The parser may wrap a resolver failure; keep the blocked-tag signal intact.
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof BlockedTagException blocked) throw blocked;
            }
            if (e instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException("Invalid cosmetic name", e);
        }
    }

    public boolean isArmor(ItemStack item) {
        return item != null && item.getItemMeta() instanceof ArmorMeta;
    }

    public boolean isShield(ItemStack item) {
        return item != null && item.getItemMeta() instanceof ShieldMeta;
    }

    /**
     * True when the raw {@code BannerPatternLayers} component holds more layers than
     * Bukkit can expose via {@link BannerMeta#getPatterns()}. Any failure to read either
     * side is treated as unsafe so callers fail closed instead of silently stripping data.
     */
    public boolean hasUnreadableBannerLayers(ItemStack item) {
        if (item == null) return true;
        try {
            BannerPatternLayers raw = item.getData(DataComponentTypes.BANNER_PATTERNS);
            int rawLayers = raw == null ? 0 : raw.patterns().size();
            ItemMeta meta = item.getItemMeta();
            int readableLayers = meta instanceof BannerMeta banner ? banner.getPatterns().size() : 0;
            return unreadableLayerCount(rawLayers, readableLayers) > 0;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Pure helper: how many raw layers Bukkit cannot represent. */
    public int unreadableLayerCount(int rawLayers, int readableLayers) {
        return Math.max(0, rawLayers - readableLayers);
    }
}
