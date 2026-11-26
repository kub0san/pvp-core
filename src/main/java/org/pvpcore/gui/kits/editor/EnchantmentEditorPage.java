package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.ItemFilter;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.features.kits.cosmetics.EnchantmentCatalog;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiPager;
import org.pvpcore.gui.base.GuiPreviewItems;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Compact three-row enchantment editor for one kit item.
 *
 * <p>Clicks build a private draft: left-click advances one level and stays at the effective
 * maximum, right-click removes it. Every change is checked against the kit item filter before
 * the draft accepts it. Nothing reaches the {@link CustomizeSession} until Apply, and unrelated
 * enchantments are left untouched. The selected item itself is shown in a read-only preview.</p>
 */
public final class EnchantmentEditorPage extends GuiScreen {
    static final int ROWS = GuiLayouts.COSMETIC_BROWSER_ROWS;
    static final int ENTRIES_PER_PAGE = GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE;
    static final int BACK_SLOT = GuiLayouts.COSMETIC_ENCHANT_BACK_SLOT;
    static final int PREV_SLOT = GuiLayouts.COSMETIC_ENCHANT_PREV_SLOT;
    static final int NEXT_SLOT = GuiLayouts.COSMETIC_ENCHANT_NEXT_SLOT;
    static final int APPLY_SLOT = GuiLayouts.COSMETIC_ENCHANT_APPLY_SLOT;
    static final int CANCEL_SLOT = GuiLayouts.COSMETIC_ENCHANT_CANCEL_SLOT;
    static final int PAGE_SLOT = GuiLayouts.COSMETIC_ENCHANT_PAGE_SLOT;

    /** Badge state derived from policy, never from the API conflict alone. */
    enum Status { AVAILABLE, CONFLICT_ALLOWED, CONFLICT_BLOCKED }

    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private final ItemFilter filter;
    private final Predicate<ItemStack> validator;
    private final EnchantmentCatalog catalog;
    private final ItemStack entrySnapshot;
    private final boolean entryWasStaged;
    private final int initialEnchantCount;
    private ItemStack draft;
    private int page;

    private EnchantmentEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        this(gui, player, session, slot, kitSlot, new EnchantmentCatalog());
    }

    private EnchantmentEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot,
                                  EnchantmentCatalog catalog) {
        super(gui, player, ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        this.entryWasStaged = session != null && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END
                && session.isStaged(slot);
        if (session != null && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        ItemStack target = session == null ? null : session.target();
        this.draft = target == null ? null : target.clone();
        this.entrySnapshot = this.draft == null ? null : this.draft.clone();
        this.initialEnchantCount = enchantCount(draft);
        this.filter = gui.kits() == null ? null : gui.kits().itemFilter();
        // No filter means no validated staging path: fail closed rather than bypass policy.
        this.validator = this.filter == null ? item -> false : this.filter::allowed;
        this.catalog = catalog == null ? new EnchantmentCatalog() : catalog;
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh entry from the item hub. */
    public static void open(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (gui == null || player == null || session == null) return;
        if (!ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        if (gui.kits() == null) return;
        ItemFilter filter = gui.kits().itemFilter();
        if (filter == null || !filter.isReady()) {
            send(gui, player, "error.kitroom-not-ready");
            return;
        }
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        if (gui.isCustomizeStale(player.getUniqueId())) {
            send(gui, player, "error.cosmetics-kit-changed");
            return;
        }
        ItemStack target = session.target();
        if (target == null || target.getType().isAir()) {
            send(gui, player, "error.enchant-invalid");
            return;
        }
        new EnchantmentEditorPage(gui, player, session, slot, kitSlot).show();
    }

    private static CustomizeRules rulesOf(GUI gui) {
        ConfigurationSection section = gui == null || gui.kits() == null || gui.kits().config() == null
                ? null : gui.kits().config().getConfigurationSection("customize");
        return CustomizeRules.parse(section);
    }

    private static void send(GUI gui, Player player, String key) {
        if (gui != null && gui.kits() != null && gui.kits().lang() != null) {
            gui.kits().lang().send(player, key);
        }
    }

    @Override
    protected String title() {
        return renderedTitle(kits(), kitSlot);
    }

    /**
     * Title seam: the slot is always substituted, so the live inventory title can never
     * display a literal {@code {slot}} token from the message template.
     */
    static String renderedTitle(KitsContext kits, int slot) {
        if (kits == null || kits.lang() == null) return "Enchant Item";
        return GuiMenuFactory.langTitle(kits, "gui.enchant-title", "slot", String.valueOf(slot));
    }

    /** Raw inventory slots used for catalog entries: the first 18 slots of a three-row screen. */
    static List<Integer> entrySlots() {
        List<Integer> slots = new ArrayList<>(ENTRIES_PER_PAGE);
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) slots.add(i);
        return List.copyOf(slots);
    }

    static List<EnchantmentCatalog.Entry> pageSlice(List<EnchantmentCatalog.Entry> entries, int page) {
        return GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE).slice(entries);
    }

    /** Pager controls are dead buttons on a single page, so they are hidden then. */
    static boolean showsPager(int totalEntries) {
        return GuiPager.of(0, totalEntries, ENTRIES_PER_PAGE).maxPages() > 1;
    }

    /** Display-only clone for the preview slot; never staged or persisted. */
    static ItemStack preview(ItemStack source) {
        return GuiPreviewItems.displayClone(source);
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<EnchantmentCatalog.Entry> entries = catalog.getEntries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);
        if (entries.isEmpty()) {
            paintEmpty(content);
        } else {
            List<EnchantmentCatalog.Entry> slice = pager.slice(entries);
            for (int index = 0; index < slice.size(); index++) {
                paintEntry(content, entrySlots().get(index), slice.get(index));
            }
        }
        paintPreview(content);
        paintState(content, entries);
        paintHint(content, pager);
    }

    private void paintEntry(StaticPane content, int raw, EnchantmentCatalog.Entry entry) {
        int current = currentLevel(draft, entry.enchantment());
        int max = effectiveMax(draft, entry.enchantment());
        ItemStack icon = displayIcon(entry, current, max);
        content.addItem(wrap(icon, (p, e) -> {
            SoundManager.playClick(p);
            // Read the level at click time; a stale painted value must never drive a change.
            int requested = requestedLevel(draft, entry.enchantment(), e.getClick(), max);
            if (requested != currentLevel(draft, entry.enchantment())) {
                changeLevel(p, entry.enchantment(), requested);
            }
        }, player), raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    private void paintEmpty(StaticPane content) {
        for (int raw = 0; raw <= GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST; raw++) {
            if (raw == 4) continue;
            content.addItem(new GuiItem(GuiTheme.filler(kits())),
                    raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
        }
        ItemStack card = card(Material.BOOK, "Enchantments",
                List.of(deserialize(message("gui.enchant-empty"))));
        content.addItem(new GuiItem(card), 4 % GuiLayouts.COLUMNS, 4 / GuiLayouts.COLUMNS);
    }

    private void paintPreview(StaticPane content) {
        int raw = GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT;
        ItemStack display = preview(draft);
        ItemStack item = display == null ? GuiTheme.filler(kits()) : display;
        // No click handler: this slot is a display-only showcase.
        content.addItem(new GuiItem(item), raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    private void paintState(StaticPane content, List<EnchantmentCatalog.Entry> entries) {
        List<Component> lore = new ArrayList<>();
        EnchantmentCatalog.Entry selected = entries.stream()
                .filter(entry -> currentLevel(draft, entry.enchantment()) > 0)
                .findFirst().orElse(null);
        if (selected == null) {
            lore.add(Component.text("No enchantments on this item yet", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            int level = currentLevel(draft, selected.enchantment());
            lore.add(deserialize(message("gui.enchant-selected",
                    "name", EnchantmentCatalog.plain(selected.label()),
                    "level", String.valueOf(level),
                    "max", String.valueOf(effectiveMax(draft, selected.enchantment())))));
            lore.add(deserialize(message("gui.enchant-category", "category", selected.category())));
            lore.add(selected.description());
        }
        int raw = GuiLayouts.COSMETIC_BROWSER_STATE_SLOT;
        content.addItem(new GuiItem(card(Material.BOOK, "Selected enchant", lore)),
                raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    private void paintHint(StaticPane content, GuiPager pager) {
        List<Component> lore = new ArrayList<>();
        lore.add(deserialize(message("gui.enchant-hint")));
        lore.add(deserialize(message("gui.enchant-back-hint")));
        lore.add(deserialize(message("gui.enchant-apply-hint")));
        if (pager.maxPages() <= 1) {
            lore.add(Component.text("Page 1 of 1", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        int raw = GuiLayouts.COSMETIC_BROWSER_HINT_SLOT;
        content.addItem(new GuiItem(card(Material.PAPER, "How to use", lore)),
                raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    private static ItemStack card(Material material, String name, List<Component> lore) {
        ItemStack card = new ItemStack(material);
        ItemMeta meta = card.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            card.setItemMeta(meta);
        }
        return card;
    }

    private ItemStack displayIcon(EnchantmentCatalog.Entry entry, int level, int max) {
        // Book icon with the stored enchant already applied; never call GuiTheme.glow here,
        // because it adds HIDE_ENCHANTS and hides exactly what the player must see.
        ItemStack icon = entry.icon(level);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.displayName(deserialize(message("gui.enchant-selected",
                "name", EnchantmentCatalog.plain(entry.label()),
                "level", String.valueOf(level),
                "max", String.valueOf(max))));
        List<Component> lore = new ArrayList<>();
        lore.add(deserialize(message("gui.enchant-level",
                "level", String.valueOf(level), "max", String.valueOf(max))));
        lore.add(statusLine(entry.enchantment()));
        lore.add(deserialize(message("gui.enchant-hint")));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private Component statusLine(Enchantment enchantment) {
        boolean incompatible = !canEnchant(draft, enchantment) || apiConflict(draft, enchantment);
        Status status = status(filter != null && filter.enabled(),
                filter != null && filter.allowsIncompatible(draft), incompatible);
        return switch (status) {
            case CONFLICT_BLOCKED -> deserialize(message("gui.enchant-conflict-blocked"));
            case CONFLICT_ALLOWED -> deserialize(message("gui.enchant-conflict-allowed"));
            case AVAILABLE -> Component.text("Available", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
        };
    }

    /** Policy-derived badge. Disabled policy has no enforcement, so it never shows a red state. */
    static Status status(boolean policyEnabled, boolean allowIncompatible, boolean apiConflict) {
        if (!policyEnabled || !apiConflict) return Status.AVAILABLE;
        return allowIncompatible ? Status.CONFLICT_ALLOWED : Status.CONFLICT_BLOCKED;
    }

    /**
     * Conflicts on the draft's own enchantments, mirroring both directions of
     * {@code Enchantment.conflictsWith} used by {@code ItemFilter}. Books read stored enchants.
     */
    static boolean apiConflict(ItemStack draft, Enchantment enchantment) {
        if (draft == null || enchantment == null) return false;
        for (Enchantment other : existing(draft).keySet()) {
            if (other == null || other.equals(enchantment)) continue;
            try {
                if (enchantment.conflictsWith(other) || other.conflictsWith(enchantment)) return true;
            } catch (RuntimeException | LinkageError ignored) {
                // A broken enchantment is never treated as a conflict.
            }
        }
        return false;
    }

    /** Stored enchants on books skip canEnchantItem, exactly like ItemFilter. */
    static boolean canEnchant(ItemStack draft, Enchantment enchantment) {
        if (draft == null || enchantment == null || draft.getType().isAir()) return false;
        if (draft.getItemMeta() instanceof EnchantmentStorageMeta) return true;
        try {
            return enchantment.canEnchantItem(draft);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Map<Enchantment, Integer> existing(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) return book.getStoredEnchants();
        return item == null ? Map.of() : item.getEnchantments();
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        List<EnchantmentCatalog.Entry> entries = catalog.getEntries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);

        chrome.addItem(wrap(GuiTheme.named(Material.ARROW, GuiTheme.MUTED, "Back",
                message("gui.enchant-back-hint")), (p, e) -> {
            SoundManager.playClick(p);
            reopenHub(p);
        }, player), BACK_SLOT % GuiLayouts.COLUMNS, BACK_SLOT / GuiLayouts.COLUMNS);

        if (showsPager(entries.size())) {
            if (page > 0) {
                chrome.addItem(wrap(GuiTheme.arrow(false, page - 1, pager.maxPages()), (p, e) -> {
                    SoundManager.playClick(p);
                    page--;
                    refresh();
                }, player), PREV_SLOT % GuiLayouts.COLUMNS, PREV_SLOT / GuiLayouts.COLUMNS);
            }
            if (page < pager.maxPages() - 1) {
                chrome.addItem(wrap(GuiTheme.arrow(true, page + 1, pager.maxPages()), (p, e) -> {
                    SoundManager.playClick(p);
                    page++;
                    refresh();
                }, player), NEXT_SLOT % GuiLayouts.COLUMNS, NEXT_SLOT / GuiLayouts.COLUMNS);
            }
            chrome.addItem(wrap(GuiTheme.pageItem(page, pager.maxPages()), (p, e) -> {
                SoundManager.playClick(p);
                page = (page + 1) % pager.maxPages();
                refresh();
            }, player), PAGE_SLOT % GuiLayouts.COLUMNS, PAGE_SLOT / GuiLayouts.COLUMNS);
        }

        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply Enchants",
                message("gui.enchant-apply-hint")), (p, e) -> {
            SoundManager.playClick(p);
            apply(p);
        }, player), APPLY_SLOT % GuiLayouts.COLUMNS, APPLY_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.dangerTool(Material.BARRIER, "Cancel",
                message("gui.enchant-cancel-hint"), "Click", "revert"), (p, e) -> {
            SoundManager.playClick(p);
            cancel(p);
        }, player), CANCEL_SLOT % GuiLayouts.COLUMNS, CANCEL_SLOT / GuiLayouts.COLUMNS);
    }

    // ------------------------------------------------------------------ editing

    /** Stored enchant levels on books; applied enchant levels on every other item. */
    private static int currentLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || enchantment == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            return book.getStoredEnchants().getOrDefault(enchantment, 0);
        }
        return item.getEnchantments().getOrDefault(enchantment, 0);
    }

    /** Counts the same enchant storage {@link #currentLevel} reads, for the Apply message. */
    private static int enchantCount(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) return book.getStoredEnchants().size();
        return item.getEnchantments().size();
    }

    /** Effective ceiling shared with Apply through the configured item filter. */
    private int effectiveMax(ItemStack item, Enchantment enchantment) {
        if (enchantment == null) return 1;
        return filter == null ? Math.max(1, enchantment.getMaxLevel())
                : filter.effectiveMaxLevel(item, enchantment);
    }

    /** Advances 1..max and stays at max; right-click is the only removal path. */
    static int nextLevel(int current, int maxLevel) {
        int max = Math.max(1, maxLevel);
        if (current <= 0) return 1;
        if (current >= max) return max;
        return current + 1;
    }

    /** Resolves the requested level from the draft as it is at click time. */
    static int requestedLevel(ItemStack draft, Enchantment enchantment, ClickType click, int maxLevel) {
        if (click == ClickType.RIGHT) return 0;
        return nextLevel(currentLevel(draft, enchantment), maxLevel);
    }

    /**
     * Returns a clone of {@code source} with one enchantment set to {@code level}, or removed
     * when {@code level <= 0}. Unrelated enchantments (and the source) are never modified.
     */
    static ItemStack withLevel(ItemStack source, Enchantment enchantment, int level) {
        if (source == null || enchantment == null) return null;
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return null;
        if (meta instanceof EnchantmentStorageMeta book) {
            book.removeStoredEnchant(enchantment);
            if (level > 0) {
                try {
                    book.addStoredEnchant(enchantment, level, true);
                } catch (RuntimeException ex) {
                    return null;
                }
                if (book.getStoredEnchants().getOrDefault(enchantment, 0) != level) return null;
            }
            result.setItemMeta(book);
            return result;
        }
        meta.removeEnchant(enchantment);
        if (level > 0) {
            try {
                // Ignore vanilla restrictions: ItemFilter.allowed is the sole policy authority.
                meta.addEnchant(enchantment, level, true);
            } catch (RuntimeException ex) {
                return null;
            }
            if (meta.getEnchantLevel(enchantment) != level) return null;
        }
        result.setItemMeta(meta);
        return result;
    }

    /** Only a candidate accepted by the item filter can ever reach the session. */
    static boolean stageIfAllowed(CustomizeSession session, ItemStack candidate, Predicate<ItemStack> allowed) {
        if (session == null || candidate == null || candidate.getType().isAir()) return false;
        if (allowed != null && !allowed.test(candidate)) return false;
        session.stageTarget(candidate);
        return true;
    }

    /**
     * Cancel is scoped to this entry: when it was unstaged on entry the target is reset,
     * otherwise the entry's own staged snapshot is restored. Back never calls this.
     */
    static void restoreOnCancel(CustomizeSession session, ItemStack entrySnapshot, boolean entryWasStaged) {
        if (session == null) return;
        if (entryWasStaged) {
            session.stageTarget(entrySnapshot);
        } else {
            session.resetTarget();
        }
    }

    private void changeLevel(Player player, Enchantment enchantment, int level) {
        if (draft == null) return;
        ItemStack next = withLevel(draft, enchantment, level);
        if (next == null || !validator.test(next)) {
            send(gui, player, "error.enchant-invalid");
            return;
        }
        draft = next;
        refresh();
    }

    private void apply(Player player) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        if (!stageIfAllowed(session, draft, validator)) {
            send(gui, player, "error.enchant-invalid");
            return;
        }
        int finalCount = enchantCount(draft);
        send(gui, player, finalCount < initialEnchantCount ? "gui.enchant-removed" : "gui.enchant-added");
        reopenHub(player);
    }

    private void cancel(Player player) {
        restoreOnCancel(session, entrySnapshot, entryWasStaged);
        reopenHub(player);
    }

    private void reopenHub(Player player) {
        CustomizePage.reopen(gui, player, session, session.targetSlot(), kitSlot);
    }

    /** Message lookup with placeholder substitution even when the language facade is absent. */
    private String message(String key, String... pairs) {
        String raw = kits() != null && kits().lang() != null
                ? kits().lang().raw(key, pairs)
                : fallback(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace("{" + pairs[i] + "}", String.valueOf(pairs[i + 1]));
        }
        return raw;
    }

    private static String fallback(String key) {
        return switch (key) {
            case "gui.enchant-hint" -> "Left: next level | Right: remove | Apply stages";
            case "gui.enchant-back-hint" -> "Back - keep staged";
            case "gui.enchant-cancel-hint" -> "Revert editor edits";
            case "gui.enchant-apply-hint" -> "Stage these changes";
            case "gui.enchant-empty" -> "No enchantments available";
            case "gui.enchant-selected" -> "{name} - Level {level} / {max}";
            case "gui.enchant-category" -> "{category}";
            case "gui.enchant-conflict-allowed" -> "API conflict - allowed by current policy";
            case "gui.enchant-conflict-blocked" -> "Incompatible with this item";
            case "gui.enchant-level" -> "Level {level} / {max}";
            default -> key;
        };
    }

    private static Component deserialize(String miniMessage) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessage);
    }

    @Override
    protected void protect(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(com.github.stefvanschie.inventoryframework.gui.type.ChestGui chest) {
        chest.setOnClose(event -> {
            if (event.getPlayer() instanceof Player closer) {
                gui.unregisterCustomizeScreen(closer.getUniqueId());
            }
        });
    }
}
