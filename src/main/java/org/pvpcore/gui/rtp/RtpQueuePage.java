package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing /rtpq picker: click a region to join its queue, or leave the queue
 * from the dock. Only registered, permitted, loaded regions are offered.
 */
public final class RtpQueuePage extends GuiScreen {
    private final TeleportModule teleport;
    private final RtpModule rtp;
    private final List<RtpRegion> destinations;
    private final GuiPager pager;

    private RtpQueuePage(TeleportModule teleport, RtpModule rtp, Player player, int page,
                         List<RtpRegion> destinations) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpQueuePage.class)), player,
                GuiLayouts.rowsFor(RtpSupport.gridRows(destinations.size(), page, RtpSupport.GRID_PER_PAGE)),
                GuiFrame.Density.BORDER, null, null);
        this.teleport = teleport;
        this.rtp = rtp;
        this.destinations = destinations;
        this.pager = GuiPager.of(page, destinations.size(), RtpSupport.GRID_PER_PAGE);
    }

    /** Opens the queue picker, or tells the player if no destination is available. */
    public static void open(TeleportModule teleport, RtpModule rtp, Player player) {
        open(teleport, rtp, player, 0);
    }

    private static void open(TeleportModule teleport, RtpModule rtp, Player player, int page) {
        List<RtpRegion> destinations = visibleQueuedDestinations(rtp, player);
        if (destinations.isEmpty()) {
            player.sendMessage(teleport.messages().get("rtp-no-regions"));
            return;
        }
        new RtpQueuePage(teleport, rtp, player, page, destinations).show();
        SoundManager.playOpenGui(player);
    }

    /** Regions the player can queue for, in the config's own order. */
    static List<RtpRegion> visibleQueuedDestinations(RtpModule rtp, Player player) {
        return rtp.settings().regions().values().stream()
                .filter(region -> region.permission().isEmpty() || player.hasPermission(region.permission()))
                .filter(region -> Bukkit.getWorld(region.worldName()) != null)
                .toList();
    }

    @Override
    protected String title() {
        return "RTP Queue";
    }

    @Override
    protected void renderContent(StaticPane content) {
        List<GuiItem> cards = new ArrayList<>();
        for (RtpRegion region : pager.slice(destinations)) {
            cards.add(wrap(GuiTheme.named(Material.COMPASS, GuiTheme.TEXT + "<bold>", region.id(),
                    GuiTheme.value("Queue", String.valueOf(rtp.rtpq().size(region.id()))),
                    GuiTheme.value("World", region.worldName()),
                    GuiTheme.hint("Click", "join queue")), (p, e) -> {
                SoundManager.playClick(p);
                rtp.rtpqCommand().join(p, region.id());
                refresh();
            }, player));
        }
        RtpSupport.placeGrid(content, cards);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> p.closeInventory())
                .pager(pager, target -> open(teleport, rtp, player, target))
                .action(8, GuiTheme.dangerTool(Material.FEATHER, "Leave queue", null, "Click", "leave"),
                        (p, e) -> {
                            SoundManager.playClick(p);
                            rtp.rtpqCommand().leave(p);
                            refresh();
                        })
                .applyTo(chrome);
    }
}
