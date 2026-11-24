package org.pvpcore.gui.base;

import org.bukkit.Material;
import org.pvpcore.features.kits.data.KitRoomDataManager;
import org.pvpcore.util.kits.BroadcastManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Schema for the framed settings pages: groups, definitions, and validation helpers.
 * Behaviour/staging lives in {@link GuiSettingsSupport}.
 */
public final class GuiSettingsSchema {
    public static final int GROUPS_PER_SCREEN = 18;
    public static final int SETTINGS_PER_SCREEN = 27;
    public static final int ENTRIES_PER_SCREEN = 27;

    public enum Kind { TOGGLE, NUMBER, CHOICE, TEXT, MATERIAL, LIST, LOCKED }
    public enum Impact { LIVE, RELOAD, RESTART }

    public record Def(Kind kind, String path, String label, String desc, String lockReason,
                      int min, int max, List<String> options, Impact impact, ListCheck check) {
    }

    public record Group(String name, Material icon, List<Def> defs) {
    }

    @FunctionalInterface
    public interface ListCheck {
        String problem(String entry);
    }

    private static final List<Group> GROUPS = buildGroups();

    private GuiSettingsSchema() {
    }

    public static List<Group> groups() {
        return GROUPS;
    }

    public static String groupMarker(Group group) {
        boolean reload = false;
        boolean restart = false;
        for (Def def : group.defs()) {
            if (def.impact() == Impact.RESTART) restart = true;
            else if (def.impact() == Impact.RELOAD) reload = true;
        }
        if (restart) return "Needs a restart";
        if (reload) return "Needs /pvp-core reload";
        return null;
    }

    public static String validMaterialEntry(String entry) {
        Material material = Material.matchMaterial(entry);
        if (material == null || !material.isItem()) {
            return entry + " is not an item name (e.g. TOTEM_OF_UNDYING). Nothing was added.";
        }
        return null;
    }

    // ================================================================== def builders

    public static DefBuilder def(Kind kind, String path, String label) {
        return new DefBuilder(kind, path, label);
    }

    public static DefBuilder toggle(String path, String label) {
        return def(Kind.TOGGLE, path, label);
    }

    public static DefBuilder number(String path, String label) {
        return def(Kind.NUMBER, path, label).bounds(0, Integer.MAX_VALUE);
    }

    public static DefBuilder choice(String path, String label) {
        return def(Kind.CHOICE, path, label).options(List.of("command", "shulker", "hybrid"));
    }

    public static DefBuilder text(String path, String label) {
        return def(Kind.TEXT, path, label);
    }

    public static DefBuilder list(String path, String label) {
        return def(Kind.LIST, path, label);
    }

    public static final class DefBuilder {
        private final Kind kind;
        private final String path;
        private final String label;
        private String desc;
        private String lockReason;
        private int min;
        private int max = Integer.MAX_VALUE;
        private List<String> options = List.of();
        private Impact impact = Impact.LIVE;
        private ListCheck check;

        private DefBuilder(Kind kind, String path, String label) {
            this.kind = kind;
            this.path = path;
            this.label = label;
        }

        public DefBuilder about(String desc) { this.desc = desc; return this; }
        public DefBuilder lock(String reason) { this.lockReason = reason; return this; }
        public DefBuilder bounds(int min, int max) { this.min = min; this.max = max; return this; }
        public DefBuilder options(List<String> options) { this.options = options; return this; }
        public DefBuilder impact(Impact impact) { this.impact = impact; return this; }
        public DefBuilder check(ListCheck check) { this.check = check; return this; }

        public Def build() {
            return new Def(kind, path, label, desc, lockReason, min, max, options, impact, check);
        }
    }

    private static List<Group> buildGroups() {
        List<Group> out = new ArrayList<>();
        out.add(new Group("General", Material.COMMAND_BLOCK, List.of(
                def(Kind.LOCKED, "enabled", "Kits module").lock("Locked while running; edit the file and restart.").build(),
                number("kits.max-slots", "Max kits").bounds(1, 99).impact(Impact.RELOAD).build()
        )));
        out.add(new Group("Kits", Material.CHEST, List.of(
                toggle("kits.load.heal", "Heal on kit load").build(),
                toggle("kits.load.feed", "Feed on kit load").build(),
                toggle("kits.load.saturate", "Saturate on kit load").build(),
                toggle("kits.load.clear-effects", "Clear effects on kit load").build(),
                toggle("kits.load.apply-potion-effects", "Apply kit potion effects").build()
        )));
        out.add(new Group("Enderchests", Material.ENDER_CHEST, List.of(
                toggle("enderchests.load.heal", "Heal on chest load").build(),
                toggle("enderchests.load.feed", "Feed on chest load").build(),
                toggle("enderchests.load.saturate", "Saturate on chest load").build(),
                toggle("enderchests.load.clear-effects", "Clear effects on chest load").build()
        )));
        out.add(new Group("Rekit", Material.CLOCK, List.of(
                toggle("rekit.respawn.enabled", "Kit on respawn").build(),
                number("rekit.respawn.delay-ticks", "Respawn delay").about("Ticks to wait; 0 is instant.").build(),
                toggle("rekit.respawn.restore-enderchest", "Respawn keeps enderchest").build(),
                toggle("rekit.kill.enabled", "Kit on kill").build(),
                toggle("rekit.kill.restore-enderchest", "Kill keeps enderchest").build()
        )));
        out.add(new Group("Kit room", Material.BEACON, List.of(
                number("kitroom.pages", "Default pages").bounds(1, 99).impact(Impact.RELOAD).build()
        )));
        out.add(new Group("Items", Material.NETHERITE_INGOT, List.of(
                toggle("item-filter.enabled", "Item filter").build(),
                toggle("item-filter.kit-room-only", "Only kit room items").build(),
                toggle("item-filter.filter-imports", "Filter imports").build(),
                toggle("item-filter.filter-saves", "Filter saves").build(),
                toggle("item-filter.allow-unbreakable", "Allow unbreakable").build(),
                toggle("item-filter.allow-custom-attributes", "Allow custom attributes").build(),
                toggle("item-filter.allow-item-flags", "Allow item flags").build(),
                toggle("item-filter.enchantments.allow-over-levelled", "Allow over-levelled enchants").build(),
                toggle("item-filter.enchantments.allow-incompatible", "Allow incompatible enchants").build(),
                number("item-filter.potions.max-level", "Potion level cap").bounds(0, 255).about("0 means no cap.").build(),
                number("item-filter.potions.max-duration-seconds", "Potion duration cap").about("0 means no cap; overrides infinite effects.").build()
        )));
        out.add(new Group("Anti-exploit", Material.SHIELD, List.of(
                toggle("anti-exploit.block-spaces-in-commands", "Block spaces in commands").build(),
                toggle("anti-exploit.prevent-shulkers-dropping-items", "Shulkers keep contents").build()
        )));
        out.add(new Group("Regear", Material.SHULKER_BOX, List.of(
                choice("regear.modes.rg", "/rg mode").build(),
                choice("regear.modes.regear", "/regear mode").build(),
                number("regear.command-cooldown-seconds", "Command cooldown").build(),
                number("regear.damage-cooldown-seconds", "Damage cooldown").build(),
                toggle("regear.allow-while-using-elytra", "Allow while flying elytra").build(),
                toggle("regear.prevent-putting-items-in-regear-inventory", "Block items in regear GUI").build(),
                toggle("regear.restore-exact-positions", "Restore exact positions").build(),
                toggle("regear.invert-whitelist", "Invert whitelist").build(),
                list("regear.whitelist", "Whitelist").check(GuiSettingsSchema::validMaterialEntry).build()
        )));
        out.add(new Group("Player", Material.GOLDEN_APPLE, List.of(
                toggle("heal.clear-effects", "Heal clears effects").build(),
                toggle("death.condensed-drops", "Condensed death drops").build(),
                toggle("updates.notify-admins-on-join", "Update notice for admins").build()
        )));
        out.add(broadcastGroup());
        out.add(new Group("Sounds", Material.NOTE_BLOCK, List.of(
                toggle("sounds.enabled", "Sounds").build(),
                text("sounds.success", "Success sound").build(),
                text("sounds.failure", "Failure sound").build(),
                text("sounds.click", "Click sound").build(),
                text("sounds.open-gui", "Open sound").build(),
                text("sounds.close-gui", "Close sound").build()
        )));
        out.add(new Group("Interface", Material.PINK_STAINED_GLASS_PANE, List.of(
                def(Kind.MATERIAL, "interface.glass-material", "Glass colour").impact(Impact.RELOAD).build(),
                def(Kind.TEXT, "interface.main-color", "Title colour").impact(Impact.RELOAD).build()
        )));
        out.add(new Group("Storage", Material.HOPPER, List.of(
                def(Kind.LOCKED, "storage.type", "Storage type")
                        .lock("Locked while running; edit the file and restart.").build(),
                toggle("storage.backup.enabled", "Automatic backups").about("SQLite and YAML only.")
                        .impact(Impact.RELOAD).build()
        )));
        return out;
    }

    private static Group broadcastGroup() {
        List<Def> defs = new ArrayList<>();
        defs.add(toggle("broadcasts.enabled", "Action messages").build());
        defs.add(toggle("broadcasts.use-display-name", "Use display name").build());
        defs.add(toggle("broadcasts.scheduled.enabled", "Scheduled messages").impact(Impact.RELOAD).build());
        defs.add(number("broadcasts.scheduled.period-seconds", "Scheduled period").bounds(1, Integer.MAX_VALUE)
                .impact(Impact.RELOAD).build());
        for (BroadcastManager.MessageKey key : BroadcastManager.MessageKey.values()) {
            String base = key.getConfigKey();
            String label = friendlyAction(key.name());
            defs.add(toggle(base + ".enabled", label + " message").build());
            defs.add(text(base + ".permission", label + " recipients").build());
        }
        return new Group("Messages", Material.BELL, List.copyOf(defs));
    }

    private static String friendlyAction(String name) {
        String label = name.replace("PLAYER_", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
