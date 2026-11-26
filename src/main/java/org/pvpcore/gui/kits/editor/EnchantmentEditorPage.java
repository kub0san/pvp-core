package org.pvpcore.gui.kits.editor;

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
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.features.kits.cosmetics.EnchantmentCatalog;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
import org.pvpcore.gui.base.GuiPager;
import org.pvpcore.gui.base.GuiScreen;
import org.pvpcore.gui.base.GuiTheme;
import org.pvpcore.gui.kits.GUI;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Compact three-row enchantment editor for one kit item.
 *
 * <p>Clicks build a private draft: left-click cycles an enchantment from level 1 up to its
 * maximum (then back to none), right-click removes it. Every change is checked against the
 * kit item filter before the draft accepts it. Nothing reaches the {@link CustomizeSession}
 * until Apply, and unrelated enchantments are left untouched.</p>
 */
public final class EnchantmentEditorPage extends GuiScreen {
    static final int ROWS = 3;
    static final int ENTRIES_PER_PAGE = 21;
    static final int BACK_SLOT = 21;
    static final int PREV_SLOT = 22;
    static final int NEXT_SLOT = 23;
    static final int APPLY_SLOT = 24;
    static final int CANCEL_SLOT = 25;
    static final int PAGE_SLOT = 26;

    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private final Predicate<ItemStack> validator;
    private final int initialEnchantCount;
    private ItemStack draft;
    private int page;

    private EnchantmentEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        super(gui, player, ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        if (session != null && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        ItemStack target = session == null ? null : session.target();
        this.draft = target == null ? null : target.clone();
        this.initialEnchantCount = draft == null ? 0 : draft.getEnchantments().size();
        ItemFilter filter = gui.kits() == null ? null : gui.kits().itemFilter();
        // No filter means no validated staging path: fail closed rather than bypass policy.
        this.validator = filter == null ? item -> false : filter::allowed;
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
        if (kits() == null || kits().lang() == null) return "Enchant Item";
        return GuiMenuFactory.langTitle(kits(), "gui.enchant-title");
    }

    /** Raw inventory slots used for catalog entries: the first 21 slots of a three-row screen. */
    static List<Integer> entrySlots() {
        List<Integer> slots = new ArrayList<>(ENTRIES_PER_PAGE);
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) slots.add(i);
        return List.copyOf(slots);
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<EnchantmentCatalog.Entry> entries = EnchantmentCatalog.entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);
        List<EnchantmentCatalog.Entry> slice = pager.slice(entries);
        for (int index = 0; index < slice.size(); index++) {
            EnchantmentCatalog.Entry entry = slice.get(index);
            int current = draft == null ? 0 : draft.getEnchantmentLevel(entry.enchantment());
            int max = Math.max(1, entry.enchantment().getMaxLevel());
            ItemStack icon = displayIcon(entry, current, max);
            final int level = current;
            int raw = entrySlots().get(index);
            content.addItem(wrap(icon, (p, e) -> {
                SoundManager.playClick(p);
                int requested = e.getClick() == ClickType.RIGHT ? 0 : nextLevel(level, max);
                changeLevel(p, entry.enchantment(), requested);
            }, player), raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
        }
    }

    private ItemStack displayIcon(EnchantmentCatalog.Entry entry, int level, int max) {
        ItemStack icon = entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.displayName(Component.text(EnchantmentCatalog.plain(entry.label()))
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(levelLine(level, max));
        lore.add(Component.text(level > 0 ? "Right-click removes" : "Click adds level 1",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return level > 0 ? GuiTheme.glow(icon) : icon;
    }

    private Component levelLine(int level, int max) {
        if (kits() != null && kits().lang() != null) {
            String raw = kits().lang().raw("gui.enchant-level",
                    "level", String.valueOf(level), "max", String.valueOf(max));
            return deserialize(raw);
        }
        return Component.text("Level " + level + " / " + max, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component deserialize(String miniMessage) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessage);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        List<EnchantmentCatalog.Entry> entries = EnchantmentCatalog.entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);

        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }, player), BACK_SLOT % GuiLayouts.COLUMNS, BACK_SLOT / GuiLayouts.COLUMNS);

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

        chrome.addItem(wrap(GuiTheme.named(Material.LIME_DYE, GuiTheme.GREEN + "<bold>", "Apply Enchants",
                GuiTheme.dim("Stage these changes"), GuiTheme.hint("Click", "apply")), (p, e) -> {
            SoundManager.playClick(p);
            apply(p);
        }, player), APPLY_SLOT % GuiLayouts.COLUMNS, APPLY_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.dangerTool(Material.BARRIER, "Discard",
                "Return without staging", "Click", "back"), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }, player), CANCEL_SLOT % GuiLayouts.COLUMNS, CANCEL_SLOT / GuiLayouts.COLUMNS);
    }

    // ------------------------------------------------------------------ editing

    /** Advances 1..max and wraps back to "no level" after the maximum. */
    static int nextLevel(int current, int maxLevel) {
        int max = Math.max(1, maxLevel);
        if (current <= 0) return 1;
        if (current >= max) return 0;
        return current + 1;
    }

    /**
     * Returns a clone of {@code source} with one enchantment set to {@code level}, or removed
     * when {@code level <= 0}. Unrelated enchantments (and the source) are never modified.
     */
    static ItemStack withLevel(ItemStack source, Enchantment enchantment, int level) {
        if (source == null || enchantment == null) return null;
        ItemStack result = source.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            book.removeStoredEnchant(enchantment);
            if (level > 0) {
                try {
                    book.addStoredEnchant(enchantment, level, true);
                } catch (RuntimeException ex) {
                    return null;
                }
                if (book.getStoredEnchantLevel(enchantment) != level) return null;
            }
            result.setItemMeta(book);
            return result;
        }
        result.removeEnchantment(enchantment);
        if (level > 0) {
            try {
                result.addEnchantment(enchantment, level);
            } catch (RuntimeException ex) {
                return null;
            }
            if (result.getEnchantmentLevel(enchantment) != level) return null;
        }
        return result;
    }

    /** Only a candidate accepted by the item filter can ever reach the session. */
    static boolean stageIfAllowed(CustomizeSession session, ItemStack candidate, Predicate<ItemStack> allowed) {
        if (session == null || candidate == null || candidate.getType().isAir()) return false;
        if (allowed != null && !allowed.test(candidate)) return false;
        session.stageTarget(candidate);
        return true;
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
        int finalCount = draft.getEnchantments().size();
        send(gui, player, finalCount < initialEnchantCount ? "gui.enchant-removed" : "gui.enchant-added");
        CustomizePage.reopen(gui, player, session, session.targetSlot(), kitSlot);
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
