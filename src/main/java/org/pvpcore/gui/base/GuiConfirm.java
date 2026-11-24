package org.pvpcore.gui.base;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.util.kits.SoundManager;

/**
 * Confirmation dialog. 4 rows: top glass, question + choices, dock glass.
 * Destructive questions keep confirm red and cancel green.
 */
public final class GuiConfirm {

    private GuiConfirm() {
    }

    /** Simple yes/no question with no details and no permission check. */
    public static void open(Player player, String title, Runnable onConfirm, Runnable onCancel) {
        open(player, title, new String[0], true, null, onConfirm, onCancel);
    }

    /**
     * @param details    short lines explaining what will happen (may be empty)
     * @param danger     destructive question: confirm is red, cancel is green
     * @param permission checked on every click, or null for none
     */
    public static void open(Player player, String title, String[] details, boolean danger, String permission,
                            Runnable onConfirm, Runnable onCancel) {
        new Screen(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(GuiConfirm.class)),
                player, title, details, danger, permission, onConfirm, onCancel).show();
    }

    private static final class Screen extends GuiScreen {
        private final String heading;
        private final String[] details;
        private final boolean danger;
        private final Runnable onConfirm;
        private final Runnable onCancel;

        private Screen(GuiSession session, Player player, String heading, String[] details, boolean danger,
                       String permission, Runnable onConfirm, Runnable onCancel) {
            super(session, player, GuiLayouts.ROWS_CONFIRM, GuiFrame.Density.BORDER, permission, null);
            this.heading = heading;
            this.details = details == null ? new String[0] : details;
            this.danger = danger;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        @Override
        protected String title() {
            if (kits() != null) {
                return GuiMenuFactory.kitRoomTitle(kits(), heading, true);
            }
            return heading;
        }

        @Override
        protected void renderContent(StaticPane content) {
            String[] lore = new String[details.length];
            for (int i = 0; i < details.length; i++) {
                lore[i] = GuiTheme.dim(details[i]);
            }
            content.addItem(wrap(GuiTheme.named(danger ? Material.TNT : Material.PAPER,
                    GuiTheme.GOLD + "<bold>", "Are you sure?", lore), (p, e) -> { }, player),
                    4, GuiLayouts.contentRow(0));

            ItemStackPair pair = danger
                    ? new ItemStackPair(
                            GuiTheme.named(Material.RED_CONCRETE, GuiTheme.RED + "<bold>", "Confirm",
                                    GuiTheme.dim("This can't be undone"), GuiTheme.hint("Click", "confirm")),
                            GuiTheme.named(Material.LIME_CONCRETE, GuiTheme.GREEN + "<bold>", "Cancel",
                                    GuiTheme.hint("Click", "go back")))
                    : new ItemStackPair(
                            GuiTheme.named(Material.LIME_CONCRETE, GuiTheme.GREEN + "<bold>", "Confirm",
                                    GuiTheme.hint("Click", "confirm")),
                            GuiTheme.named(Material.GRAY_CONCRETE, GuiTheme.MUTED + "<bold>", "Cancel",
                                    GuiTheme.hint("Click", "go back")));

            content.addItem(wrap(pair.confirm, (p, e) -> {
                SoundManager.playClick(p);
                p.closeInventory();
                if (onConfirm != null) onConfirm.run();
            }, player), 2, GuiLayouts.contentRow(1));
            content.addItem(wrap(pair.cancel, (p, e) -> {
                SoundManager.playClick(p);
                p.closeInventory();
                if (onCancel != null) onCancel.run();
            }, player), 6, GuiLayouts.contentRow(1));
        }

        @Override
        protected void renderChrome(StaticPane chrome) {
            // The dock stays plain glass: the two choices are the only actions.
        }

        private record ItemStackPair(org.bukkit.inventory.ItemStack confirm, org.bukkit.inventory.ItemStack cancel) {
        }
    }
}
