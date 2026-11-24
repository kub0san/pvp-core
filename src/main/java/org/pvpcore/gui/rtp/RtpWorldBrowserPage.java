package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every loaded world. Clicking an unregistered world registers it, clicking a registered one
 * opens its editor. The screen is as tall as the number of worlds needs.
 */
public final class RtpWorldBrowserPage extends GuiScreen {
    private final TeleportModule teleport;
    private final RtpModule rtp;
    private final RtpAppearance appearance;
    private final RtpNavigation nav;
    private final List<World> worlds;
    private final GuiPager pager;

    private RtpWorldBrowserPage(TeleportModule teleport, RtpModule rtp, Player player, int page) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpWorldBrowserPage.class)), player,
                GuiLayouts.rowsFor(RtpSupport.gridRows(Bukkit.getWorlds().size(), page, RtpSupport.GRID_PER_PAGE)),
                GuiFrame.Density.BORDER, null, null);
        this.teleport = teleport;
        this.rtp = rtp;
        this.nav = new RtpNavigation(teleport, rtp);
        this.appearance = RtpAppearance.shared();
        this.worlds = Bukkit.getWorlds().stream()
                .sorted(Comparator.comparing(World::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        this.pager = GuiPager.of(page, worlds.size(), RtpSupport.GRID_PER_PAGE);
    }

    static void open(TeleportModule teleport, RtpModule rtp, Player player, int page) {
        new RtpWorldBrowserPage(teleport, rtp, player, page).show();
        SoundManager.playOpenGui(player);
    }

    @Override
    protected String title() {
        if (kits() != null) return GuiMenuFactory.sectionTitle(kits(), "RTP <dark_gray>\u00BB <white>Worlds");
        return "RTP \u00BB Worlds";
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<GuiItem> cards = new ArrayList<>();
        for (World world : pager.slice(worlds)) {
            RtpRegion registered = RtpSupport.findRegionByWorld(rtp, world.getName());
            boolean exists = registered != null;

            Material icon = exists ? appearance.icon(registered) : RtpAppearance.defaultIcon(world.getName());
            String name = exists ? appearance.displayName(registered) : RtpAppearance.prettify(world.getName());

            cards.add(wrap(GuiTheme.named(icon, GuiTheme.TEXT + "<bold>", name,
                    exists ? GuiTheme.status(GuiTheme.GREEN, true, "Registered")
                            : GuiTheme.status(GuiTheme.MUTED, false, "Not registered"),
                    GuiTheme.value("World", world.getName()),
                    GuiTheme.value("Type", environment(world)),
                    GuiTheme.hint("Click", exists ? "edit" : "register")), (p, e) -> {
                SoundManager.playClick(p);
                RtpRegion current = RtpSupport.findRegionByWorld(rtp, world.getName());
                if (current != null) {
                    nav.openRegionSettings(p, current.id());
                } else if (registerWorld(world, p)) {
                    SoundManager.playSuccess(p);
                    nav.openManagement(p);
                }
            }, player));
        }
        RtpSupport.placeGrid(content, cards);
    }

    private static String environment(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "The End";
            case NORMAL -> "Overworld";
            default -> "Custom";
        };
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> nav.openManagement(p))
                .pager(pager, target -> nav.openWorldBrowser(player, target))
                .applyTo(chrome);
    }

    private boolean registerWorld(World world, Player player) {
        String regionId = RtpSupport.makeRegionId(rtp, world.getName());
        try {
            rtp.createRegion(regionId, world.getName());
        } catch (RuntimeException exception) {
            JavaPlugin.getProvidingPlugin(RtpWorldBrowserPage.class).getLogger()
                    .warning("Could not register RTP world '" + world.getName() + "': " + exception);
            player.sendMessage(MiniMessage.miniMessage().deserialize(
                    GuiTheme.fail("Could not register this world. Check the console for details.")));
            return false;
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                GuiTheme.ok("Added " + GuiTheme.TEXT + world.getName() + GuiTheme.GREEN + " to the RTP destinations.")));
        return true;
    }
}
