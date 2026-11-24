/*

    Style Manager
        by kirushkinx (https://github.com/kirushkinx)

 */

package org.pvpcore.util.kits;
import org.pvpcore.config.Messages;
import org.pvpcore.util.TextUtil;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

public class StyleManager {
    private static StyleManager instance; // why: singleton instance pointer retained for legacy static lifecycle compatibility.
    private final Plugin plugin;
    private final org.pvpcore.features.kits.KitsContext context;

    private Material glassMaterial;
    private String titleColor;
    private String titleColorTag;

    public StyleManager(org.pvpcore.features.kits.KitsContext context) {
        this.context = java.util.Objects.requireNonNull(context, "context");
        this.plugin = context.plugin();
        instance = this;
        loadConfig();
    }

    private org.bukkit.configuration.file.FileConfiguration cfg() {
        return context.config();
    }

    /** why: static shutdown retained for module disable/reload lifecycle cleanup of singleton-style state. */
    public static void shutdown() {
        instance = null;
    }

    public void loadConfig() {
        org.bukkit.configuration.file.FileConfiguration c = cfg();
        try {
            this.glassMaterial = Material.valueOf(c != null
                    ? c.getString("interface.glass-material", "LIGHT_BLUE_STAINED_GLASS_PANE")
                    : "LIGHT_BLUE_STAINED_GLASS_PANE");
        } catch (IllegalArgumentException e) {
            this.glassMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        }

        String colorTag = c != null ? c.getString("interface.main-color", "<#6ea8d9>") : "<#6ea8d9>";
        this.titleColorTag = colorTag;
        this.titleColor = miniMessageToLegacy(colorTag);
    }

    private String miniMessageToLegacy(String miniMessage) {
        try {
            return TextUtil.formattingPrefix(miniMessage);
        } catch (Exception e) {
            return "§9";
        }
    }

    /** why: stateless text conversion utility retained for external/legacy compatibility. */
    public static String convertMiniMessage(String miniMessage) {
        try {
            return TextUtil.toLegacy(miniMessage);
        } catch (Exception e) {
            return miniMessage;
        }
    }

    public Material getGlassMaterial() {
        return glassMaterial;
    }

    public String getPrimaryColor() {
        return titleColor;
    }

    public String getPrimaryColorTag() {
        return titleColorTag;
    }
}