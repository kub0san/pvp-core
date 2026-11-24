package org.pvpcore.gui.rtp;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;
import org.pvpcore.features.teleport.rtp.RtpRegion;
import org.pvpcore.gui.base.*;
import org.pvpcore.util.kits.SoundManager;

import java.util.List;

/** Icon grid on a bordered chest (up to four content rows + dock). The current icon glows. */
public final class RtpIconPickerPage extends GuiScreen {
    private static final int PER_PAGE = 4 * GuiLayouts.COLUMNS;

    private final RtpModule rtp;
    private final RtpAppearance appearance;
    private final RtpNavigation nav;
    private final String regionId;
    private final GuiPager pager;

    private RtpIconPickerPage(TeleportModule teleport, RtpModule rtp, Player player, String regionId, int page) {
        super(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(RtpIconPickerPage.class)), player,
                GuiLayouts.rowsFor(RtpSupport.gridRows(RtpSupport.ICONS.size(), page, PER_PAGE)),
                GuiFrame.Density.BORDER, null, null);
        this.rtp = rtp;
        this.nav = new RtpNavigation(teleport, rtp);
        this.appearance = RtpAppearance.shared();
        this.regionId = regionId;
        this.pager = GuiPager.of(page, RtpSupport.ICONS.size(), PER_PAGE);
    }

    static void open(TeleportModule teleport, RtpModule rtp, Player player, String regionId, int page) {
        new RtpIconPickerPage(teleport, rtp, player, regionId, page).show();
        SoundManager.playOpenGui(player);
    }

    @Override
    protected String title() {
        if (kits() != null) return GuiMenuFactory.sectionTitle(kits(), "RTP <dark_gray>\u00BB <white>Choose icon");
        return "RTP \u00BB Choose icon";
    }

    @Override
    protected void renderContent(StaticPane content) {
        RtpRegion region = rtp.settings().region(regionId);
        Material current = region == null ? null : appearance.icon(region);

        List<Material> visible = pager.slice(RtpSupport.ICONS);
        for (int i = 0; i < visible.size(); i++) {
            Material material = visible.get(i);
            boolean selected = material == current;
            ItemStack item = GuiTheme.named(material, (selected ? GuiTheme.ACCENT : GuiTheme.TEXT) + "<bold>",
                    RtpSupport.prettyMaterial(material),
                    selected ? GuiTheme.status(GuiTheme.ACCENT, true, "Current") : GuiTheme.hint("Click", "use"));
            content.addItem(wrap(selected ? GuiTheme.glow(item) : item, (p, e) -> {
                appearance.setIcon(regionId, material);
                SoundManager.playSuccess(p);
                nav.openRegionSettings(p, regionId);
            }, player), i % GuiLayouts.COLUMNS, GuiLayouts.contentRow(i / GuiLayouts.COLUMNS));
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock.of(this)
                .back((p, e) -> nav.openRegionSettings(p, regionId))
                .pager(pager, target -> nav.openIconPicker(player, regionId, target))
                .applyTo(chrome);
    }
}
