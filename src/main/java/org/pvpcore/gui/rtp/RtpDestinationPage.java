package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.TeleportService;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.features.teleport.rtp.RtpSettings;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Player-facing destination picker. The screen is exactly as tall as it needs to be:
 * one row of cards for a handful of destinations (centered), up to three rows, then the
 * dock. More than 27 destinations are paged from the dock.
 */
public final class RtpDestinationPage extends GuiScreen {
    private final TeleportModule teleport;
    private final TeleportService service;
    private final RtpModule rtp;
    private final RtpAppearance appearance;
    private final List<RtpRegion> destinations;
    private final GuiPager pager;

    private RtpDestinationPage(TeleportModule teleport, TeleportService service, RtpModule rtp, Player player,
                               int page, List<RtpRegion> destinations) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpDestinationPage.class)), player,
                GuiLayouts.rowsFor(RtpSupport.gridRows(destinations.size(), page, RtpSupport.GRID_PER_PAGE)),
                GuiFrame.Density.BORDER, null, null);
        this.teleport = teleport;
        this.service = service;
        this.rtp = rtp;
        this.appearance = RtpAppearance.shared();
        this.destinations = destinations;
        this.pager = GuiPager.of(page, destinations.size(), RtpSupport.GRID_PER_PAGE);
    }

    /** Builds and shows the selector, or tells the player when there is nothing to show. */
    public static void open(TeleportModule teleport, TeleportService service, RtpModule rtp, Player player, int page) {
        List<RtpRegion> destinations = visibleDestinations(rtp, player);
        if (destinations.isEmpty()) {
            player.sendMessage(teleport.messages().get("rtp-no-regions"));
            return;
        }
        new RtpDestinationPage(teleport, service, rtp, player, page, destinations).show();
        SoundManager.playOpenGui(player);
    }

    /** Regions the player may see, in the config's own order. */
    static List<RtpRegion> visibleDestinations(RtpModule rtp, Player player) {
        return rtp.settings().regions().values().stream()
                .filter(region -> region.permission().isEmpty() || player.hasPermission(region.permission()))
                .toList();
    }

    @Override
    protected String title() {
        if (kits() != null) {
            return GuiMenuFactory.sectionTitle(kits(), "Random Teleport");
        }
        return "Random Teleport";
    }

    @Override
    protected void renderContent(StaticPane content) {
        int cooldown = service.getRemainingCooldown(player);
        boolean pending = service.hasPending(player);
        RtpSettings settings = rtp.settings();

        List<GuiItem> cards = new ArrayList<>();
        for (RtpRegion region : pager.slice(destinations)) {
            boolean loaded = Bukkit.getWorld(region.worldName()) != null;
            boolean ready = loaded && !pending && cooldown <= 0;

            String status;
            if (!loaded) status = GuiTheme.status(GuiTheme.RED, false, "World not loaded");
            else if (pending) status = GuiTheme.status(GuiTheme.GOLD, true, "Teleport in progress");
            else if (cooldown > 0) status = GuiTheme.status(GuiTheme.GOLD, false, "Cooldown " + cooldown + "s");
            else status = GuiTheme.status(GuiTheme.GREEN, true, "Ready");

            List<String> lore = new ArrayList<>();
            lore.add(status);
            String description = appearance.description(region);
            if (description != null && !description.isBlank()) {
                lore.add(GuiTheme.dim(description));
            }
            lore.add(GuiTheme.value("Range", region.minRadius() + " - " + region.maxRadius()));
            org.bukkit.World loadedWorld = Bukkit.getWorld(region.worldName());
            if (loadedWorld != null && region.maxRadius() > 0) {
                org.bukkit.WorldBorder border = loadedWorld.getWorldBorder();
                double borderHalf = Math.max(0.0, border.getSize() / 2.0 - settings.borderMargin());
                double centreX = region.centerOnBorder() ? border.getCenter().getX() : region.centerX();
                double centreZ = region.centerOnBorder() ? border.getCenter().getZ() : region.centerZ();
                double toEdgeX = Math.max(0.0, borderHalf - Math.abs(centreX - border.getCenter().getX()));
                double toEdgeZ = Math.max(0.0, borderHalf - Math.abs(centreZ - border.getCenter().getZ()));
                double fit = Math.min(toEdgeX, toEdgeZ);
                if (fit < region.maxRadius()) {
                    lore.add(GuiTheme.dim(String.format(java.util.Locale.ROOT, "Border caps range ~%.0f", fit)));
                }
            }
            if (ready) {
                lore.add(GuiTheme.hint("Click", "teleport"));
            }

            Material icon = loaded ? appearance.icon(region) : Material.BEDROCK;
            ItemStack item = GuiTheme.named(icon, (loaded ? GuiTheme.TEXT : GuiTheme.RED) + "<bold>",
                    appearance.displayName(region), lore.toArray(new String[0]));

            cards.add(wrap(item, (p, e) -> {
                SoundManager.playClick(p);
                if (!ready) return;
                p.closeInventory();
                rtp.service().acquire(region, p).thenAccept(location -> {
                    if (location == null) {
                        if (p.isOnline()) p.sendMessage(teleport.messages().get("rtp-failed"));
                        return;
                    }
                    if (!p.isOnline()) return;
                    TeleportService.Options options = new TeleportService.Options(
                            settings.warmupSeconds(), settings.cooldownSeconds(), "rtp-failed", "rtp-success");
                    service.begin(p, () -> location, options);
                });
            }, player));
        }
        RtpSupport.placeGrid(content, cards);
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .action(GuiDock.COL_BACK, GuiTheme.named(Material.BARRIER, GuiTheme.MUTED, "Close"), (p, e) -> {
                    SoundManager.playClick(p);
                    p.closeInventory();
                })
                .pager(pager, target -> open(teleport, service, rtp, player, target))
                .applyTo(chrome);
    }
}
