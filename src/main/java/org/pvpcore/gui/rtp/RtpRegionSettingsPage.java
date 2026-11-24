package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * One destination. Bordered: appearance tools on the first content row, teleport
 * limits on the second, dock with Back and Remove. Every change saves at once.
 */
public final class RtpRegionSettingsPage extends GuiScreen {
    private final TeleportModule teleport;
    private final RtpModule rtp;
    private final RtpAppearance appearance;
    private final RtpNavigation nav;
    private final String regionId;

    private RtpRegionSettingsPage(TeleportModule teleport, RtpModule rtp, Player player, String regionId) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpRegionSettingsPage.class)), player,
                GuiLayouts.ROWS_DOCK_LIST18, GuiFrame.Density.BORDER, null, null);
        this.teleport = teleport;
        this.rtp = rtp;
        this.nav = new RtpNavigation(teleport, rtp);
        this.appearance = RtpAppearance.shared();
        this.regionId = regionId;
    }

    /** Opens the editor for one destination. Callers should confirm the region exists. */
    static void open(TeleportModule teleport, RtpModule rtp, Player player, String regionId) {
        new RtpRegionSettingsPage(teleport, rtp, player, regionId).show();
        SoundManager.playOpenGui(player);
    }

    private RtpRegion region() {
        return rtp.settings().region(regionId);
    }

    @Override
    protected String title() {
        RtpRegion region = region();
        String name = region == null ? regionId : appearance.displayName(region);
        if (kits() != null) return GuiMenuFactory.sectionTitle(kits(), "RTP <dark_gray>\u00BB <white>" + name);
        return "RTP \u00BB " + name;
    }

    @Override
    protected void renderContent(StaticPane content) {
        RtpRegion region = region();
        if (region == null) return;

        GuiLayouts.centerItems(content, List.of(
                wrap(GuiTheme.tool(Material.NAME_TAG, "Display name", "Now: " + appearance.displayName(region),
                        "Click", "rename"),
                        (p, e) -> promptText(p, "Enter a display name for this destination:", 32, value ->
                                appearance.setDisplayName(regionId, value)), player),
                wrap(GuiTheme.tool(appearance.icon(region), "Icon", "Now: " + RtpSupport.prettyMaterial(appearance.icon(region)),
                        "Click", "choose icon"),
                        (p, e) -> {
                            SoundManager.playClick(p);
                            nav.openIconPicker(p, regionId, 0);
                        }, player),
                wrap(GuiTheme.tool(Material.WRITABLE_BOOK, "Description", "Now: " + appearance.description(region),
                        "Click", "edit"),
                        (p, e) -> promptText(p, "Enter a short destination description:", 160, value ->
                                appearance.setDescription(regionId, value)), player)),
                GuiLayouts.contentRow(0));

        GuiLayouts.centerItems(content, List.of(
                numeric(Material.MAP, "Min radius", "Closest landing distance",
                        String.valueOf(region.minRadius()), "Enter new MIN radius:",
                        v -> rtp.updateRegionSetting(regionId, "min-radius", v)),
                numeric(Material.COMPASS, "Max radius", "Farthest landing distance",
                        String.valueOf(region.maxRadius()), "Enter new MAX radius:",
                        v -> rtp.updateRegionSetting(regionId, "max-radius", v)),
                numeric(Material.LADDER, "Min Y", "Lowest landing height",
                        region.minY() == Integer.MIN_VALUE ? "World default" : String.valueOf(region.minY()),
                        "Enter new MIN Y level:", v -> rtp.updateRegionSetting(regionId, "min-y", v)),
                numeric(Material.SCAFFOLDING, "Max Y", "Highest landing height",
                        region.maxY() == Integer.MAX_VALUE ? "World default" : String.valueOf(region.maxY()),
                        "Enter new MAX Y level:", v -> rtp.updateRegionSetting(regionId, "max-y", v))),
                GuiLayouts.contentRow(1));
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> nav.openManagement(p))
                .action(GuiLayouts.DOCK_ACTION_COL_B, GuiTheme.dangerTool(Material.LAVA_BUCKET, "Remove destination",
                        "The world itself is kept", "Click", "remove"), (p, e) -> {
                    SoundManager.playClick(p);
                    GuiConfirm.open(p, "Remove this destination?", new String[]{"The Minecraft world is kept."}, true, null,
                            () -> {
                                rtp.deleteRegion(regionId);
                                appearance.remove(regionId);
                                SoundManager.playSuccess(p);
                                nav.openManagement(p);
                            },
                            () -> nav.openRegionSettings(p, regionId));
                })
                .applyTo(chrome);
    }

    private GuiItem numeric(Material material, String name, String about, String current, String prompt, IntConsumer apply) {
        return wrap(GuiTheme.named(material, GuiTheme.TEXT + "<bold>", name,
                GuiTheme.value("Now", current), GuiTheme.dim(about), GuiTheme.hint("Click", "change")), (p, e) -> {
            SoundManager.playClick(p);
            GuiChatPrompt.ask(JavaPlugin.getProvidingPlugin(RtpRegionSettingsPage.class), p, prompt, value -> {
                try {
                    int parsed = Integer.parseInt(value.trim());
                    apply.accept(parsed);
                    p.sendMessage(teleport.messages().get("rtp-setting-updated", "value", String.valueOf(parsed)));
                } catch (NumberFormatException ex) {
                    p.sendMessage(teleport.messages().get("rtp-setup-invalid-number"));
                }
                nav.openRegionSettings(p, regionId);
            }, () -> nav.openRegionSettings(p, regionId));
        }, player);
    }

    private void promptText(Player p, String prompt, int maxLength, Consumer<String> action) {
        SoundManager.playClick(p);
        GuiChatPrompt.ask(JavaPlugin.getProvidingPlugin(RtpRegionSettingsPage.class), p, prompt, value -> {
            action.accept(value.length() > maxLength ? value.substring(0, maxLength) : value);
            SoundManager.playSuccess(p);
            nav.openRegionSettings(p, regionId);
        }, () -> nav.openRegionSettings(p, regionId));
    }
}
