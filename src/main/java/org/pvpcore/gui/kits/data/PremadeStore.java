package org.pvpcore.gui.kits.data;

import org.pvpcore.gui.kits.*;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.features.kits.PublicKit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the premade kit screens need to read or change, in one place.
 *
 * Two kinds of data live here:
 *
 *  1. CATEGORIES. A pure menu feature, so they are stored in their own file,
 *     Kits/premade-categories.yml: the categories (id, name, icon, in order) and which
 *     category each premade kit belongs to. The kits plugin itself never sees them.
 *
 *  2. KITS. Premade kits are registered by the kits plugin (a list in KitManager and a
 *     "publickits.&lt;id&gt;" entry in the kits config). The four methods in the second half of
 *     this file (createKit, renameKit, setKitIcon, deleteKit) are the only code that
 *     creates or changes those, so if the plugin's own storage differs from what is
 *     written here, this is the only file that needs adjusting.
 */
public final class PremadeStore {
    private static final int NAME_MAX = 32;

    /** One category tab. */
    public record Category(String id, String name, Material icon) {
    }

    private static File file;
    private static KitsContext kits;
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> ASSIGNED = new HashMap<>();

    private PremadeStore() {
    }

    // ================================================================== categories

    /** Loads the categories file. Called once when the plugin starts. */
    public static void init(Plugin plugin, KitsContext kitsContext) {
        kits = kitsContext;
        file = new File(plugin.getDataFolder(), "Kits/premade-categories.yml");
        load();
    }

    private static void load() {
        CATEGORIES.clear();
        ASSIGNED.clear();
        if (file == null || !file.isFile()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection cats = yml.getConfigurationSection("categories");
        if (cats != null) {
            for (String id : cats.getKeys(false)) {
                String name = cats.getString(id + ".name", id);
                Material icon = Material.matchMaterial(cats.getString(id + ".icon", "CHEST"));
                CATEGORIES.put(id, new Category(id, name, icon == null ? Material.CHEST : icon));
            }
        }
        ConfigurationSection assigned = yml.getConfigurationSection("assigned");
        if (assigned != null) {
            for (String kitId : assigned.getKeys(false)) {
                String category = assigned.getString(kitId);
                if (category != null && CATEGORIES.containsKey(category)) {
                    ASSIGNED.put(kitId, category);
                }
            }
        }
    }

    private static void save() {
        if (file == null) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (Category category : CATEGORIES.values()) {
            yml.set("categories." + category.id() + ".name", category.name());
            yml.set("categories." + category.id() + ".icon", category.icon().name());
        }
        for (Map.Entry<String, String> entry : ASSIGNED.entrySet()) {
            yml.set("assigned." + entry.getKey(), entry.getValue());
        }
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ignored) {
            // the menu keeps working from memory; the next save tries again
        }
    }

    public static List<Category> categories() {
        return new ArrayList<>(CATEGORIES.values());
    }

    public static Category category(String id) {
        return id == null ? null : CATEGORIES.get(id);
    }

    /** The category a kit is filed under, or null for none. */
    public static String categoryOf(String kitId) {
        return ASSIGNED.get(kitId);
    }

    public static Category createCategory(String name) {
        String id = uniqueId(name, CATEGORIES.keySet());
        Category category = new Category(id, name, Material.CHEST);
        CATEGORIES.put(id, category);
        save();
        return category;
    }

    public static void renameCategory(String id, String name) {
        Category old = CATEGORIES.get(id);
        if (old == null) return;
        CATEGORIES.put(id, new Category(id, name, old.icon()));
        save();
    }

    public static void setCategoryIcon(String id, Material icon) {
        Category old = CATEGORIES.get(id);
        if (old == null) return;
        CATEGORIES.put(id, new Category(id, old.name(), icon));
        save();
    }

    /** Removes a category. Its kits are not deleted, they simply have no category any more. */
    public static void deleteCategory(String id) {
        CATEGORIES.remove(id);
        ASSIGNED.values().removeIf(id::equals);
        save();
    }

    /** Files a kit under a category. A null category removes it from its category. */
    public static void assign(String kitId, String categoryId) {
        if (categoryId == null) {
            ASSIGNED.remove(kitId);
        } else if (CATEGORIES.containsKey(categoryId)) {
            ASSIGNED.put(kitId, categoryId);
        }
        save();
    }

    // ================================================================== kits

    public static List<PublicKit> kits(KitsContext kitsContext) {
        return kitsContext.kitManager().getPublicKitList();
    }

    public static PublicKit kit(KitsContext kitsContext, String id) {
        return kitsContext.kitManager().findPublicKit(id);
    }

    /** Trims a typed name and rejects empty ones. Returns null when it is unusable. */
    public static String cleanName(String typed) {
        String name = typed == null ? "" : typed.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) return null;
        return name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
    }

    private static String uniqueId(String name, Iterable<String> taken) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) base = "kit";
        if (base.length() > 24) base = base.substring(0, 24);
        List<String> used = new ArrayList<>();
        taken.forEach(used::add);
        String id = base;
        int n = 2;
        while (used.contains(id)) {
            id = base + "-" + n++;
        }
        return id;
    }

    /** The four methods below are the only ones that touch the kits plugin's own data. ---- */

    /**
     * Registers a new, empty premade kit: writes its entry to the kits config and adds it to
     * the plugin's list, so it shows up at once. Returns the new kit.
     */
    public static PublicKit createKit(KitsContext kitsContext, String name, Material icon) {
        List<String> ids = new ArrayList<>();
        kits(kitsContext).forEach(k -> ids.add(k.id));
        String id = uniqueId(name, ids);

        kitsContext.config().set("publickits." + id + ".name", name);
        kitsContext.config().set("publickits." + id + ".icon", icon.name());
        kitsContext.saveConfig();

        PublicKit kit = new PublicKit(id, name, icon);
        kitsContext.kitManager().registerPublicKit(kit);
        return kit;
    }

    public static void renameKit(KitsContext kitsContext, PublicKit kit, String name) {
        kit.name = name;
        kitsContext.config().set("publickits." + kit.id + ".name", name);
        kitsContext.saveConfig();
    }

    public static void setKitIcon(KitsContext kitsContext, PublicKit kit, Material icon) {
        kit.icon = icon;
        kitsContext.config().set("publickits." + kit.id + ".icon", icon.name());
        kitsContext.saveConfig();
    }

    /** Removes the kit, its contents, and its category. */
    public static void deleteKit(KitsContext kitsContext, PublicKit kit) {
        kitsContext.kitManager().deletePublicKit(kit.id);
        kitsContext.kitManager().removePublicKit(kit);
        kitsContext.config().set("publickits." + kit.id, null);
        kitsContext.saveConfig();
        ASSIGNED.remove(kit.id);
        save();
    }
}
