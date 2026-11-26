package org.pvpcore.gui.kits.editor;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pvpcore.commands.kits.core.ActionGuards;
import org.pvpcore.config.kits.CustomizeRules;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.cosmetics.ApplyResult;
import org.pvpcore.features.kits.cosmetics.CosmeticApplier;
import org.pvpcore.features.kits.cosmetics.CosmeticValidator;
import org.pvpcore.features.kits.cosmetics.CustomizeService;
import org.pvpcore.features.kits.cosmetics.CustomizeSession;
import org.pvpcore.gui.base.GuiChatPrompt;
import org.pvpcore.gui.base.GuiClick;
import org.pvpcore.gui.base.GuiFrame;
import org.pvpcore.gui.base.GuiGuard;
import org.pvpcore.gui.base.GuiLayouts;
import org.pvpcore.gui.base.GuiMenuFactory;
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
import java.util.Set;
import java.util.UUID;

/**
 * One-row hub for the single kit item selected by a shift-click in the editor.
 *
 * <p>There is no 41-slot target picker and no mode tabs: the selected slot stays the session
 * target, and every action (rename, enchant, trim, apply, cancel) acts on that one item. The
 * screen is deliberately read-only and 27 slots so it cannot be used to shuffle kit contents.</p>
 */
public final class CustomizePage extends GuiScreen {
    private static final int HUB_ROWS = GuiLayouts.COSMETIC_HUB_ROWS;
    private static final int HUB_ROW = GuiLayouts.COSMETIC_HUB_RENAME_SLOT / GuiLayouts.COLUMNS;
    private static final int RENAME_COL = GuiLayouts.COSMETIC_HUB_RENAME_SLOT % GuiLayouts.COLUMNS;
    private static final int ENCHANT_COL = GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT % GuiLayouts.COLUMNS;
    private static final int TRIM_COL = GuiLayouts.COSMETIC_HUB_TRIM_SLOT % GuiLayouts.COLUMNS;
    private static final int APPLY_COL = GuiLayouts.COSMETIC_HUB_APPLY_SLOT % GuiLayouts.COLUMNS;
    private static final int CANCEL_COL = GuiLayouts.COSMETIC_HUB_CANCEL_SLOT % GuiLayouts.COLUMNS;

    /** Slots owned by a card or an action button; the low-priority frame never fills them. */
    private static final Set<Integer> RESERVED_SLOTS = Set.of(
            GuiLayouts.COSMETIC_HUB_INFO_SLOT, GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT,
            GuiLayouts.COSMETIC_HUB_STATE_SLOT, GuiLayouts.COSMETIC_HUB_RENAME_SLOT,
            GuiLayouts.COSMETIC_HUB_ENCHANT_SLOT, GuiLayouts.COSMETIC_HUB_TRIM_SLOT,
            GuiLayouts.COSMETIC_HUB_APPLY_SLOT, GuiLayouts.COSMETIC_HUB_CANCEL_SLOT,
            GuiLayouts.COSMETIC_HUB_HINT_SLOT);

    private final GUI gui;
    private final int kitSlot;
    private final int slot;
    private final CustomizeSession session;

    private boolean writePending;
    private boolean chatPending;

    private CustomizePage(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        super(gui, player, HUB_ROWS, GuiFrame.Density.NONE, "perplayerkit.kit", LocationFeature.KITS);
        this.gui = gui;
        this.session = session;
        this.slot = slot;
        this.kitSlot = kitSlot;
        // A reopened session keeps its selection; a fresh hub entry selects the clicked item.
        if (session != null && slot >= 0 && slot < GuiLayouts.KIT_CONTENT_END) {
            session.selectTarget(slot);
        }
        gui.registerCustomizeScreen(player.getUniqueId());
    }

    /** Fresh entry from the editor: a previous reload marker no longer applies. */
    public static void open(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (!canOpen(gui, player, session)) return;
        gui.clearCustomizeStale(player.getUniqueId());
        new CustomizePage(gui, player, session, slot, kitSlot).show();
    }

    /** Reopens the hub with an existing session (rename return, save failure); stale state stays. */
    public static void reopen(GUI gui, Player player, CustomizeSession session, int slot, int kitSlot) {
        if (!canOpen(gui, player, session)) return;
        new CustomizePage(gui, player, session, slot, kitSlot).show();
    }

    private static boolean canOpen(GUI gui, Player player, CustomizeSession session) {
        if (gui == null || player == null || session == null) return false;
        if (!ActionGuards.allowed(player, "perplayerkit.kit", LocationFeature.KITS)) return false;
        if (gui.kits() == null) return false;
        if (!gui.kits().itemFilter().isReady()) {
            gui.kits().lang().send(player, "error.kitroom-not-ready");
            return false;
        }
        if (!rulesOf(gui).enabled()) {
            gui.kits().lang().send(player, "error.cosmetics-disabled");
            return false;
        }
        return true;
    }

    @Override
    protected String title() {
        return renderedTitle(kits(), kitSlot);
    }

    /**
     * Title seam: the kit slot is always substituted, so neither this screen nor a reopened
     * one can show a literal {@code {slot}} token from the message template.
     */
    static String renderedTitle(KitsContext kits, int slot) {
        if (kits == null || kits.lang() == null) return "Customize Item";
        return GuiMenuFactory.langTitle(kits, "gui.customize-title", "slot", String.valueOf(slot));
    }

    private static CustomizeRules rulesOf(GUI gui) {
        return CustomizeRules.parse(gui == null || gui.kits() == null || gui.kits().config() == null
                ? null : gui.kits().config().getConfigurationSection("customize"));
    }

    private static void send(GUI gui, Player player, String key) {
        if (gui != null && gui.kits() != null) gui.kits().lang().send(player, key);
    }

    /** Trims are only offered for genuine armor; shields and weapons must never see the button. */
    public static boolean supportsTrim(ItemStack item) {
        return new CosmeticValidator(CustomizeRules.parse(null)).isArmor(item);
    }

    @Override
    protected void renderFrame(StaticPane frame) {
        super.renderFrame(frame);
        // Decoration lives at LOW priority, so a card or button can always paint over it.
        // Reserved slots are left empty here: they belong to content or chrome, never filler.
        ItemStack glass = GuiTheme.filler(kits());
        for (int raw = 0; raw < GuiLayouts.COSMETIC_HUB_SIZE; raw++) {
            if (RESERVED_SLOTS.contains(raw)) continue;
            frame.addItem(new GuiItem(glass.clone(), gui.plugin()),
                    raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
        }
    }

    @Override
    protected void renderContent(StaticPane content) {
        // No content picker: the item is selected in the editor before this screen opens.
        paintCard(content, GuiLayouts.COSMETIC_HUB_INFO_SLOT, Material.PAPER, "Selected item",
                List.of(
                        line("Kit " + kitSlot, NamedTextColor.AQUA, true),
                        line("Slot " + slot + " - selected item", NamedTextColor.GRAY, false),
                        deserialize(message("gui.customize-preview-readonly"))));

        // Showcase: a protected, display-only clone. No click handler and no theme styling,
        // so enchants, trims and the staged name are exactly what the player will save.
        ItemStack display = GuiPreviewItems.displayClone(session == null ? null : session.target());
        content.addItem(displayItem(display == null ? GuiTheme.filler(kits()) : display),
                GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT % GuiLayouts.COLUMNS,
                GuiLayouts.COSMETIC_HUB_PREVIEW_SLOT / GuiLayouts.COLUMNS);

        boolean staged = session != null && session.isStaged(session.targetSlot());
        paintCard(content, GuiLayouts.COSMETIC_HUB_STATE_SLOT,
                staged ? Material.LIME_DYE : Material.GRAY_DYE, "Staged state",
                List.of(deserialize(message(staged
                        ? "gui.customize-staged" : "gui.customize-unstaged"))));

        paintCard(content, GuiLayouts.COSMETIC_HUB_HINT_SLOT, Material.PAPER, "How to use",
                List.of(deserialize(message("gui.customize-hub-hint"))));
    }

    private void paintCard(StaticPane pane, int raw, Material material, String name,
                           List<Component> lore) {
        pane.addItem(displayItem(card(material, name, lore)),
                raw % GuiLayouts.COLUMNS, raw / GuiLayouts.COLUMNS);
    }

    /** A GuiItem with no click handler, used for showcases and information cards. */
    private GuiItem displayItem(ItemStack item) {
        return new GuiItem(item, gui.plugin());
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
            case "gui.customize-preview-readonly" -> "Preview - read only";
            case "gui.customize-staged" -> "Staged changes";
            case "gui.customize-unstaged" -> "No staged changes";
            case "gui.customize-hub-hint" -> "Back keeps staged edits; Cancel discards them";
            default -> key;
        };
    }

    private static Component deserialize(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    // ---- package-local paint seams for contract tests (production never calls them) ----

    void paintForTest() {
        framePane.clear();
        contentPane.clear();
        chromePane.clear();
        renderFrame(framePane);
        renderContent(contentPane);
        renderChrome(chromePane);
    }

    Map<Integer, GuiItem> paintedFrame() {
        return paintedSlots(framePane);
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

    private enum Action { RENAME, ENCHANT, TRIM, APPLY, CANCEL }

    /** Testable description of the one-row hub: column, icon and the action it performs. */
    record HubButton(int column, Material icon, Action action) {
    }

    static List<HubButton> hubButtons(CustomizeSession session) {
        List<HubButton> buttons = new ArrayList<>();
        buttons.add(new HubButton(RENAME_COL, Material.NAME_TAG, Action.RENAME));
        buttons.add(new HubButton(ENCHANT_COL, Material.ENCHANTED_BOOK, Action.ENCHANT));
        if (session != null && supportsTrim(session.target())) {
            buttons.add(new HubButton(TRIM_COL, Material.NETHERITE_INGOT, Action.TRIM));
        }
        buttons.add(new HubButton(APPLY_COL, Material.LIME_DYE, Action.APPLY));
        buttons.add(new HubButton(CANCEL_COL, Material.BARRIER, Action.CANCEL));
        return List.copyOf(buttons);
    }

    private static ItemStack buttonItem(HubButton button) {
        return switch (button.action()) {
            case RENAME -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Rename",
                    GuiTheme.dim("Change this item's name"), GuiTheme.hint("Click", "type a name"));
            case ENCHANT -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Enchant",
                    GuiTheme.dim("Edit enchantments"), GuiTheme.hint("Click", "open"));
            case TRIM -> GuiTheme.named(button.icon(), GuiTheme.AQUA + "<bold>", "Trim",
                    GuiTheme.dim("Edit armor trim"), GuiTheme.hint("Click", "open"));
            case APPLY -> GuiTheme.named(button.icon(), GuiTheme.GREEN + "<bold>", "Apply",
                    GuiTheme.dim("Commit staged changes"), GuiTheme.hint("Click", "save"));
            case CANCEL -> GuiTheme.named(button.icon(), GuiTheme.RED + "<bold>", "Cancel",
                    GuiTheme.dim("Discard changes and go back"), GuiTheme.hint("Click", "back"));
        };
    }

    private GuiClick handler(Action action) {
        return switch (action) {
            // A queued save owns the pending operation; renaming or applying again would
            // overwrite it before the outcome callback can resolve it.
            case RENAME -> (p, e) -> {
                if (writePending) return;
                openRename(p);
            };
            case ENCHANT -> (p, e) -> openEnchantments(p);
            case TRIM -> (p, e) -> openTrims(p);
            case APPLY -> (p, e) -> {
                if (writePending) return;
                apply(p);
            };
            case CANCEL -> (p, e) -> cancel(p);
        };
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        for (HubButton button : hubButtons(session)) {
            GuiClick action = handler(button.action());
            chrome.addItem(clickable(buttonItem(button), (p, e) -> {
                if (writePending && (button.action() == Action.RENAME || button.action() == Action.APPLY)) {
                    return;
                }
                SoundManager.playClick(p);
                action.on(p, e);
            }), button.column(), HUB_ROW);
        }
    }

    /**
     * Explicit plugin owner instead of InventoryFramework's classloader lookup: the jar is
     * relocated into this plugin, but standalone tests load IF from the test classpath.
     */
    private GuiItem clickable(ItemStack item, GuiClick click) {
        return new GuiItem(item, event -> {
            Player clicker = event.getWhoClicked() instanceof Player p ? p : player;
            click.on(clicker, event);
        }, gui.plugin());
    }

    // ------------------------------------------------------------------ actions

    private void openRename(Player p) {
        if (writePending || chatPending) {
            return;
        }
        if (!rulesOf(gui).enabled()) {
            send(gui, p, "error.cosmetics-disabled");
            return;
        }
        UUID viewer = p.getUniqueId();
        if (gui.isCustomizeStale(viewer)) {
            send(gui, p, "error.cosmetics-kit-changed");
            return;
        }
        ItemStack target = session.target();
        if (target == null || target.getType().isAir()) {
            send(gui, p, "error.cosmetics-name-invalid");
            return;
        }
        UUID operationId = gui.holdPendingCustomize(viewer, session);
        // The prompt closes this screen; keep its staged edits alive until the callback arrives.
        chatPending = true;
        GuiChatPrompt.askRename(gui.plugin(), p,
                text -> {
                    chatPending = false;
                    CustomizeSession taken = stageRename(gui, p, operationId, text);
                    if (taken != null) CustomizePage.reopen(gui, p, taken, slot, kitSlot);
                },
                () -> cancelRename(p, operationId));
    }

    /**
     * Consumes the pending rename operation and stages the parsed name on its selected target.
     * A stale/mismatched operation returns null so no screen can be revived after a reload.
     */
    static CustomizeSession stageRename(GUI gui, Player player, UUID operationId, String text) {
        if (gui == null || player == null || operationId == null) return null;
        CustomizeSession taken = gui.takePendingCustomize(player.getUniqueId(), operationId);
        if (taken == null) return null;
        ItemStack current = taken.target();
        if (current == null || current.getType().isAir()) return taken;
        CustomizeRules rules = rulesOf(gui);
        CosmeticApplier applier = new CosmeticApplier(rules, new CosmeticValidator(rules));
        try {
            taken.stageTarget(applier.applyName(current, text));
        } catch (CosmeticValidator.BlockedTagException ex) {
            send(gui, player, "error.cosmetics-name-tag-blocked");
        } catch (IllegalArgumentException ex) {
            send(gui, player, "error.cosmetics-name-invalid");
        }
        return taken;
    }

    /** Cancels a pending rename; a stale callback must neither reopen nor consume the operation. */
    boolean cancelRename(Player p, UUID operationId) {
        chatPending = false;
        CustomizeSession taken = gui.takePendingCustomize(p.getUniqueId(), operationId);
        if (taken == null) return false;
        CustomizePage.reopen(gui, p, taken, slot, kitSlot);
        return true;
    }

    private void openEnchantments(Player p) {
        EnchantmentEditorPage.open(gui, p, session, slot, kitSlot);
    }

    /** Opens the compact trim screen; it re-checks armor before showing anything. */
    private void openTrims(Player p) {
        TrimEditorPage.open(gui, p, session, slot, kitSlot);
    }

    private void apply(Player p) {
        if (writePending || chatPending) return;
        CustomizeRules rules = rulesOf(gui);
        if (!rules.enabled()) {
            send(gui, p, "error.cosmetics-disabled");
            return;
        }
        UUID viewer = p.getUniqueId();
        if (gui.isCustomizeStale(viewer)) {
            send(gui, p, "error.cosmetics-kit-changed");
            return;
        }
        // Register without clearing a reload stale marker, then commit through the
        // outcome overload so the queued write can report back after close/quit.
        UUID operationId = gui.holdPendingCustomize(viewer, session);
        CustomizeService service = CustomizeService.create(gui);
        ApplyResult result = service.commit(p, kitSlot, session, success ->
                Bukkit.getScheduler().runTask(gui.plugin(), () -> {
                    CustomizeSession taken = gui.takePendingCustomize(viewer, operationId);
                    if (taken == null) return; // consumed, mismatched, or invalidated by reload
                    if (!p.isOnline()) {
                        if (!success) {
                            gui.plugin().getLogger().warning(
                                    "Late kit cosmetics save failure for " + viewer);
                        }
                        return;
                    }
                    if (success) {
                        send(gui, p, "info.cosmetics-applied");
                        ItemEditorPage.openKit(gui, p, kitSlot);
                    } else {
                        // The write was queued but not confirmed. The cache already holds the
                        // staged result, so rebase onto it: otherwise a retry is rejected as
                        // KIT_CHANGED forever. A null manager or cache read keeps the taken
                        // session (and its staged edits) intact.
                        send(gui, p, "error.cosmetics-save-unconfirmed");
                        KitManager manager = gui.kits() == null ? null : gui.kits().kitManager();
                        ItemStack[] current = manager == null
                                ? null : manager.getPlayerKit(viewer, kitSlot);
                        CustomizePage.reopen(gui, p, taken.rebaseFrom(current), slot, kitSlot);
                    }
                }));
        if (result == ApplyResult.QUEUED) {
            writePending = true;
            send(gui, p, "info.cosmetics-save-queued");
            return; // the outcome callback closes the screen once the write resolves
        }
        gui.clearPendingCustomize(viewer);
        switch (result) {
            case PERMISSION_DENIED -> send(gui, p, "error.cosmetics-disabled");
            case KIT_CHANGED -> send(gui, p, "error.cosmetics-kit-changed");
            case INVALID_CONTENT -> send(gui, p, "error.cosmetics-name-invalid");
            case STORAGE_FAILED -> send(gui, p, "error.cosmetics-save-failed");
            case QUEUED -> { /* handled above */ }
        }
    }

    private void cancel(Player p) {
        gui.clearPendingCustomize(p.getUniqueId());
        session.discard();
        gui.flushOpenEditor(p);
        ItemEditorPage.openKit(gui, p, kitSlot);
    }

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.readOnly(chest, "perplayerkit.kit", LocationFeature.KITS, rows);
    }

    @Override
    protected void configure(ChestGui chest) {
        chest.setOnClose(event -> {
            if (!(event.getPlayer() instanceof Player closer)) return;
            UUID viewer = closer.getUniqueId();
            gui.unregisterCustomizeScreen(viewer);
            if (writePending) {
                // The queued write keeps its clone; only feedback and pending state are dropped.
                gui.clearPendingCustomize(viewer);
            } else if (chatPending) {
                // GuiChatPrompt holds the session until the typed name (or cancel) arrives.
                return;
            } else if (gui.isLastCustomizeScreen(viewer)) {
                session.discard();
            }
        });
    }
}
