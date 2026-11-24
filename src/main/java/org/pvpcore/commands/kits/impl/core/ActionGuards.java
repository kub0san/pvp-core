package org.pvpcore.commands.kits.impl.core;

import org.pvpcore.commands.kits.KitsCommands;
import org.pvpcore.features.kits.KitManager;
import org.pvpcore.features.kits.KitsContext;
import org.pvpcore.util.kits.LocationAccess;
import org.pvpcore.util.kits.LocationFeature;
import org.pvpcore.util.kits.SoundManager;
import org.bukkit.entity.Player;

/** Player actions use the same policy whether invoked by a command, menu, or special item. */
public final class ActionGuards {
    private ActionGuards() {}

    private static KitsContext kits() {
        return KitsCommands.activeKits();
    }

    public static boolean allowed(Player player, String permission) {
        return allowed(player, permission, LocationFeature.forPermission(permission));
    }

    public static boolean allowed(Player player, String permission, LocationFeature feature) {
        KitsContext kits = kits();
        if (!player.hasPermission(permission)) {
            if (kits != null && kits.lang() != null) kits.lang().send(player, "error.no-permission");
            SoundManager.playFailure(player);
            return false;
        }
        LocationAccess access = kits != null ? kits.locationAccess() : null;
        return feature == null || access == null || access.require(player, feature);
    }

    public static boolean dataReady(Player player) {
        KitsContext kits = kits();
        KitManager km = kits != null ? kits.kitManager() : null;
        if (km == null || !km.isLoading(player.getUniqueId())) return true;
        if (kits.lang() != null) kits.lang().send(player, "error.kit-data-loading");
        return false;
    }
}
