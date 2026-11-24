/*

 * Copyright 2022-2026 Noah Ross
 *
 * This file is part of Kits.
 *
 * Kits is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kits is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Kits. If not, see <https://www.gnu.org/licenses/>.
 */
package org.pvpcore.gui.base;

import org.bukkit.entity.Player;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.Lang;
import org.pvpcore.util.kits.StyleManager;

/** Builds the finished, formatted inventory title strings for the kits screens. */
public final class GuiMenuFactory {
    private GuiMenuFactory() {
    }

    private static String title(KitsContext kits, String key) {
        StyleManager style = kits.styleManager();
        Lang lang = kits.lang();
        return style.getPrimaryColor() + StyleManager.convertMiniMessage(lang.raw(key));
    }

    private static String title(KitsContext kits, String key, String... pairs) {
        StyleManager style = kits.styleManager();
        Lang lang = kits.lang();
        return style.getPrimaryColor() + StyleManager.convertMiniMessage(lang.raw(key, pairs));
    }

    /** Title for a screen, resolved from a Lang key with the brand colour. */
    public static String langTitle(KitsContext kits, String key, String... pairs) {
        return title(kits, key, pairs);
    }

    /** The finished title string of the premade kits list. */
    public static String publicKitRoomTitle(KitsContext kits) {
        return title(kits, "gui.public-kit-room-title");
    }

    /** The finished title string of the premade kit preview. */
    public static String viewPublicKitTitle(KitsContext kits, String id) {
        return title(kits, "gui.view-public-kit-title", "id", id);
    }

    /** Title for any screen: the brand colour plus MiniMessage text, e.g. "Premade Kits". */
    public static String sectionTitle(StyleManager style, String miniMessageText) {
        return style.getPrimaryColor() + StyleManager.convertMiniMessage(miniMessageText);
    }

    /** Title for any screen: the brand colour plus MiniMessage text, e.g. "Premade Kits". */
    public static String sectionTitle(KitsContext kits, String miniMessageText) {
        return sectionTitle(kits.styleManager(), miniMessageText);
    }

    /** Title for the focused action screen belonging to one personal kit slot. */
    public static String kitSlotActionsTitle(KitsContext kits, int slot) {
        return title(kits, "gui.kit-slot-name", "slot", String.valueOf(slot));
    }

    /** Main-menu title, including page information when there are multiple pages. */
    public static String mainMenuTitle(KitsContext kits, Player player, int page, int pages) {
        if (pages <= 1) {
            return title(kits, "gui.main-menu-title", "player", player.getName());
        }
        return title(kits, "gui.main-menu-title-paged",
                "player", player.getName(),
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(pages));
    }

    /** Finished, optionally centered title for a kit-room screen. */
    public static String kitRoomTitle(StyleManager style, String customTitle, boolean center) {
        String finalTitle = style.getPrimaryColor() + StyleManager.convertMiniMessage(customTitle);
        return center ? centerText(finalTitle) : finalTitle;
    }

    /** Finished, optionally centered title for a kit-room screen. */
    public static String kitRoomTitle(KitsContext kits, String customTitle, boolean center) {
        return kitRoomTitle(kits.styleManager(), customTitle, center);
    }

    /**
     * Estimates the title's visible width before adding leading spaces.
     * Formatting codes are removed first so they do not skew centering.
     */
    private static String centerText(String text) {
        String plainText = text
                .replaceAll("(?i)(?:\u00a7x|&x)(?:[\u00a7&][0-9a-f]){6}", "")
                .replaceAll("(?i)[\u00a7&][0-9a-fk-orx]", "")
                .replaceAll("<[^>]*>", "");

        int lengthPixels = 0;
        for (char c : plainText.toCharArray()) {
            if (c == 'i' || c == '!' || c == '.' || c == ',') {
                lengthPixels += 2;
            } else if (c == 'W' || c == 'M' || c == 'm') {
                lengthPixels += 6;
            } else {
                lengthPixels += 5;
            }
        }

        int spacesToAdd = Math.max(0, (88 - (lengthPixels / 2)) / 4);
        return " ".repeat(spacesToAdd) + text;
    }
}
