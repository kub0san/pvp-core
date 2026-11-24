package org.pvpcore.gui.base;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every kit card, category tab and "new" card is built here.
 *
 * <pre>
 * Kit 3                      (name, colour = state)
 * ● Ready                    (status)
 * • Netherite Sword          (short preview)
 * • Totem of Undying x6
 * Click — load               (actions, no blank separators)
 * Right-click — edit
 * </pre>
 */
public final class GuiCard {
    private static final int PREVIEW_LINES = 3;

    public enum CardState {
        READY, PREVIEW, EMPTY, LOCKED, STAFF_EDIT
    }

    private GuiCard() {
    }

    /** Compatibility overload: no content preview. */
    public static ItemStack kitCard(Material icon, String name, CardState state) {
        return kitCard(icon, name, null, state);
    }

    public static ItemStack kitCard(Material icon, String name, ItemStack[] contents, CardState state) {
        List<String> lore = new ArrayList<>();

        lore.add(switch (state) {
            case READY -> GuiTheme.status(GuiTheme.GREEN, true, "Ready");
            case PREVIEW -> GuiTheme.status(GuiTheme.AQUA, true, "Premade");
            case EMPTY -> GuiTheme.status(GuiTheme.MUTED, false, "Empty");
            case LOCKED -> GuiTheme.status(GuiTheme.RED, false, "Locked");
            case STAFF_EDIT -> GuiTheme.status(GuiTheme.GOLD, true, "Staff");
        });

        if (state != CardState.LOCKED) {
            Map<Material, Integer> totals = new LinkedHashMap<>();
            if (contents != null) {
                for (ItemStack stack : contents) {
                    if (stack != null && !stack.getType().isAir()) {
                        totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
                    }
                }
            }
            if (!totals.isEmpty()) {
                int shown = 0;
                for (Map.Entry<Material, Integer> e : totals.entrySet()) {
                    if (shown++ == PREVIEW_LINES) break;
                    lore.add(GuiTheme.entry(prettify(e.getKey()), e.getValue()));
                }
                int hidden = totals.size() - PREVIEW_LINES;
                if (hidden > 0) {
                    lore.add(GuiTheme.dim(GuiTheme.BULLET + " +" + hidden + " more"));
                }
            }
        }

        switch (state) {
            case READY -> {
                lore.add(GuiTheme.hint("Click", "load"));
                lore.add(GuiTheme.hint("Right-click", "edit"));
            }
            case PREVIEW -> {
                lore.add(GuiTheme.hint("Click", "preview"));
                lore.add(GuiTheme.hint("Shift-click", "load"));
            }
            case EMPTY -> lore.add(GuiTheme.hint("Click", "add items"));
            case LOCKED -> lore.add(GuiTheme.dim("Unavailable"));
            case STAFF_EDIT -> {
                lore.add(GuiTheme.hint("Click", "preview"));
                lore.add(GuiTheme.hint("Right-click", "manage"));
            }
        }

        String color = switch (state) {
            case READY -> GuiTheme.TEXT;
            case PREVIEW -> GuiTheme.AQUA;
            case EMPTY -> GuiTheme.MUTED;
            case LOCKED -> GuiTheme.RED;
            case STAFF_EDIT -> GuiTheme.GOLD;
        };
        return GuiTheme.named(icon, color + "<bold>", name, lore.toArray(new String[0]));
    }

    /** A category tab with no counters (compatibility). */
    public static ItemStack categoryTab(Material icon, String name, boolean staff) {
        return categoryTab(icon, name, false, -1, staff);
    }

    /** A category tab. The selected one glows and says so; {@code kitCount < 0} hides the counter. */
    public static ItemStack categoryTab(Material icon, String name, boolean selected, int kitCount, boolean staff) {
        List<String> lore = new ArrayList<>();
        if (selected) {
            lore.add(GuiTheme.status(GuiTheme.ACCENT, true, "Viewing"));
        } else if (kitCount >= 0) {
            lore.add(GuiTheme.value("Kits", String.valueOf(kitCount)));
        }
        if (staff) {
            lore.add(GuiTheme.hint("Right-click", "manage"));
        }
        ItemStack item = GuiTheme.named(icon, (selected ? GuiTheme.ACCENT : GuiTheme.TEXT) + "<bold>", name,
                lore.toArray(new String[0]));
        return selected ? GuiTheme.glow(item) : item;
    }

    public static ItemStack newKitCard() {
        return newCard(Material.WRITABLE_BOOK, "New kit", null);
    }

    public static ItemStack newKitCard(int slot) {
        return newCard(Material.WRITABLE_BOOK, "New kit", slot);
    }

    public static ItemStack newEnderChestCard(int slot) {
        return newCard(Material.ENDER_EYE, "New ender chest", slot);
    }

    public static ItemStack newCategoryTab() {
        return newCard(Material.WRITABLE_BOOK, "New category", null);
    }

    private static ItemStack newCard(Material icon, String name, Integer slot) {
        List<String> lore = new ArrayList<>();
        if (slot != null) lore.add(GuiTheme.dim("Slot " + slot));
        lore.add(GuiTheme.hint("Click", "create"));
        return GuiTheme.named(icon, GuiTheme.GREEN + "<bold>", "+ " + name, lore.toArray(new String[0]));
    }

    private static String prettify(Material material) {
        StringBuilder out = new StringBuilder();
        for (String word : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
