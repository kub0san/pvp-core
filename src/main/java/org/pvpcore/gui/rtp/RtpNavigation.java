package org.pvpcore.gui.rtp;

import org.bukkit.entity.Player;
import org.pvpcore.features.teleport.TeleportModule;
import org.pvpcore.features.teleport.rtp.RtpModule;

/**
 * One place that knows how to open the RTP pages. Pages navigate through this
 * instead of constructing each other, so no page needs to know the module wiring
 * of a page it only links to.
 */
public final class RtpNavigation {
    private final TeleportModule teleport;
    private final RtpModule rtp;

    public RtpNavigation(TeleportModule teleport, RtpModule rtp) {
        this.teleport = teleport;
        this.rtp = rtp;
    }

    public void openManagement(Player player) {
        openManagement(player, 0);
    }

    public void openManagement(Player player, int page) {
        RtpManagementPage.open(teleport, rtp, player, page);
    }

    public void openWorldBrowser(Player player) {
        openWorldBrowser(player, 0);
    }

    public void openWorldBrowser(Player player, int page) {
        RtpWorldBrowserPage.open(teleport, rtp, player, page);
    }

    public void openRegionSettings(Player player, String regionId) {
        if (rtp.settings().region(regionId) == null) {
            openManagement(player);
            return;
        }
        RtpRegionSettingsPage.open(teleport, rtp, player, regionId);
    }

    public void openIconPicker(Player player, String regionId, int page) {
        RtpIconPickerPage.open(teleport, rtp, player, regionId, page);
    }
}
