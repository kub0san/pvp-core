package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.features.teleport.rtp.RtpSettings;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Admin RTP hub. Bordered: global settings on the first content row, 18 destinations
 * on the next two, dock with Close, pager and "+ Add destination".
 */
public final class RtpManagementPage extends GuiScreen {
    private static final int PER_PAGE = 2 * GuiLayouts.COLUMNS;

    private final TeleportModule teleport;
    private final RtpModule rtp;
    private final RtpAppearance appearance;
    private final RtpNavigation nav;
    private final List<RtpRegion> regions;
    private final GuiPager pager;

    private RtpManagementPage(TeleportModule teleport, RtpModule rtp, Player player, int page) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpManagementPage.class)), player,
                GuiLayouts.ROWS_DOCK_LIST27, GuiFrame.Density.BORDER, null, null);
        this.teleport = teleport;
        this.rtp = rtp;
        this.nav = new RtpNavigation(teleport, rtp);
        this.appearance = RtpAppearance.shared();
        this.regions = new ArrayList<>(rtp.settings().regions().values());
        regions.sort(Comparator.comparing(RtpRegion::id, String.CASE_INSENSITIVE_ORDER));
        this.pager = GuiPager.of(page, regions.size(), PER_PAGE);
    }

    static void open(TeleportModule teleport, RtpModule rtp, Player player, int page) {
        new RtpManagementPage(teleport, rtp, player, page).show();
        SoundManager.playOpenGui(player);
    }

    @Override
    protected String title() {
        if (kits() != null) return GuiMenuFactory.sectionTitle(kits(), "RTP <dark_gray>\u00BB <white>Destinations");
        return "RTP \u00BB Destinations";
    }

    @Override
    protected void renderContent(StaticPane content) {
        RtpSettings settings = rtp.settings();

        List<GuiItem> tools = List.of(
                wrap(GuiTheme.tool(Material.CLOCK, "Cooldown", "Now: " + cooldownLabel(settings), "Click", "edit"),
                        (p, e) -> promptGlobalSetting(p, "cooldown-seconds", "Enter cooldown in seconds (-1 for default):"), player),
                wrap(GuiTheme.tool(Material.FEATHER, "Warmup", "Now: " + warmupLabel(settings), "Click", "edit"),
                        (p, e) -> promptGlobalSetting(p, "warmup-seconds", "Enter warmup in seconds (-1 for default):"), player),
                wrap(GuiTheme.tool(Material.COMPASS, "Search attempts", "Now: " + settings.maxAttempts(), "Click", "edit"),
                        (p, e) -> promptGlobalSetting(p, "search.max-attempts", "Enter max search attempts (1-100):"), player));
        GuiLayouts.centerItems(content, tools, GuiLayouts.contentRow(0));

        if (regions.isEmpty()) {
            content.addItem(wrap(GuiTheme.named(Material.MAP, GuiTheme.MUTED + "<bold>", "No destinations yet",
                    GuiTheme.dim("Use + Add destination to register a world")), (p, e) -> { }, player),
                    4, GuiLayouts.contentRow(1));
            return;
        }

        List<RtpRegion> visible = pager.slice(regions);
        for (int i = 0; i < visible.size(); i++) {
            RtpRegion region = visible.get(i);
            content.addItem(wrap(regionCard(region), (p, e) -> {
                if (e.isShiftClick() && e.isRightClick()) {
                    confirmRemove(p, region);
                } else if (e.isLeftClick()) {
                    SoundManager.playClick(p);
                    nav.openRegionSettings(p, region.id());
                }
            }, player), i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(1 + i / GuiLayouts.COLUMNS));
        }
    }

    private ItemStack regionCard(RtpRegion region) {
        boolean loaded = Bukkit.getWorld(region.worldName()) != null;
        return GuiTheme.named(appearance.icon(region), (loaded ? GuiTheme.TEXT : GuiTheme.RED) + "<bold>",
                appearance.displayName(region),
                loaded ? GuiTheme.status(GuiTheme.GREEN, true, "World loaded")
                        : GuiTheme.status(GuiTheme.RED, false, "World not loaded"),
                GuiTheme.value("World", region.worldName()),
                GuiTheme.value("Range", region.minRadius() + " - " + region.maxRadius()),
                GuiTheme.hint("Click", "edit"),
                GuiTheme.hint("Shift-right", "remove"));
    }

    private void confirmRemove(Player p, RtpRegion region) {
        SoundManager.playClick(p);
        int page = pager.currentPage();
        GuiConfirm.open(p, "Remove " + appearance.displayName(region) + "?",
                new String[]{"The Minecraft world is kept."}, true, null,
                () -> {
                    rtp.deleteRegion(region.id());
                    appearance.remove(region.id());
                    SoundManager.playSuccess(p);
                    nav.openManagement(p, page);
                },
                () -> nav.openManagement(p, page));
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .action(GuiDock.COL_BACK, GuiTheme.named(Material.BARRIER, GuiTheme.MUTED, "Close"), (p, e) -> {
                    SoundManager.playClick(p);
                    p.closeInventory();
                })
                .pager(pager, target -> nav.openManagement(player, target))
                .action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.named(Material.MAP, GuiTheme.GREEN + "<bold>",
                        "+ Add destination", GuiTheme.dim("Pick a loaded world"),
                        GuiTheme.hint("Click", "browse")), (p, e) -> {
                    SoundManager.playClick(p);
                    nav.openWorldBrowser(p);
                })
                .applyTo(chrome);
    }

    private void promptGlobalSetting(Player p, String key, String prompt) {
        SoundManager.playClick(p);
        int page = pager.currentPage();
        GuiChatPrompt.ask(JavaPlugin.getProvidingPlugin(RtpManagementPage.class), p, prompt, value -> {
            try {
                int parsed = Integer.parseInt(value.trim());
                rtp.updateSetting(key, parsed);
                p.sendMessage(teleport.messages().get("rtp-setting-updated", "value", String.valueOf(parsed)));
            } catch (NumberFormatException e) {
                p.sendMessage(teleport.messages().get("rtp-setup-invalid-number"));
            }
            nav.openManagement(p, page);
        }, () -> nav.openManagement(p, page));
    }

    private static String cooldownLabel(RtpSettings settings) {
        return settings.cooldownSeconds() == -1 ? "Default (5s)" : settings.cooldownSeconds() + "s";
    }

    private static String warmupLabel(RtpSettings settings) {
        return settings.warmupSeconds() == -1 ? "Default (3s)" : settings.warmupSeconds() + "s";
    }
}
