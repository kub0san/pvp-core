package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
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

/**
 * Compact three-row trim editor for one armor item.
 *
 * <p>Two catalog views (pattern templates and trim materials) share the same 21 entry slots.
 * Selecting an entry resolves its registry object, applies an {@link ArmorTrim} to a private
 * draft through {@link CosmeticApplier} and stages that clone immediately; Remove clears the
 * trim. Non-armor items can never reach this screen, and an unresolvable registry entry is
 * ignored rather than guessed.</p>
 */
public final class TrimEditorPage extends GuiScreen {
    static final int ROWS = 3;
    static final int ENTRIES_PER_PAGE = 21;
    static final int MODE_SLOT = 21;
    static final int PREV_SLOT = 22;
    static final int NEXT_SLOT = 23;
    static final int PAGE_SLOT = 24;
    static final int REMOVE_SLOT = 25;
    static final int BACK_SLOT = 26;

    private enum Mode { PATTERN, MATERIAL }

    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private final CosmeticApplier applier;
    private final CosmeticCatalog catalog;
    private ItemStack draft;
    private String selectedPattern;
    private String selectedMaterial;
    private Mode mode = Mode.PATTERN;
    private int page;

    private TrimEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        super(gui, player, ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        if (slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        CustomizeRules rules = rulesOf(gui);
        this.applier = new CosmeticApplier(rules, new CosmeticValidator(rules));
        this.catalog = CosmeticCatalog.fromRegistries(rules);
        ItemStack target = session.target();
        this.draft = target == null ? null : target.clone();
        captureSelection(this.draft);
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh entry from the item hub; non-armor targets are rejected before any screen opens. */
    public static void open(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (gui == null || player == null || session == null) return;
        if (!ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return;
        if (gui.kits() == null) return;
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        if (gui.isCustomizeStale(player.getUniqueId())) {
            send(gui, player, "error.cosmetics-kit-changed");
            return;
        }
        if (session.targetSlot() < 0 && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        if (!CustomizePage.supportsTrim(session.target())) {
            send(gui, player, "error.cosmetics-not-armor");
            return;
        }
        new TrimEditorPage(gui, player, session, slot, kitSlot).show();
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
        if (kits() == null) return "Trim Armor";
        return GuiMenuFactory.sectionTitle(kits(), GuiTheme.ACCENT + "<bold>Trim Armor");
    }

    /** Raw inventory slots used for catalog entries: the first 21 slots of a three-row screen. */
    static List<Integer> entrySlots() {
        List<Integer> slots = new ArrayList<>(ENTRIES_PER_PAGE);
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) slots.add(i);
        return List.copyOf(slots);
    }

    private List<CosmeticCatalog.Entry> entries() {
        return mode == Mode.PATTERN ? catalog.trimPatterns() : catalog.trimMaterials();
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<CosmeticCatalog.Entry> entries = entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);
        List<CosmeticCatalog.Entry> slice = pager.slice(entries);
        String selectedKey = mode == Mode.PATTERN ? selectedPattern : selectedMaterial;
        for (int index = 0; index < slice.size(); index++) {
            CosmeticCatalog.Entry entry = slice.get(index);
            boolean selected = entry.key().equals(selectedKey);
            int raw = entrySlots().get(index);
            content.addItem(wrap(displayIcon(entry, selected), (p, e) -> {
                SoundManager.playClick(p);
                select(p, entry);
            }, player), raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
        }
    }

    private ItemStack displayIcon(CosmeticCatalog.Entry entry, boolean selected) {
        ItemStack icon = entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.displayName(line(entry.label(), NamedTextColor.AQUA, true));
        List<Component> lore = new ArrayList<>();
        lore.add(line(mode == Mode.PATTERN ? "Pattern template" : "Trim material",
                NamedTextColor.GRAY, false));
        lore.add(line(selected ? "Selected - click to keep" : "Click to apply",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return selected ? GuiTheme.glow(icon) : icon;
    }

    private static Component line(String text, NamedTextColor color, boolean bold) {
        return Component.text(text).color(color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        List<CosmeticCatalog.Entry> entries = entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);

        Material switchIcon = mode == Mode.PATTERN ? Material.NETHERITE_INGOT
                : Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
        String switchName = mode == Mode.PATTERN ? "Materials" : "Patterns";
        chrome.addItem(wrap(GuiTheme.named(switchIcon, GuiTheme.GOLD + "<bold>", switchName,
                GuiTheme.dim("Switch trim category"), GuiTheme.hint("Click", "switch")), (p, e) -> {
            SoundManager.playClick(p);
            mode = mode == Mode.PATTERN ? Mode.MATERIAL : Mode.PATTERN;
            page = 0;
            refresh();
        }, player), MODE_SLOT % GuiLayouts.COLUMNS, MODE_SLOT / GuiLayouts.COLUMNS);

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

        chrome.addItem(wrap(GuiTheme.dangerTool(Material.BARRIER, "Remove Trim",
                "Clear this item's trim", "Click", "remove"), (p, e) -> {
            SoundManager.playClick(p);
            remove(p);
        }, player), REMOVE_SLOT % GuiLayouts.COLUMNS, REMOVE_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }, player), BACK_SLOT % GuiLayouts.COLUMNS, BACK_SLOT / GuiLayouts.COLUMNS);
    }

    // ------------------------------------------------------------------ editing

    /** Remember the current trim so the other half of the pair can be resolved on the next click. */
    private void captureSelection(ItemStack item) {
        ArmorTrim trim = trim(item);
        if (trim == null) return;
        String pattern = keyOf(trim.getPattern());
        String material = keyOf(trim.getMaterial());
        if (pattern != null) selectedPattern = pattern;
        if (material != null) selectedMaterial = material;
    }

    private static ArmorTrim trim(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof ArmorMeta armor ? armor.getTrim() : null;
    }

    private static String keyOf(Keyed keyed) {
        try {
            return keyed.getKey() == null ? null : keyed.getKey().toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private TrimPattern patternFor(CosmeticCatalog.Entry clicked) {
        String key = mode == Mode.PATTERN ? clicked.key()
                : selectedPattern != null ? selectedPattern : firstKey(catalog.trimPatterns());
        return key == null ? null : CosmeticCatalog.trimPattern(key);
    }

    private TrimMaterial materialFor(CosmeticCatalog.Entry clicked) {
        String key = mode == Mode.MATERIAL ? clicked.key()
                : selectedMaterial != null ? selectedMaterial : firstKey(catalog.trimMaterials());
        return key == null ? null : CosmeticCatalog.trimMaterial(key);
    }

    private static String firstKey(List<CosmeticCatalog.Entry> entries) {
        return entries.isEmpty() ? null : entries.get(0).key();
    }

    private void select(Player player, CosmeticCatalog.Entry entry) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        ItemStack next = applyOrNull(draft, applier, materialFor(entry), patternFor(entry));
        if (next == null || !stageIfArmor(session, next)) return;
        draft = next;
        captureSelection(next);
        refresh();
    }

    private void remove(Player player) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        ItemStack next = removeOrNull(draft, applier);
        if (next == null || !stageIfArmor(session, next)) return;
        draft = next;
        refresh();
    }

    /**
     * Only an armor item may be staged, and only a clone accepted by this guard ever reaches the
     * session. The original draft and the source kit item are never mutated.
     */
    static boolean stageIfArmor(CustomizeSession session, ItemStack candidate) {
        if (session == null || !CustomizePage.supportsTrim(candidate)) return false;
        session.stageTarget(candidate);
        return true;
    }

    /** Applies a trim to a clone, or null for non-armor/missing objects/disallowed keys. */
    static ItemStack applyOrNull(ItemStack source, CosmeticApplier applier,
                                 TrimMaterial material, TrimPattern pattern) {
        if (applier == null || material == null || pattern == null) return null;
        if (!CustomizePage.supportsTrim(source)) return null;
        try {
            return applier.applyTrim(source, material, pattern);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Clears a trim on a clone, or null when the target cannot be safely edited. */
    static ItemStack removeOrNull(ItemStack source, CosmeticApplier applier) {
        if (applier == null || !CustomizePage.supportsTrim(source)) return null;
        try {
            return applier.removeTrim(source);
        } catch (RuntimeException ex) {
            return null;
        }
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
