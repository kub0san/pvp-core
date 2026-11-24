package org.pvpcore.gui.base;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.StyleManager;

import java.util.Arrays;

/**
 * Quiet professional design language.
 *
 * <ul>
 *   <li>Steel blue is the brand accent; warm sand marks secondary click keys and numbers.</li>
 *   <li>Green = create / success, red = destroy / locked, aqua = preview / info.</li>
 *   <li>Tooltips stay short: name, optional status/body, then at most two action hints.</li>
 *   <li>Stained-glass framing lives on the border (see {@link GuiFrame}); content stays open.</li>
 * </ul>
 *
 * Lore is built straight from MiniMessage (hex colours work, italics are switched off per line).
 */
public final class GuiTheme {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Fallback glass when kits StyleManager is unavailable (e.g. RTP-only). */
    public static final Material DEFAULT_GLASS = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    // ---- palette ----
    public static final String ACCENT = "<#6ea8d9>";
    public static final String GOLD = "<#d4b87a>";
    public static final String TEXT = "<#e8eef5>";
    public static final String MUTED = "<#8b95a8>";
    public static final String FAINT = "<#5c6578>";
    public static final String GREEN = "<#6bcf8e>";
    public static final String RED = "<#e06c75>";
    public static final String AQUA = "<#56b6c2>";

    // ---- legacy aliases so existing callers keep compiling ----
    public static final String WHITE = TEXT;
    public static final String GRAY = MUTED;

    // ---- glyphs ----
    public static final String DOT_ON = "\u25CF";
    public static final String DOT_OFF = "\u25CB";
    public static final String BULLET = "\u2022";
    public static final String ARROW = "\u2014";
    public static final String DIAMOND = "\u25C6";

    private GuiTheme() {
    }

    // ------------------------------------------------------------ builders

    /** The server's configured glass pane, with no tooltip. */
    public static ItemStack filler(StyleManager style) {
        return fillerItem(style != null ? style.getGlassMaterial() : DEFAULT_GLASS);
    }

    /** The server's configured glass pane, with no tooltip. */
    public static ItemStack filler(KitsContext kits) {
        return filler(kits == null ? null : kits.styleManager());
    }

    /** Theme-default glass when no kits style is available. */
    public static ItemStack filler() {
        return fillerItem(DEFAULT_GLASS);
    }

    private static ItemStack fillerItem(Material material) {
        ItemStack item = new ItemStack(material == null ? DEFAULT_GLASS : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!hideTooltip(meta)) {
                meta.displayName(Component.text(" "));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean hideTooltip(ItemMeta meta) {
        meta.setHideTooltip(true);
        return true;
    }

    /** A button with a light name. */
    public static ItemStack item(Material material, String name, String... lore) {
        return named(material, TEXT, name, lore);
    }

    /** A button with a coloured name. */
    public static ItemStack named(Material material, String color, String name, String... lore) {
        return build(material, color + name, lore);
    }

    /** A brand-accent heading button (section / hub entries). */
    public static ItemStack heading(Material material, String name, String... lore) {
        return build(material, ACCENT + "<bold>" + name, lore);
    }

    /** Raw MiniMessage pass-through for user-entered names and descriptions. */
    public static ItemStack custom(Material material, String name, String... lore) {
        return build(material, name, lore);
    }

    // ------------------------------------------------------------ lore vocabulary

    /** Quiet action line: {@code Click — load}. */
    public static String hint(String key, String action) {
        return GOLD + key + " " + FAINT + ARROW + " " + TEXT + action;
    }

    /** Quiet small print. */
    public static String dim(String text) {
        return FAINT + text;
    }

    /** @deprecated Dividers were removed from the quiet lore anatomy; returns empty. */
    @Deprecated
    public static String divider() {
        return "";
    }

    /** "● Ready" style status line. */
    public static String status(String color, boolean on, String text) {
        return color + (on ? DOT_ON : DOT_OFF) + " " + text;
    }

    /** "• Diamond Sword x2" content line. */
    public static String entry(String label, int amount) {
        return MUTED + BULLET + " " + TEXT + label + (amount > 1 ? " " + FAINT + "x" + GOLD + amount : "");
    }

    /** "label: value" line for settings. */
    public static String value(String label, String value) {
        return MUTED + label + FAINT + ": " + GOLD + value;
    }

    // ------------------------------------------------------------ nav items

    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack back() {
        return named(Material.ARROW, MUTED, "Back");
    }

    public static ItemStack arrow(boolean next, int targetPage, int pages) {
        return named(Material.SPECTRAL_ARROW, GOLD, next ? "Next" : "Previous",
                dim("Page " + (targetPage + 1) + " of " + pages));
    }

    /**
     * The single smart pager control. Stack size shows the page number;
     * lore shows progress and one condensed click map.
     */
    public static ItemStack pageItem(int page, int pages) {
        String progress;
        if (pages <= 12) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                if (i > 0) sb.append(' ');
                sb.append(i == page ? ACCENT + DOT_ON : FAINT + DOT_OFF);
            }
            progress = sb.toString();
        } else {
            progress = dim("Page " + (page + 1) + " / " + pages);
        }
        ItemStack item = named(Material.PAPER, TEXT, "Page " + (page + 1) + " of " + pages,
                progress,
                hint("Click / Right / Shift", "next · previous · ends"));
        item.setAmount(Math.max(1, Math.min(64, page + 1)));
        return item;
    }

    /** The material of the item on the cursor, or null. */
    public static Material cursorMaterial(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return null;
        }
        return cursor.getType();
    }

    // ------------------------------------------------------------ chat + tool helpers

    /** Chat message vocabulary: success / warning / failure lines for GuiChatPrompt.say. */
    public static String ok(String text) { return GREEN + "\u2714 " + text; }
    public static String warn(String text) { return GOLD + "\u26A0 " + text; }
    public static String fail(String text) { return RED + "\u2716 " + text; }

    /** A tool button: name, optional about line, one action hint. */
    public static ItemStack tool(Material material, String name, String about, String key, String action) {
        if (about == null || about.isBlank()) {
            return named(material, TEXT + "<bold>", name, hint(key, action));
        }
        return named(material, TEXT + "<bold>", name, dim(about), hint(key, action));
    }

    /** Same as {@link #tool} but red, for destructive actions. */
    public static ItemStack dangerTool(Material material, String name, String about, String key, String action) {
        if (about == null || about.isBlank()) {
            return named(material, RED + "<bold>", name, hint(key, action));
        }
        return named(material, RED + "<bold>", name, dim(about), hint(key, action));
    }

    // ------------------------------------------------------------ internals

    private static ItemStack build(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null && !name.isEmpty()) {
            meta.displayName(MM.deserialize("<!italic>" + name));
        }
        if (lore != null && lore.length > 0) {
            meta.lore(Arrays.stream(lore)
                    .filter(line -> line != null && !line.isEmpty())
                    .map(line -> MM.deserialize("<!italic>" + line))
                    .toList());
        }
        // Menu buttons only: never show vanilla attribute / enchant text on a button.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
