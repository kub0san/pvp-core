package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticCatalog;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiClick;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact three-row trim editor for one armor item.
 *
 * <p>Two catalog views (pattern templates and trim materials) share the same 18 entry slots.
 * Selecting an entry resolves its registry object, applies an {@link ArmorTrim} to a private
 * draft through {@link CosmeticApplier} and stages that clone immediately; Remove clears the
 * trim and the selected state. A display-only preview is rebuilt after every change. Non-armor
 * items can never reach this screen, and an unresolvable registry entry is ignored rather
 * than guessed.</p>
 */
public final class TrimEditorPage extends GuiScreen {
    static final int ROWS = GuiLayouts.COSMETIC_BROWSER_ROWS;
    static final int ENTRIES_PER_PAGE = GuiLayouts.COSMETIC_BROWSER_ENTRIES_PER_PAGE;
    static final int MODE_SLOT = GuiLayouts.COSMETIC_TRIM_MODE_SLOT;
    static final int PREV_SLOT = GuiLayouts.COSMETIC_TRIM_PREV_SLOT;
    static final int NEXT_SLOT = GuiLayouts.COSMETIC_TRIM_NEXT_SLOT;
    static final int PAGE_SLOT = GuiLayouts.COSMETIC_TRIM_PAGE_SLOT;
    static final int REMOVE_SLOT = GuiLayouts.COSMETIC_TRIM_REMOVE_SLOT;
    static final int BACK_SLOT = GuiLayouts.COSMETIC_TRIM_BACK_SLOT;

    private enum Mode { PATTERN, MATERIAL }

    private final GUI gui;
    private final CustomizeSession session;
    private final int kitSlot;
    private final CosmeticApplier applier;
    private final CosmeticCatalog catalog;
    private ItemStack draft;
    private ItemStack preview;
    private String selectedPattern;
    private String selectedMaterial;
    private Mode mode = Mode.PATTERN;
    private int page;

    private TrimEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        this(gui, player, session, slot, kitSlot, CosmeticCatalog.fromRegistries(rulesOf(gui)));
    }

    TrimEditorPage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot,
                   CosmeticCatalog catalog) {
        super(gui, player, ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.kitSlot = kitSlot;
        if (slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        CustomizeRules rules = rulesOf(gui);
        this.applier = new CosmeticApplier(rules, new CosmeticValidator(rules));
        this.catalog = catalog == null ? CosmeticCatalog.fromRegistries(rules) : catalog;
        ItemStack target = session.target();
        this.draft = target == null ? null : target.clone();
        captureSelection(this.draft);
        updatePreview();
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
        return renderedTitle(kits(), kitSlot, mode == Mode.MATERIAL);
    }

    /** Title seam: the kit slot is always substituted, never a literal {@code {slot}} token. */
    static String renderedTitle(KitsContext kits, int slot, boolean material) {
        if (kits == null || kits.lang() == null) return material ? "Trim Material" : "Trim Pattern";
        return GuiMenuFactory.langTitle(kits,
                material ? "gui.trim-material-title" : "gui.trim-pattern-title",
                "slot", String.valueOf(slot));
    }

    /** Raw inventory slots used for catalog entries: the first 18 slots of a three-row screen. */
    static List<Integer> entrySlots() {
        List<Integer> slots = new ArrayList<>(ENTRIES_PER_PAGE);
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) slots.add(i);
        return List.copyOf(slots);
    }

    /** Pager controls are dead buttons on a single page, so they are hidden then. */
    static boolean showsPager(int totalEntries) {
        return GuiPager.of(0, totalEntries, ENTRIES_PER_PAGE).maxPages() > 1;
    }

    private List<CosmeticCatalog.Entry> entries() {
        return mode == Mode.PATTERN ? catalog.trimPatterns() : catalog.trimMaterials();
    }

    /**
     * Display-only preview: the draft with the resolved trim applied, stripped of editor hints.
     * A missing registry object or a rejected apply leaves the source draft visible rather
     * than inventing a trim. Null and air sources return null.
     */
    static ItemStack preview(ItemStack source, CosmeticApplier applier,
                             TrimMaterial material, TrimPattern pattern) {
        if (source == null || source.getType().isAir()) return null;
        ItemStack base = source;
        if (material != null && pattern != null) {
            ItemStack applied = applyOrNull(source, applier, material, pattern);
            if (applied != null) base = applied;
        }
        return GuiPreviewItems.displayClone(base);
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<CosmeticCatalog.Entry> entries = entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);
        if (entries.isEmpty()) {
            paintEmpty(content);
        } else {
            List<CosmeticCatalog.Entry> slice = pager.slice(entries);
            String selectedKey = mode == Mode.PATTERN ? selectedPattern : selectedMaterial;
            for (int index = 0; index < slice.size(); index++) {
                CosmeticCatalog.Entry entry = slice.get(index);
                boolean selected = entry.key().equals(selectedKey);
                int raw = entrySlots().get(index);
                content.addItem(clickable(displayIcon(entry, selected,
                                mode == Mode.PATTERN ? "Pattern template" : "Trim material"),
                        (p, e) -> {
                            SoundManager.playClick(p);
                            select(p, entry);
                        }), raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
            }
        }

        // Preview: display-only clone, no click handler and no theme styling.
        int previewRaw = GuiLayouts.COSMETIC_BROWSER_PREVIEW_SLOT;
        content.addItem(displayItem(preview == null ? GuiTheme.filler(kits()) : preview),
                previewRaw % GuiLayouts.COLUMNS, previewRaw / GuiLayouts.COLUMNS);

        paintCard(content, GuiLayouts.COSMETIC_BROWSER_STATE_SLOT, Material.BOOK, "Trim state",
                List.of(deserialize(statusLine())));

        List<Component> hint = new ArrayList<>();
        hint.add(deserialize(message("gui.trim-hint")));
        hint.add(deserialize(message(mode == Mode.PATTERN
                ? "gui.trim-pattern-hint" : "gui.trim-material-hint")));
        if (pager.maxPages() <= 1) {
            hint.add(line("Page 1 of 1", NamedTextColor.GRAY, false));
        }
        paintCard(content, GuiLayouts.COSMETIC_BROWSER_HINT_SLOT, Material.PAPER, "How to use", hint);
    }

    private void paintEmpty(StaticPane content) {
        for (int raw = 0; raw <= GuiLayouts.COSMETIC_BROWSER_ENTRY_LAST; raw++) {
            if (raw == 4) continue;
            content.addItem(displayItem(GuiTheme.filler(kits())),
                    raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
        }
        paintCard(content, 4, Material.BOOK, "Trim catalog",
                List.of(deserialize(message("gui.trim-empty"))));
    }

    /** Catalog icon with a non-destructive selected state; never glow, which hides metadata. */
    static ItemStack displayIcon(CosmeticCatalog.Entry entry, boolean selected, String kind) {
        ItemStack icon = entry.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.displayName(line(entry.label(),
                selected ? NamedTextColor.GREEN : NamedTextColor.AQUA, true));
        List<Component> lore = new ArrayList<>();
        lore.add(line(kind, NamedTextColor.GRAY, false));
        lore.add(line(selected ? "Selected - click to keep" : "Click to apply",
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private String statusLine() {
        return message("gui.trim-status",
                "pattern", statusValue(selectedPattern, catalog.trimPatterns()),
                "material", statusValue(selectedMaterial, catalog.trimMaterials()));
    }

    /**
     * No selection means the untrimmed draft is on display, so the state line must not name a
     * concrete catalog trim; "None (default)" truthfully describes either half of the pair.
     */
    private static String statusValue(String key, List<CosmeticCatalog.Entry> options) {
        if (key == null) return "None (default)";
        return options.stream()
                .filter(entry -> entry.key().equals(key))
                .map(CosmeticCatalog.Entry::label)
                .findFirst()
                .orElse(key);
    }

    private void paintCard(StaticPane pane, int raw, Material material, String name,
                           List<Component> lore) {
        pane.addItem(displayItem(card(material, name, lore)),
                raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    private static ItemStack card(Material material, String name, List<Component> lore) {
        ItemStack card = new ItemStack(material);
        ItemMeta meta = card.getItemMeta();
        if (meta != null) {
            meta.displayName(line(name, NamedTextColor.AQUA, true));
            meta.lore(lore);
            card.setItemMeta(meta);
        }
        return card;
    }

    private static Component line(String text, NamedTextColor color, boolean bold) {
        return Component.text(text).color(color)
                .decoration(TextDecoration.BOLD, bold)
                .decoration(TextDecoration.ITALIC, false);
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
            case "gui.trim-hint" -> "Click to stage | Remove clears trim";
            case "gui.trim-pattern-hint" -> "Choosing pattern - click to stage";
            case "gui.trim-material-hint" -> "Choosing material - click to stage";
            case "gui.trim-empty" -> "No trims available";
            case "gui.trim-status" -> "Pattern: {pattern} | Material: {material}";
            default -> key;
        };
    }

    private static Component deserialize(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        List<CosmeticCatalog.Entry> entries = entries();
        GuiPager pager = GuiPager.of(page, entries.size(), ENTRIES_PER_PAGE);

        Material switchIcon = mode == Mode.PATTERN ? Material.NETHERITE_INGOT
                : Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE;
        String switchName = mode == Mode.PATTERN ? "Materials" : "Patterns";
        chrome.addItem(clickable(GuiTheme.named(switchIcon, GuiTheme.GOLD + "<bold>", switchName,
                GuiTheme.dim("Switch trim category"), GuiTheme.hint("Click", "switch")), (p, e) -> {
            SoundManager.playClick(p);
            switchMode();
        }), MODE_SLOT % GuiLayouts.COLUMNS, MODE_SLOT / GuiLayouts.COLUMNS);

        if (showsPager(entries.size())) {
            if (page > 0) {
                chrome.addItem(clickable(GuiTheme.arrow(false, page - 1, pager.maxPages()), (p, e) -> {
                    SoundManager.playClick(p);
                    page--;
                    refresh();
                }), PREV_SLOT % GuiLayouts.COLUMNS, PREV_SLOT / GuiLayouts.COLUMNS);
            }
            if (page < pager.maxPages() - 1) {
                chrome.addItem(clickable(GuiTheme.arrow(true, page + 1, pager.maxPages()), (p, e) -> {
                    SoundManager.playClick(p);
                    page++;
                    refresh();
                }), NEXT_SLOT % GuiLayouts.COLUMNS, NEXT_SLOT / GuiLayouts.COLUMNS);
            }
            chrome.addItem(clickable(GuiTheme.pageItem(page, pager.maxPages()), (p, e) -> {
                SoundManager.playClick(p);
                page = (page + 1) % pager.maxPages();
                refresh();
            }), PAGE_SLOT % GuiLayouts.COLUMNS, PAGE_SLOT / GuiLayouts.COLUMNS);
        }

        chrome.addItem(clickable(GuiTheme.dangerTool(Material.BARRIER, "Remove Trim",
                "Clear this item's trim", "Click", "remove"), (p, e) -> {
            SoundManager.playClick(p);
            remove(p);
        }), REMOVE_SLOT % GuiLayouts.COLUMNS, REMOVE_SLOT / GuiLayouts.COLUMNS);

        chrome.addItem(clickable(GuiTheme.back(), (p, e) -> {
            SoundManager.playClick(p);
            CustomizePage.reopen(gui, p, session, session.targetSlot(), kitSlot);
        }), BACK_SLOT % GuiLayouts.COLUMNS, BACK_SLOT / GuiLayouts.COLUMNS);
    }

    // ------------------------------------------------------------------ editing

    /** Explicit plugin owner: relocated IF in production, test-classpath IF in unit tests. */
    private GuiItem clickable(ItemStack item, GuiClick click) {
        return new GuiItem(item, event -> {
            Player clicker = event.getWhoClicked() instanceof Player p ? p : player;
            click.on(clicker, event);
        }, gui.plugin());
    }

    /** A GuiItem with no click handler, used for the preview and information cards. */
    private GuiItem displayItem(ItemStack item) {
        return new GuiItem(item, gui.plugin());
    }

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

    private String patternKey(CosmeticCatalog.Entry clicked) {
        if (mode == Mode.PATTERN && clicked != null) return clicked.key();
        return selectedPattern != null ? selectedPattern : firstKey(catalog.trimPatterns());
    }

    private String materialKey(CosmeticCatalog.Entry clicked) {
        if (mode == Mode.MATERIAL && clicked != null) return clicked.key();
        return selectedMaterial != null ? selectedMaterial : firstKey(catalog.trimMaterials());
    }

    private TrimPattern patternFor(CosmeticCatalog.Entry clicked) {
        String key = patternKey(clicked);
        return key == null ? null : CosmeticCatalog.trimPattern(key);
    }

    private TrimMaterial materialFor(CosmeticCatalog.Entry clicked) {
        String key = materialKey(clicked);
        return key == null ? null : CosmeticCatalog.trimMaterial(key);
    }

    private static String firstKey(List<CosmeticCatalog.Entry> entries) {
        return entries.isEmpty() ? null : entries.get(0).key();
    }

    void select(Player player, CosmeticCatalog.Entry entry) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        selectResolved(player, materialFor(entry), patternFor(entry));
    }

    /** Core of a catalog click with registry objects already resolved (also a test seam). */
    void selectResolved(Player player, TrimMaterial material, TrimPattern pattern) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        ItemStack next = applyOrNull(draft, applier, material, pattern);
        if (next == null || !stageIfArmor(session, next)) return;
        draft = next;
        captureSelection(next);
        updatePreview();
        refresh();
    }

    void remove(Player player) {
        if (!rulesOf(gui).enabled()) {
            send(gui, player, "error.cosmetics-disabled");
            return;
        }
        ItemStack next = removeOrNull(draft, applier);
        if (next == null || !stageIfArmor(session, next)) return;
        draft = next;
        // No stale "Selected" indicator after the trim is gone.
        selectedPattern = null;
        selectedMaterial = null;
        updatePreview();
        refresh();
    }

    void switchMode() {
        mode = mode == Mode.PATTERN ? Mode.MATERIAL : Mode.PATTERN;
        page = 0;
        updatePreview();
        refresh();
    }

    /**
     * Rebuilds the display preview; a failed resolution keeps the last valid one. With no
     * explicit pattern/material (fresh or after Remove) the draft is shown untouched, so a
     * cleared trim cannot silently reappear through the first-key fallback.
     */
    private void updatePreview() {
        ItemStack next;
        if (selectedPattern == null || selectedMaterial == null) {
            next = GuiPreviewItems.displayClone(draft);
        } else {
            next = preview(draft, applier,
                    CosmeticCatalog.trimMaterial(selectedMaterial),
                    CosmeticCatalog.trimPattern(selectedPattern));
        }
        if (next != null) preview = next;
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

    // ---- package-local paint seams for contract tests (production never calls them) ----

    void paintForTest() {
        contentPane.clear();
        chromePane.clear();
        renderContent(contentPane);
        renderChrome(chromePane);
    }

    Map<Integer, GuiItem> paintedContent() {
        return paintedSlots(contentPane);
    }

    Map<Integer, GuiItem> paintedChrome() {
        return paintedSlots(chromePane);
    }

    private static Map<Integer, GuiItem> paintedSlots(StaticPane pane) {
        Map<Integer, GuiItem> slots = new HashMap<>();
        pane.getSlottedItems().forEach((slot, item) -> slots.put(
                slot.getX(GuiLayouts.COLUMNS) + slot.getY(GuiLayouts.COLUMNS) * GuiLayouts.COLUMNS, item));
        return Map.copyOf(slots);
    }

    ItemStack previewForTest() {
        return preview;
    }

    boolean hasSelectionForTest() {
        return selectedPattern != null || selectedMaterial != null;
    }
}
