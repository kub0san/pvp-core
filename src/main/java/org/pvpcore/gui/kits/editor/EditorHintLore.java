package org.pvpcore.gui.kits.editor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.pvpcore.gui.base.GuiTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * Display-only hover hints for the items shown in an editor inventory.
 *
 * <p>Hints are written to a clone of the stored item and marked with a private persistent-data
 * key. The marker is the contract: only an item carrying it may have its lore rewritten, and
 * every exit path strips it before the stack is persisted, copied or exported. An unmarked item
 * whose lore happens to contain the same text is therefore never modified.</p>
 */
public final class EditorHintLore {
    private static final NamespacedKey MARKER = new NamespacedKey("pvp-core", "editor_hint");
    private static final NamespacedKey LINE_COUNT =
            new NamespacedKey("pvp-core", "editor_hint_lines");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** The only lore lines this helper may add or remove. */
    private static final List<Component> HINT_LINES = List.of(
            line("Edit: ", "Shift-Click"),
            line("Copy: ", "Right-Click"),
            line("Discard: ", "Drop"));

    private EditorHintLore() {
    }

    /** Adds the marker and hints to {@code item} in place, at most once. */
    public static ItemStack apply(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data.has(MARKER, PersistentDataType.BYTE)) {
            return item;
        }
        data.set(MARKER, PersistentDataType.BYTE, (byte) 1);
        data.set(LINE_COUNT, PersistentDataType.INTEGER, HINT_LINES.size());
        List<Component> withHints = new ArrayList<>();
        List<Component> existing = meta.lore();
        if (existing != null) {
            withHints.addAll(existing);
        }
        withHints.addAll(HINT_LINES);
        meta.lore(withHints);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Removes the marker and exactly the trailing lines this helper appended from {@code item}
     * in place. Unmarked items, including player-authored lore that merely looks like a hint,
     * are left untouched. The recorded count keeps player-authored lookalikes elsewhere in the
     * lore intact.
     */
    public static void strip(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(MARKER, PersistentDataType.BYTE)) {
            return;
        }
        Integer recorded = data.get(LINE_COUNT, PersistentDataType.INTEGER);
        // Items marked before the count existed always received the full hint block.
        int count = recorded == null ? HINT_LINES.size() : recorded;
        data.remove(MARKER);
        data.remove(LINE_COUNT);
        List<Component> lore = meta.lore();
        if (lore != null && count > 0 && lore.size() >= count) {
            List<Component> kept = new ArrayList<>(lore.subList(0, lore.size() - count));
            meta.lore(kept.isEmpty() ? null : kept);
        }
        item.setItemMeta(meta);
    }

    /** True only for an item carrying this helper's private marker. */
    public static boolean has(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }

    private static Component line(String label, String action) {
        return MINI_MESSAGE.deserialize("<!italic>" + GuiTheme.GOLD + label + GuiTheme.TEXT + action);
    }
}
